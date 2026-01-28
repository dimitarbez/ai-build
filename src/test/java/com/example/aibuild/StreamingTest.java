package com.example.aibuild;

import com.google.gson.Gson;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
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
        String allowedList = String.join(", ", materials);
        
        String instructions = "You generate Minecraft building plans as STRICT JSON only.\n" +
                "Schema:\n" +
                "{\n" +
                "  \"name\": string,\n" +
                "  \"size\": {\"x\": int, \"y\": int, \"z\": int},\n" +
                "  \"blocks\": [{\"dx\": int, \"dy\": int, \"dz\": int, \"material\": string}]\n" +
                "}\n" +
                "Rules:\n" +
                "- blocks.length <= 100\n" +
                "- dx in [0..size.x-1]\n" +
                "- dy in [0..size.y-1]\n" +
                "- dz in [0..size.z-1]\n" +
                "- ALL blocks at dy=0 must form a solid foundation (no gaps)\n" +
                "- No floating blocks: every block with dy>0 must have a block below it\n" +
                "- material MUST be one of: " + allowedList + "\n" +
                "- Output JSON only. No markdown, no commentary.";

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
        AtomicInteger progressStage = new AtomicInteger(0);
        List<String> progressMessages = new ArrayList<>();

        System.out.println("Sending request: 'small wooden house'");
        System.out.println("Progress updates:");

        EventSourceListener listener = new EventSourceListener() {
            @Override
            public void onOpen(EventSource eventSource, Response response) {
                System.out.println("  → Stream connected (HTTP " + response.code() + ")");
            }

            @Override
            public void onEvent(EventSource eventSource, String id, String type, String data) {
                System.out.println("  [DEBUG] Event: type=" + type + ", data=" + (data != null ? data.substring(0, Math.min(100, data.length())) : "null"));
                
                if (data == null || data.isBlank()) return;

                if ("response.completed".equals(type) || "done".equals(type) || "[DONE]".equals(data)) {
                    System.out.println("  → Stream completion signal received");
                    done.countDown();
                    return;
                }

                if (type != null && type.startsWith("response.output_text")) {
                    String delta = extractDelta(gson, data);
                    if (delta != null && !delta.isEmpty()) {
                        text.append(delta);

                        int len = text.length();
                        if (progressStage.compareAndSet(0, 1)) {
                            String msg = "AI is sketching the build...";
                            System.out.println("  → " + msg);
                            progressMessages.add(msg);
                        } else if (len > 400 && progressStage.compareAndSet(1, 2)) {
                            String msg = "AI is selecting materials...";
                            System.out.println("  → " + msg);
                            progressMessages.add(msg);
                        } else if (len > 800 && progressStage.compareAndSet(2, 3)) {
                            String msg = "AI is finalizing the plan...";
                            System.out.println("  → " + msg);
                            progressMessages.add(msg);
                        }
                    }
                }
            }

            @Override
            public void onFailure(EventSource eventSource, Throwable t, Response response) {
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

        if (!finished) {
            es.cancel();
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

        System.out.println("\n✓ Stream completed successfully");
        System.out.println("Progress messages received: " + progressMessages.size());
        System.out.println("\nResponse preview (first 300 chars):");
        System.out.println(result.substring(0, Math.min(300, result.length())));
        System.out.println("...");
        System.out.println("\nResponse length: " + result.length() + " chars");

        if (result.startsWith("{") && result.contains("blocks")) {
            System.out.println("✓ Response appears to be valid JSON");
        } else {
            System.out.println("⚠ Response may not be valid JSON");
        }

        System.out.println("\n=== ALL TESTS PASSED ===");
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
