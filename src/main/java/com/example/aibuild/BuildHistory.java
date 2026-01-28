package com.example.aibuild;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.*;

public class BuildHistory {
    private final Map<UUID, Deque<PlacedBlock>> lastBuild = new HashMap<>();
    private final Map<UUID, Long> lastBuildAtMs = new HashMap<>();

    public void store(UUID playerId, Deque<PlacedBlock> blocksPlaced) {
        lastBuild.put(playerId, blocksPlaced);
        lastBuildAtMs.put(playerId, System.currentTimeMillis());
    }

    public Deque<PlacedBlock> pop(UUID playerId) {
        return lastBuild.remove(playerId);
    }

    public long getLastBuildAtMs(UUID playerId) {
        return lastBuildAtMs.getOrDefault(playerId, 0L);
    }

    public record PlacedBlock(Location loc, Material previous) {}
}
