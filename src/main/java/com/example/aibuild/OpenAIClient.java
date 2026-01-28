package com.example.aibuild;

import com.google.gson.Gson;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.bukkit.Material;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
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

    public String generateBuildPlanJson(String userPrompt, int maxBlocks, Set<Material> allowed) throws IOException {
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("PUT_YOUR_KEY")) {
            throw new IOException("OpenAI API key not set in plugins/AIBuild/config.yml");
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
                throw new IOException("OpenAI error: HTTP " + res.code() + " " + raw);
            }
            String extracted = extractTextFromResponsesApi(raw);
            if (extracted == null || extracted.isBlank()) {
                throw new IOException("OpenAI returned empty text output. Raw: " + raw);
            }
            return extracted.trim();
        }
    }

    public String generateBuildPlanJsonStreaming(
            String userPrompt,
            int maxBlocks,
            Set<Material> allowed,
            Consumer<String> onProgress
    ) throws IOException {
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("PUT_YOUR_KEY")) {
            throw new IOException("OpenAI API key not set in plugins/AIBuild/config.yml");
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
        AtomicReference<IOException> error = new AtomicReference<>(null);
        AtomicInteger progressStage = new AtomicInteger(0);

        EventSourceListener listener = new EventSourceListener() {
            @Override
            public void onEvent(EventSource eventSource, String id, String type, String data) {
                if (data == null || data.isBlank()) return;

                if ("response.completed".equals(type)) {
                    done.countDown();
                    return;
                }

                if (type != null && type.startsWith("response.output_text")) {
                    String delta = extractDeltaText(data);
                    if (delta != null && !delta.isEmpty()) {
                        text.append(delta);

                        int len = text.length();
                        if (progressStage.compareAndSet(0, 1)) {
                            onProgress.accept("AI is sketching the build...");
                        } else if (len > 400 && progressStage.compareAndSet(1, 2)) {
                            onProgress.accept("AI is selecting materials...");
                        } else if (len > 800 && progressStage.compareAndSet(2, 3)) {
                            onProgress.accept("AI is finalizing the plan...");
                        }
                    }
                }
            }

            @Override
            public void onFailure(EventSource eventSource, Throwable t, Response response) {
                String msg = t != null ? t.getMessage() : "Unknown streaming error";
                if (response != null) {
                    try {
                        String body = response.body() != null ? response.body().string() : "";
                        msg = "HTTP " + response.code() + ": " + (body.isBlank() ? response.message() : body);
                    } catch (Exception e) {
                        msg = "HTTP " + response.code() + ": " + response.message();
                    }
                }
                error.set(new IOException("OpenAI stream error: " + msg));
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
            throw new IOException("OpenAI stream interrupted", e);
        }

        if (!finished) {
            es.cancel();
            throw new IOException("OpenAI stream timed out");
        }
        if (error.get() != null) throw error.get();

        String extracted = text.toString().trim();
        if (extracted.isBlank()) {
            throw new IOException("OpenAI returned empty streaming output.");
        }
        return extracted;
    }

    private String buildInstructions(int maxBlocks, String allowedList) {
        return "You generate Minecraft building plans as STRICT JSON only.\n" +
                "Schema:\n" +
                "{\n" +
                "  \"name\": string,\n" +
                "  \"size\": {\"x\": int, \"y\": int, \"z\": int},\n" +
                "  \"blocks\": [{\"dx\": int, \"dy\": int, \"dz\": int, \"material\": string}]\n" +
                "}\n" +
                "Rules:\n" +
                "- blocks.length <= " + maxBlocks + "\n" +
                "- dx in [0..size.x-1]\n" +
                "- dy in [0..size.y-1]\n" +
                "- dz in [0..size.z-1]\n" +
                "- ALL blocks at dy=0 must form a solid foundation (no gaps)\n" +
                "- No floating blocks: every block with dy>0 must have a block below it\n" +
                "- material MUST be one of: " + allowedList + "\n" +
                "- Output JSON only. No markdown, no commentary.";
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
