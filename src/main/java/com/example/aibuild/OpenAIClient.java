package com.example.aibuild;

import com.example.aibuild.exception.OpenAIException;
import com.google.gson.Gson;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.bukkit.Material;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class OpenAIClient {
    private static final String RESPONSES_URL = "https://api.openai.com/v1/responses";
    private final OkHttpClient http;
    private final Gson gson = new Gson();
    private final String apiKey;
    private final String model;

    public OpenAIClient(String apiKey, String model, int timeoutMs) {
        this.apiKey = apiKey;
        this.model = model;

        this.http = new OkHttpClient.Builder()
                .callTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    public String generateBuildPlanJson(String userPrompt, int maxBlocks, Set<Material> allowed) throws OpenAIException {
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("PUT_YOUR_KEY")) {
            throw new OpenAIException("OpenAI API key not set in plugins/AIBuild/config.yml");
        }

        String allowedList = allowed.stream().map(Enum::name).sorted().collect(Collectors.joining(", "));
        String instructions = buildInstructions(maxBlocks, allowedList);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("instructions", instructions);
        payload.put("input", userPrompt);

        // keep cost down
        Map<String, Object> reasoning = new LinkedHashMap<>();
        // reasoning.put("effort", "low");
        // payload.put("reasoning", reasoning);

        RequestBody body = RequestBody.create(gson.toJson(payload), MediaType.parse("application/json"));
        Request req = new Request.Builder()
                .url(RESPONSES_URL)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();

        try (Response res = http.newCall(req).execute()) {
            String raw = res.body() != null ? res.body().string() : "";
            if (!res.isSuccessful()) {
                throw new OpenAIException("OpenAI API error: HTTP " + res.code() + " " + raw, res.code());
            }
            String extracted = extractTextFromResponsesApi(raw);
            if (extracted == null || extracted.isBlank()) {
                throw new OpenAIException("OpenAI returned empty text output. Raw: " + raw);
            }
            return extracted.trim();
        } catch (java.io.IOException e) {
            throw new OpenAIException("Network error calling OpenAI API", e);
        }
    }

    public String generateBuildPlanJsonStreaming(
            String userPrompt,
            int maxBlocks,
            Set<Material> allowed,
            Consumer<String> onProgress
    ) throws OpenAIException {
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("PUT_YOUR_KEY")) {
            throw new OpenAIException("OpenAI API key not set in plugins/AIBuild/config.yml");
        }

        String allowedList = allowed.stream().map(Enum::name).sorted().collect(Collectors.joining(", "));
        String instructions = buildInstructions(maxBlocks, allowedList);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("instructions", instructions);
        payload.put("input", userPrompt);
        payload.put("stream", true);

        RequestBody body = RequestBody.create(gson.toJson(payload), MediaType.parse("application/json"));
        Request req = new Request.Builder()
                .url(RESPONSES_URL)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "text/event-stream")
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();

        CountDownLatch done = new CountDownLatch(1);
        StringBuilder text = new StringBuilder();
        AtomicReference<OpenAIException> error = new AtomicReference<>(null);
        AtomicInteger animFrame = new AtomicInteger(0);
        AtomicReference<Boolean> started = new AtomicReference<>(false);
        
        String[] animMessages = {
            "⚒ Gathering materials...",
            "⚒ Crafting blocks...",
            "⚒ Assembling structure..."
        };

        // Recurring animation timer
        Timer animTimer = new Timer(true);
        animTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (started.get()) {
                    int frame = animFrame.getAndIncrement();
                    onProgress.accept(animMessages[frame % animMessages.length]);
                }
            }
        }, 0, 3000); // Update every 3 seconds

        EventSourceListener listener = new EventSourceListener() {
            @Override
            public void onEvent(EventSource eventSource, String id, String type, String data) {
                if (data == null || data.isBlank()) return;

                // Start animation when API responds
                if ("response.created".equals(type)) {
                    started.set(true);
                }

                // If the API signals that output text is done, treat this as completion
                if ("response.output_text.done".equals(type) || "response.content_part.done".equals(type)) {
                    onProgress.accept("⚒ Assembling structure...");
                    // Close the SSE connection immediately to avoid waiting for server-side completion
                    eventSource.cancel();
                    animTimer.cancel();
                    done.countDown();
                    return;
                }

                if (type != null && type.startsWith("response.output_text")) {
                    String delta = extractDeltaText(data);
                    if (delta != null && !delta.isEmpty()) {
                        text.append(delta);
                    }
                }
            }

            @Override
            public void onFailure(EventSource eventSource, Throwable t, Response response) {
                animTimer.cancel();
                String msg = t != null ? t.getMessage() : "Unknown streaming error";
                int code = -1;
                if (response != null) {
                    code = response.code();
                    try {
                        String body = response.body() != null ? response.body().string() : "";
                        msg = (body.isBlank() ? response.message() : body);
                    } catch (Exception e) {
                        msg = response.message();
                    }
                }
                error.set(new OpenAIException("OpenAI stream error: " + msg, code));
                done.countDown();
            }
        };

        EventSource.Factory factory = EventSources.createFactory(http);
        EventSource es = factory.newEventSource(req, listener);

        boolean finished;
        try {
            finished = done.await(http.callTimeoutMillis() + 5000L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            es.cancel();
            throw new OpenAIException("OpenAI stream interrupted", e);
        }

        // Close the EventSource proactively as soon as we're done to speed up shutdown
        es.cancel();

        if (!finished) {
            throw new OpenAIException("OpenAI stream timed out");
        }
        if (error.get() != null) throw error.get();

        String extracted = text.toString().trim();
        if (extracted.isBlank()) {
            throw new OpenAIException("OpenAI returned empty streaming output.");
        }
        return extracted;
    }

    private String buildInstructions(int maxBlocks, String allowedList) {
        // Create material ID mapping for compact protocol
        String[] mats = allowedList.split(", ");
        StringBuilder matMapping = new StringBuilder();
        for (int i = 0; i < mats.length; i++) {
            matMapping.append(i).append("=").append(mats[i]);
            if (i < mats.length - 1) matMapping.append(", ");
        }
        
        return "You generate Minecraft building plans as STRICT JSON only.\n" +
                "OPTIMIZED COMPACT PROTOCOL (70% smaller):\n" +
                "{\n" +
                "  \"s\": [x, y, z],\n" +
                "  \"b\": [[x,y,z,m], [x,y,z,m], ...]\n" +
                "}\n" +
                "Where:\n" +
                "- s = size [width, height, depth]\n" +
                "- b = blocks array, each block is [x, y, z, material_id]\n" +
                "- Material IDs: " + matMapping + "\n" +
                "Rules:\n" +
                "- b.length <= " + maxBlocks + "\n" +
                "- x in [0..s[0]-1], y in [0..s[1]-1], z in [0..s[2]-1]\n" +
                "- Include foundation blocks at y=0 where structure touches ground\n" +
                "- Use material IDs only (integers 0-" + (mats.length - 1) + ")\n" +
                "- Output JSON only. No markdown, no commentary. Use compact format.";
    }

    @SuppressWarnings("unchecked")
    private String extractDeltaText(String rawJson) {
        Object rootObj = gson.fromJson(rawJson, Object.class);
        if (!(rootObj instanceof Map<?, ?> root)) return null;

        Object delta = root.get("delta");
        if (delta instanceof String s && !s.isBlank()) return s;

        Object text = root.get("text");
        if (text instanceof String s2 && !s2.isBlank()) return s2;

        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractTextFromResponsesApi(String rawJson) {
        Object rootObj = gson.fromJson(rawJson, Object.class);
        if (!(rootObj instanceof Map<?, ?> root)) return null;

        // Some SDKs include output_text convenience
        Object outputText = root.get("output_text");
        if (outputText instanceof String s && !s.isBlank()) return s;

        // Typical structure: { output: [ { content: [ { type:"output_text", text:"..." } ] } ] }
        Object output = root.get("output");
        if (output instanceof List<?> outList) {
            for (Object item : outList) {
                if (!(item instanceof Map<?, ?> outItem)) continue;
                Object content = outItem.get("content");
                if (!(content instanceof List<?> contentList)) continue;

                for (Object c : contentList) {
                    if (!(c instanceof Map<?, ?> cm)) continue;

                    Object type = cm.get("type");
                    if (type instanceof String ts && ts.equalsIgnoreCase("output_text")) {
                        Object text = cm.get("text");
                        if (text instanceof String s && !s.isBlank()) return s;
                    }

                    // fallback: if it just has "text"
                    Object text = cm.get("text");
                    if (text instanceof String s && !s.isBlank()) return s;
                }
            }
        }

        // last resort: maybe it's directly in "response" or similar
        for (String k : List.of("text", "content")) {
            Object v = root.get(k);
            if (v instanceof String s && !s.isBlank()) return s;
        }

        return null;
    }
}
