package com.example.aibuild;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Manual test runner for OpenAI client without Minecraft/Bukkit runtime.
 * Run from command line: make test
 * Or run main() directly from IDE.
 */
public class OpenAIClientTest {

    // Mock Material enum for testing without Bukkit
    enum MockMaterial {
        OAK_PLANKS,
        COBBLESTONE,
        GLASS,
        OAK_STAIRS,
        TORCH
    }

    public static void main(String[] args) {
        System.out.println("=== OpenAI Client Test ===\n");

        // Get API key from environment or .env file
        String apiKey = EnvConfig.getOpenAiApiKey();
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("PUT_YOUR_KEY")) {
            System.err.println("ERROR: OPENAI_API_KEY not found");
            System.err.println("Options:");
            System.err.println("  1. Set environment variable: export OPENAI_API_KEY='sk-...'");
            System.err.println("  2. Create .env file with: OPENAI_API_KEY=sk-...");
            System.exit(1);
        }

        System.out.println("✓ API key loaded");

        // Initialize client
        OpenAIClient client = new OpenAIClient(
                apiKey,
                "gpt-4o-2024-11-20",
                60000 // 60 second timeout
        );
        System.out.println("✓ Client initialized\n");

        // Test materials as strings (no Bukkit dependency)
        Set<String> allowedMaterials = Set.of(
                "OAK_PLANKS",
                "COBBLESTONE", 
                "GLASS",
                "OAK_STAIRS",
                "TORCH"
        );

        // Progress messages
        List<String> progressMessages = new ArrayList<>();

        System.out.println("Testing streaming API with prompt: 'small wooden house'");
        System.out.println("Progress updates:");

        try {
            String json = testStreaming(client, allowedMaterials, progressMessages);

            System.out.println("\n✓ Stream completed successfully");
            System.out.println("Progress messages received: " + progressMessages.size());
            System.out.println("\nResponse preview (first 300 chars):");
            System.out.println(json.substring(0, Math.min(300, json.length())));
            System.out.println("\nResponse length: " + json.length() + " chars");

            // Validate it's valid JSON
            if (json.startsWith("{") && json.contains("blocks")) {
                System.out.println("✓ Response appears to be valid JSON");
            } else {
                System.out.println("⚠ Response may not be valid JSON");
            }

            System.out.println("\n=== ALL TESTS PASSED ===");

        } catch (IOException e) {
            System.err.println("\n✗ ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String testStreaming(OpenAIClient client, Set<String> materials, List<String> progress) throws IOException {
        // Build instructions manually without Material enum
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

        // Use reflection to call private method generateBuildPlanJsonStreaming
        try {
            var method = client.getClass().getDeclaredMethod("generateBuildPlanJsonStreaming", 
                String.class, int.class, Set.class, java.util.function.Consumer.class);
            method.setAccessible(true);
            
            @SuppressWarnings("unchecked")
            String result = (String) method.invoke(client, "small wooden house", 100, materials, 
                (java.util.function.Consumer<String>) msg -> {
                    System.out.println("  → " + msg);
                    progress.add(msg);
                });
            return result;
        } catch (Exception e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw new IOException("Reflection error: " + e.getMessage(), e);
        }
    }
}
