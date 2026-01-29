package com.example.aibuild;

import com.example.aibuild.model.BuildPlan;
import com.example.aibuild.model.BlockSpec;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class BlockPlacer {

    public static void placeBatched(
            JavaPlugin plugin,
            Location origin,
            BlockFace facing,
            BuildPlan plan,
            Set<Material> allowed,
            int placePerTick,
            boolean replaceOnlyAir,
            Player player,
            BuildHistory history
    ) {
        List<BlockSpec> blocks = plan.blocks;
        Deque<BuildHistory.PlacedBlock> placed = new ArrayDeque<>(blocks.size());
        
        // Pre-cache origin coordinates to avoid repeated access
        World world = origin.getWorld();
        int originX = origin.getBlockX();
        int originY = origin.getBlockY();
        int originZ = origin.getBlockZ();

        new org.bukkit.scheduler.BukkitRunnable() {
            int idx = 0;
            long lastProgressMs = System.currentTimeMillis();

            @Override
            public void run() {
                int end = Math.min(idx + placePerTick, blocks.size());

                for (int i = idx; i < end; i++) {
                    BlockSpec b = blocks.get(i);

                    Material m = safeMaterial(b.material, allowed);
                    if (m == null) continue;

                    int[] xz = Rotation.rotateXZ(b.dx, b.dz, facing);
                    int rdx = xz[0];
                    int rdz = xz[1];

                    // Minimize Location allocations - get block directly by coordinates
                    int blockX = originX + rdx;
                    int blockY = originY + b.dy;
                    int blockZ = originZ + rdz;
                    
                    Block block = world.getBlockAt(blockX, blockY, blockZ);

                    if (replaceOnlyAir && block.getType() != Material.AIR) continue;

                    Material prev = block.getType();
                    if (prev == m) continue;

                    block.setType(m, true); // Enable physics so blocks behave normally
                    // Only create Location for history when actually placing a block
                    placed.addLast(new BuildHistory.PlacedBlock(
                        new Location(world, blockX, blockY, blockZ), prev
                    ));
                }

                idx = end;
                
                // Show progress every 5 seconds
                long now = System.currentTimeMillis();
                if (now - lastProgressMs > 5000 && idx < blocks.size()) {
                    int percent = (idx * 100) / blocks.size();
                    player.sendMessage(ChatColor.GRAY + "⚒ Building... " + percent + "% (" + idx + "/" + blocks.size() + " blocks)");
                    lastProgressMs = now;
                }
                
                if (idx >= blocks.size()) {
                    history.store(player.getUniqueId(), placed);
                    int totalPlaced = placed.size();
                    player.sendMessage(ChatColor.GREEN + "✓ Build complete: " + totalPlaced + " blocks placed" + 
                        (plan.name != null ? " (" + plan.name + ")" : ""));
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public static void undoLast(JavaPlugin plugin, Player player, BuildHistory history, int placePerTick) {
        Deque<BuildHistory.PlacedBlock> last = history.pop(player.getUniqueId());
        if (last == null || last.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "Nothing to undo.");
            return;
        }

        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                int n = 0;
                while (n < placePerTick && !last.isEmpty()) {
                    BuildHistory.PlacedBlock pb = last.removeLast(); // reverse order
                    pb.loc().getBlock().setType(pb.previous(), true); // Enable physics for undo too
                    n++;
                }
                if (last.isEmpty()) {
                    player.sendMessage(ChatColor.GREEN + "Undo complete.");
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static Material safeMaterial(String materialName, Set<Material> allowed) {
        if (materialName == null) return null;
        try {
            Material m = Material.valueOf(materialName.toUpperCase(Locale.ROOT));
            return allowed.contains(m) ? m : null;
        } catch (Exception e) {
            return null;
        }
    }
}
