package com.example.aibuild;

import com.example.aibuild.exception.BuildValidationException;
import com.example.aibuild.exception.OpenAIException;
import com.example.aibuild.exception.PlanParseException;
import com.example.aibuild.model.BuildPlan;
import com.example.aibuild.service.ConfigService;
import com.example.aibuild.service.PlanParser;
import com.example.aibuild.util.DebugTimer;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Logger;

public class AIBuildCommand implements CommandExecutor {
    private final AIBuildPlugin plugin;
    private final OpenAIClient client;
    private final BuildHistory history;
    private final ConfigService config;
    private final PlanParser planParser;
    private final Logger logger;

    public AIBuildCommand(AIBuildPlugin plugin, OpenAIClient client, BuildHistory history, ConfigService config) {
        this.plugin = plugin;
        this.client = client;
        this.history = history;
        this.config = config;
        this.planParser = new PlanParser();
        this.logger = plugin.getLogger();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!p.hasPermission("aibuild.use")) {
            sendError(p, "No permission.");
            return true;
        }
        if (args.length == 0) {
            p.sendMessage(ChatColor.YELLOW + "Usage: /aibuild <description|undo>");
            return true;
        }

        int placePerTick = config.getPlacePerTick();

        // /aibuild undo
        if (args.length == 1 && args[0].equalsIgnoreCase("undo")) {
            BlockPlacer.undoLast(plugin, p, history, placePerTick);
            return true;
        }

        // cooldown
        int cooldownSec = config.getCooldownSeconds();
        long lastAt = history.getLastBuildAtMs(p.getUniqueId());
        long now = System.currentTimeMillis();
        long waitMs = (cooldownSec * 1000L) - (now - lastAt);
        if (waitMs > 0) {
            long s = (waitMs + 999) / 1000;
            sendError(p, "Cooldown: wait " + s + "s");
            return true;
        }

        String userPrompt = String.join(" ", args);

        int maxBlocks = config.getMaxBlocks();
        boolean replaceOnlyAir = config.isReplaceOnlyAir();
        int forwardOffset = config.getForwardOffset();
        Set<Material> allowed = config.getAllowedMaterials();

        // origin: in front of player
        Location base = p.getLocation().getBlock().getLocation();
        BlockFace facing = yawToCardinal(p.getLocation().getYaw());
        Location origin = base.clone().add(facing.getModX() * forwardOffset, 0, facing.getModZ() * forwardOffset);

        p.sendMessage(ChatColor.GRAY + "⚒ Generating build plan...");

        boolean debugEnabled = config.isDebugLoggingEnabled();
        DebugTimer totalTimer = DebugTimer.start(logger, debugEnabled, "Total build generation");

        // network call async
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                DebugTimer apiTimer = DebugTimer.start(logger, debugEnabled, "OpenAI API call");
                String json = client.generateBuildPlanJsonStreaming(
                        userPrompt,
                        maxBlocks,
                        allowed,
                        msg -> sendSync(p, ChatColor.AQUA + msg)
                );
                apiTimer.stop();

                // Parse with material names for compact format
                String[] matArray = allowed.stream()
                        .map(Enum::name)
                        .sorted()
                        .toArray(String[]::new);
                
                DebugTimer parseTimer = DebugTimer.start(logger, debugEnabled, "JSON parsing");
                BuildPlan plan;
                try {
                    plan = planParser.parse(json, matArray);
                } catch (PlanParseException e) {
                    logger.warning("JSON parse error: " + e.getMessage());
                    if (e.getJsonSnippet() != null && !e.getJsonSnippet().isBlank()) {
                        logger.warning("JSON snippet: " + e.getJsonSnippet());
                    }
                    sendErrorSync(p, "Failed to parse AI response");
                    return;
                }
                parseTimer.stop();

                if (plan == null || plan.blocks == null || plan.size == null) {
                    logger.warning("AI returned invalid plan structure");
                    sendErrorSync(p, "AI returned invalid plan");
                    return;
                }
                
                if (plan.blocks.size() > maxBlocks) {
                    logger.info(String.format("Plan rejected: %d blocks exceeds limit of %d", 
                        plan.blocks.size(), maxBlocks));
                    sendErrorSync(p, String.format("Plan too large: %d blocks (max %d)", 
                        plan.blocks.size(), maxBlocks));
                    return;
                }

                DebugTimer validationTimer = DebugTimer.start(logger, debugEnabled, "Plan validation");
                try {
                    BuildValidator.validate(plan);
                } catch (BuildValidationException e) {
                    logger.warning("Build validation failed: " + e.getMessage());
                    sendErrorSync(p, "Invalid plan: " + e.getMessage());
                    return;
                }
                validationTimer.stop();

                totalTimer.stop(String.format("%d blocks in %dx%dx%d", 
                    plan.blocks.size(), plan.size.x, plan.size.y, plan.size.z));

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

            } catch (OpenAIException e) {
                logger.severe("OpenAI API error: " + e.getMessage());
                String userMsg;
                if (e.isAuthError()) {
                    userMsg = "API authentication failed - check server configuration";
                } else if (e.isRateLimited()) {
                    userMsg = "Rate limited - try again later";
                } else if (e.isTimeout()) {
                    userMsg = "Request timed out - try a simpler build";
                } else {
                    userMsg = "AI service error - try again";
                }
                sendErrorSync(p, userMsg);
            } catch (Exception e) {
                logger.severe("Unexpected error: " + e.getMessage());
                e.printStackTrace();
                sendErrorSync(p, "An unexpected error occurred");
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

    private void sendSync(Player player, String message) {
        plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage(message));
    }

    private void sendError(Player player, String message) {
        player.sendMessage(formatError(message));
    }

    private void sendErrorSync(Player player, String message) {
        sendSync(player, formatError(message));
    }

    private String formatError(String message) {
        return ChatColor.DARK_RED + "[AIBuild] " + ChatColor.RED + message;
    }
}
