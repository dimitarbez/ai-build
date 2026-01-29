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

        new org.bukkit.scheduler.BukkitRunnable() {
            int idx = 0;

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

                    Location at = origin.clone().add(rdx, b.dy, rdz);
                    Block block = at.getBlock();

                    if (replaceOnlyAir && block.getType() != Material.AIR) continue;

                    Material prev = block.getType();
                    if (prev == m) continue;

                    block.setType(m, false);
                    placed.addLast(new BuildHistory.PlacedBlock(at.clone(), prev));
                }

                idx = end;
                if (idx >= blocks.size()) {
                    history.store(player.getUniqueId(), placed);
                    player.sendMessage(ChatColor.GREEN + "Build complete: " + (plan.name != null ? plan.name : "AI build"));
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
                    pb.loc().getBlock().setType(pb.previous(), false);
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
