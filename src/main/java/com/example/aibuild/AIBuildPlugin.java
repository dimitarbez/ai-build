package com.example.aibuild;

import com.example.aibuild.service.ConfigService;
import org.bukkit.plugin.java.JavaPlugin;

public class AIBuildPlugin extends JavaPlugin {
    private OpenAIClient openAIClient;
    private BuildHistory buildHistory;
    private ConfigService configService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.configService = new ConfigService(getConfig());

        // Priority: 1) config.yml (for production), 2) env var (for development)
        String apiKey = configService.getApiKey();
        if (apiKey == null || apiKey.isBlank() || isPlaceholder(apiKey)) {
            getLogger().warning("API key not found in config.yml, checking environment variables...");
            apiKey = EnvConfig.getOpenAiApiKey();
        }

        // Validate API key
        if (apiKey == null || apiKey.isBlank() || isPlaceholder(apiKey)) {
            getLogger().severe("========================================");
            getLogger().severe("ERROR: OpenAI API key not configured!");
            getLogger().severe("Please edit plugins/AIBuild/config.yml");
            getLogger().severe("and set your API key in the 'openai.api_key' field.");
            getLogger().severe("Get your API key from: https://platform.openai.com/api-keys");
            getLogger().severe("========================================");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.openAIClient = new OpenAIClient(
                apiKey,
                configService.getModel(),
                configService.getTimeoutMs()
        );

        this.buildHistory = new BuildHistory();

        if (getCommand("aibuild") != null) {
            getCommand("aibuild").setExecutor(
                new AIBuildCommand(this, openAIClient, buildHistory, configService)
            );
        }

        getLogger().info("AIBuild v2 enabled successfully.");
        getLogger().info("Using model: " + configService.getModel());
    }

    private boolean isPlaceholder(String key) {
        return key.contains("PUT_YOUR") || 
               key.contains("YOUR_OPENAI") || 
               key.contains("REPLACE_ME") ||
               key.equals("sk-...") ||
               key.length() < 20;
    }
}
