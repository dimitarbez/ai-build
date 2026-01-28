package com.example.aibuild;

import org.bukkit.plugin.java.JavaPlugin;

public class AIBuildPlugin extends JavaPlugin {
    private OpenAIClient openAIClient;
    private BuildHistory buildHistory;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        String apiKey = EnvConfig.getOpenAiApiKey();
        if (apiKey == null) {
            apiKey = getConfig().getString("openai.api_key");
        }

        this.openAIClient = new OpenAIClient(
                apiKey,
                getConfig().getString("openai.model", "gpt-4o"),
                getConfig().getInt("openai.timeout_ms", 20000)
        );

        this.buildHistory = new BuildHistory();

        if (getCommand("aibuild") != null) {
            getCommand("aibuild").setExecutor(new AIBuildCommand(this, openAIClient, buildHistory));
        }

        getLogger().info("AIBuild v2 enabled.");
    }
}
