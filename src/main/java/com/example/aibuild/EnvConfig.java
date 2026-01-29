package com.example.aibuild;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Small helper to resolve configuration values.
 * Order of precedence for a key:
 * 1) System.getenv(key)
 * 2) System.getProperty(key)
 * 3) .env file in project root (if present)
 */
public final class EnvConfig {
    private static final Map<String, String> DOTENV = new HashMap<>();

    static {
        loadDotenv();
    }

    private EnvConfig() { }

    private static void loadDotenv() {
        try {
            Path cwd = Paths.get(".").toAbsolutePath().normalize();
            File dotenv = cwd.resolve(".env").toFile();
            if (!dotenv.exists() || !dotenv.isFile()) return;

            try (BufferedReader br = new BufferedReader(new FileReader(dotenv))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eq = line.indexOf('=');
                    if (eq <= 0) continue;
                    String key = line.substring(0, eq).trim();
                    String value = line.substring(eq + 1).trim();
                    // Remove surrounding quotes if present
                    if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    DOTENV.putIfAbsent(key, value);
                }
            }
        } catch (IOException e) {
            // ignore - dotenv is optional
        }
    }

    /**
     * Get a configuration value by key with standard fallback order.
     */
    public static String get(String key) {
        String v = System.getenv(key);
        if (v != null && !v.isEmpty()) return v;
        v = System.getProperty(key);
        if (v != null && !v.isEmpty()) return v;
        v = DOTENV.get(key);
        return (v != null && !v.isEmpty()) ? v : null;
    }

    /** Convenience accessor for OpenAI API key. */
    public static String getOpenAiApiKey() {
        return get("OPENAI_API_KEY");
    }

    /** Convenience accessor for OpenAI timeout in milliseconds. */
    public static Integer getOpenAiTimeoutMs() {
        String v = get("OPENAI_TIMEOUT_MS");
        if (v == null) return null;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
