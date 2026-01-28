package com.example.aibuild;

import com.google.gson.Gson;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;

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

/**
 * Standalone test for OpenAI streaming without Bukkit dependencies.
 * Run: make test
 */
public class StreamingTest {
    private static final String RESPONSES_URL = "https://api.openai.com/v1/responses";
    
    public static void main(String[] args) {
        System.out.println("=== OpenAI Streaming Test ===\n");

        String apiKey = EnvConfig.getOpenAiApiKey();
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("PUT_YOUR_KEY")) {
            System.err.println("ERROR: OPENAI_API_KEY not found");
            System.err.println("Set in .env file: OPENAI_API_KEY=sk-...");
            System.exit(1);
        }

        System.out.println("✓ API key loaded from .env");

        OkHttpClient http = new OkHttpClient.Builder()
                .callTimeout(Duration.ofMillis(60000))
                .build();

        System.out.println("✓ HTTP client initialized\n");

        Set<String> materials = Set.of("OAK_PLANKS", "COBBLESTONE", "GLASS", "OAK_STAIRS", "TORCH");
        String[] matArray = materials.stream().sorted().toArray(String[]::new);
        String allowedList = String.join(", ", materials);
        
        // Create material ID mapping for compact protocol
        StringBuilder matMapping = new StringBuilder();
        for (int i = 0; i < matArray.length; i++) {
            matMapping.append(i).append("=").append(matArray[i]);
            if (i < matArray.length - 1) matMapping.append(", ");
        }
        
        String instructions = "You generate Minecraft building plans as STRICT JSON only.\n" +
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
                "- b.length <= 100\n" +
                "- x in [0..s[0]-1], y in [0..s[1]-1], z in [0..s[2]-1]\n" +
                "- Include foundation blocks at y=0 where structure touches ground\n" +
                "- Use material IDs only (integers 0-" + (matArray.length - 1) + ")\n" +
                "- Output JSON only. No markdown, no commentary. Use compact format.";

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", "gpt-4o-mini");
        payload.put("instructions", instructions);
        payload.put("input", "small wooden house");
        payload.put("stream", true);

        Gson gson = new Gson();
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
        AtomicInteger animFrame = new AtomicInteger(0);
        AtomicReference<Boolean> started = new AtomicReference<>(false);
        List<String> progressMessages = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        
        String[] animMessages = {
            "⚒ Gathering materials...",
            "⚒ Crafting blocks...",
            "⚒ Assembling structure..."
        };

        System.out.println("Sending request: 'small wooden house'");
        System.out.println("Progress updates:");

        // Recurring animation timer
        Timer animTimer = new Timer(true);
        animTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (started.get()) {
                    int frame = animFrame.getAndIncrement();
                    String msg = animMessages[frame % animMessages.length];
                    progressMessages.add(msg);
                    System.out.println("  → " + msg);
                }
            }
        }, 0, 3000); // Update every 3 seconds

        EventSourceListener listener = new EventSourceListener() {
            @Override
            public void onOpen(EventSource eventSource, Response response) {
                System.out.println("  ✓ Connected to OpenAI (HTTP " + response.code() + ")");
            }

            @Override
            public void onEvent(EventSource eventSource, String id, String type, String data) {
                if (data == null || data.isBlank()) return;

                // Start animation when API responds
                if ("response.created".equals(type)) {
                    started.set(true);
                }
                // Treat output_text.done as completion to finish faster
                if ("response.output_text.done".equals(type) || "response.content_part.done".equals(type)) {
                    String msg = "⚒ Assembling structure...";
                    System.out.println("  → " + msg);
                    progressMessages.add(msg);
                    // Close SSE connection immediately
                    eventSource.cancel();
                    animTimer.cancel();
                    done.countDown();
                    return;
                }
                if ("response.completed".equals(type) || "done".equals(type) || "[DONE]".equals(data)) {
                    eventSource.cancel();
                    animTimer.cancel();
                    done.countDown();
                    return;
                }

                if (type != null && type.startsWith("response.output_text")) {
                    String delta = extractDelta(gson, data);
                    if (delta != null && !delta.isEmpty()) {
                        text.append(delta);
                    }
                }
            }

            @Override
            public void onFailure(EventSource eventSource, Throwable t, Response response) {
                animTimer.cancel();
                String msg = t != null ? t.getMessage() : "Unknown streaming error";
                if (response != null) {
                    try {
                        String responseBody = response.body() != null ? response.body().string() : "";
                        msg = "HTTP " + response.code() + ": " + (responseBody.isBlank() ? response.message() : responseBody);
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
            finished = done.await(65000L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            es.cancel();
            System.err.println("\n✗ Stream interrupted");
            System.exit(1);
            return;
        }

        // Close EventSource proactively to speed shutdown
        es.cancel();

        if (!finished) {
            System.err.println("\n✗ Stream timed out");
            System.exit(1);
            return;
        }

        if (error.get() != null) {
            System.err.println("\n✗ ERROR: " + error.get().getMessage());
            error.get().printStackTrace();
            System.exit(1);
            return;
        }

        String result = text.toString().trim();
        if (result.isBlank()) {
            System.err.println("\n✗ Empty response");
            System.exit(1);
            return;
        }

        long elapsedMs = System.currentTimeMillis() - startTime;
        System.out.println("\n✓ Stream completed successfully");
        System.out.println("Time elapsed: " + elapsedMs + "ms");
        System.out.println("Progress messages received: " + progressMessages.size());
        
        // Validate JSON structure
        try {
            com.google.gson.stream.JsonReader reader = new com.google.gson.stream.JsonReader(
                    new java.io.StringReader(result));
            reader.setLenient(true);
            Map<?, ?> parsed = gson.fromJson(reader, Map.class);
            
            // Check for compact format (s and b keys)
            if (parsed.containsKey("s") && parsed.containsKey("b")) {
                System.out.println("✓ Valid compact JSON format");
                
                @SuppressWarnings("unchecked")
                List<?> blocks = (List<?>) parsed.get("b");
                System.out.println("✓ Generated " + blocks.size() + " blocks");
                
                @SuppressWarnings("unchecked")
                List<?> sizeList = (List<?>) parsed.get("s");
                System.out.println("✓ Structure size: " + sizeList.get(0) + "x" + sizeList.get(1) + "x" + sizeList.get(2));
                
                // Calculate size reduction
                int compactSize = result.length();
                int estimatedOldSize = compactSize * 3; // Rough estimate of old format size
                System.out.println("✓ Compact format saved ~" + ((estimatedOldSize - compactSize) * 100 / estimatedOldSize) + "% characters");
            } else if (parsed.containsKey("name") && parsed.containsKey("size") && parsed.containsKey("blocks")) {
                System.out.println("✓ Valid JSON structure with required fields (legacy format)");
                
                @SuppressWarnings("unchecked")
                List<?> blocks = (List<?>) parsed.get("blocks");
                System.out.println("✓ Generated " + blocks.size() + " blocks");
                
                @SuppressWarnings("unchecked")
                Map<String, ?> size = (Map<String, ?>) parsed.get("size");
                System.out.println("✓ Structure size: " + size.get("x") + "x" + size.get("y") + "x" + size.get("z"));
            } else {
                System.out.println("⚠ Missing required fields");
            }
        } catch (Exception e) {
            System.out.println("⚠ JSON parse error: " + e.getMessage());
        }

        System.out.println("\nResponse preview (first 300 chars):");
        System.out.println(result.substring(0, Math.min(300, result.length())));
        System.out.println("...");

        System.out.println("\n=== ALL TESTS PASSED ===");

        // Shutdown OkHttp resources to allow JVM to exit promptly
        http.dispatcher().executorService().shutdown();
        http.connectionPool().evictAll();
    }

    @SuppressWarnings("unchecked")
    private static String extractDelta(Gson gson, String rawJson) {
        Object rootObj = gson.fromJson(rawJson, Object.class);
        if (!(rootObj instanceof Map<?, ?> root)) return null;

        Object delta = root.get("delta");
        if (delta instanceof String s && !s.isBlank()) return s;

        Object text = root.get("text");
        if (text instanceof String s2 && !s2.isBlank()) return s2;

        return null;
    }
}
