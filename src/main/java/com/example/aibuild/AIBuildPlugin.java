package com.example.aibuild;

import org.bukkit.plugin.java.JavaPlugin;

public class AIBuildPlugin extends JavaPlugin {
    private OpenAIClient openAIClient;
    private BuildHistory buildHistory;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.openAIClient = new OpenAIClient(
                getConfig().getString("openai.api_key"),
                getConfig().getString("openai.model", "gpt-5"),
                getConfig().getInt("openai.timeout_ms", 20000)
        );

        this.buildHistory = new BuildHistory();

        if (getCommand("aibuild") != null) {
            getCommand("aibuild").setExecutor(new AIBuildCommand(this, openAIClient, buildHistory));
        }

        getLogger().info("AIBuild v2 enabled.");
    }
}
