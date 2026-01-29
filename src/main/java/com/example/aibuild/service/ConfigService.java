package com.example.aibuild.service;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for accessing plugin configuration with defaults and validation
 */
public class ConfigService {
    private final FileConfiguration config;
    
    public ConfigService(FileConfiguration config) {
        this.config = config;
    }
    
    public int getMaxBlocks() {
        return config.getInt("openai.max_blocks", 2500);
    }
    
    public int getPlacePerTick() {
        return config.getInt("build.place_per_tick", 150);
    }
    
    public int getCooldownSeconds() {
        return config.getInt("build.cooldown_seconds", 30);
    }
    
    public int getForwardOffset() {
        return config.getInt("build.forward_offset_blocks", 3);
    }
    
    public boolean isReplaceOnlyAir() {
        return config.getBoolean("build.replace_only_air", true);
    }
    
    public Set<Material> getAllowedMaterials() {
        return config.getStringList("build.allowed_materials").stream()
                .map(name -> {
                    try { 
                        return Material.valueOf(name.toUpperCase()); 
                    } catch (IllegalArgumentException e) { 
                        return null; 
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
    
    public String getApiKey() {
        return config.getString("openai.api_key");
    }
    
    public String getModel() {
        return config.getString("openai.model", "gpt-4o");
    }
    
    public int getTimeoutMs() {
        return config.getInt("openai.timeout_ms", 20000);
    }
    
    public boolean isDebugLoggingEnabled() {
        return config.getBoolean("debug.enabled", false);
    }
}
