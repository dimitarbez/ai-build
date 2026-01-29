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

        String apiKey = EnvConfig.getOpenAiApiKey();
        if (apiKey == null) {
            apiKey = configService.getApiKey();
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

        getLogger().info("AIBuild v2 enabled.");
    }
}
