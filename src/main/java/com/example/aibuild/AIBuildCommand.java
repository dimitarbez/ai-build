package com.example.aibuild;

import com.google.gson.Gson;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class AIBuildCommand implements CommandExecutor {
    private final AIBuildPlugin plugin;
    private final OpenAIClient client;
    private final BuildHistory history;
    private final Gson gson = new Gson();

    public AIBuildCommand(AIBuildPlugin plugin, OpenAIClient client, BuildHistory history) {
        this.plugin = plugin;
        this.client = client;
        this.history = history;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!p.hasPermission("aibuild.use")) {
            p.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (args.length == 0) {
            p.sendMessage(ChatColor.YELLOW + "Usage: /aibuild <description|undo>");
            return true;
        }

        int placePerTick = plugin.getConfig().getInt("build.place_per_tick", 150);

        // /aibuild undo
        if (args.length == 1 && args[0].equalsIgnoreCase("undo")) {
            BlockPlacer.undoLast(plugin, p, history, placePerTick);
            return true;
        }

        // cooldown
        int cooldownSec = plugin.getConfig().getInt("build.cooldown_seconds", 30);
        long lastAt = history.getLastBuildAtMs(p.getUniqueId());
        long now = System.currentTimeMillis();
        long waitMs = (cooldownSec * 1000L) - (now - lastAt);
        if (waitMs > 0) {
            long s = (waitMs + 999) / 1000;
            p.sendMessage(ChatColor.RED + "Cooldown: wait " + s + "s");
            return true;
        }

        String userPrompt = String.join(" ", args);

        int maxBlocks = plugin.getConfig().getInt("openai.max_blocks", 2500);
        boolean replaceOnlyAir = plugin.getConfig().getBoolean("build.replace_only_air", true);
        int forwardOffset = plugin.getConfig().getInt("build.forward_offset_blocks", 3);

        Set<Material> allowed = plugin.getConfig().getStringList("build.allowed_materials").stream()
                .map(name -> {
                    try { return Material.valueOf(name); } catch (Exception e) { return null; }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // origin: in front of player
        Location base = p.getLocation().getBlock().getLocation();
        BlockFace facing = yawToCardinal(p.getLocation().getYaw());
        Location origin = base.clone().add(facing.getModX() * forwardOffset, 0, facing.getModZ() * forwardOffset);

        p.sendMessage(ChatColor.GRAY + "Generating build plan...");

        // network call async
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String json = client.generateBuildPlanJson(userPrompt, maxBlocks, allowed);

                BuildPlan plan = gson.fromJson(json, BuildPlan.class);
                if (plan == null || plan.blocks == null || plan.size == null) {
                    p.sendMessage(ChatColor.RED + "AI returned invalid plan.");
                    return;
                }
                if (plan.blocks.size() > maxBlocks) {
                    p.sendMessage(ChatColor.RED + "Plan too large (" + plan.blocks.size() + " blocks).");
                    return;
                }

                String validationError = BuildValidator.validate(plan);
                if (validationError != null) {
                    p.sendMessage(ChatColor.RED + "Invalid AI plan: " + validationError);
                    return;
                }

                // place on main thread
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    BlockPlacer.placeBatched(
                            plugin,
                            origin,
                            facing,
                            plan,
                            allowed,
                            placePerTick,
                            replaceOnlyAir,
                            p,
                            history
                    );
                });

            } catch (Exception e) {
                p.sendMessage(ChatColor.RED + "Error: " + e.getMessage());
            }
        });

        return true;
    }

    private BlockFace yawToCardinal(float yaw) {
        float rot = (yaw % 360 + 360) % 360;
        if (rot >= 315 || rot < 45) return BlockFace.SOUTH;
        if (rot < 135) return BlockFace.WEST;
        if (rot < 225) return BlockFace.NORTH;
        return BlockFace.EAST;
    }

    // DTOs matching AI JSON
    public static class BuildPlan {
        public String name;
        public Size size;
        public List<BlockSpec> blocks;
    }

    public static class Size {
        public int x, y, z;
    }

    public static class BlockSpec {
        public int dx, dy, dz;
        public String material;
    }
}
