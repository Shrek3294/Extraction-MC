package com.raidextraction.command;

import com.raidextraction.integration.RaidLifecycleCoordinator;
import com.raidextraction.raid.RaidManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;

public final class RaidAdminCommand implements CommandExecutor {
    private final JavaPlugin plugin;
    private final RaidManager raidManager;
    private final RaidLifecycleCoordinator raidLifecycleCoordinator;

    public RaidAdminCommand(JavaPlugin plugin, RaidManager raidManager, RaidLifecycleCoordinator raidLifecycleCoordinator) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.raidManager = Objects.requireNonNull(raidManager, "raidManager");
        this.raidLifecycleCoordinator = Objects.requireNonNull(raidLifecycleCoordinator, "raidLifecycleCoordinator");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /raidadmin <start|stop|cancel|force-extract> [raidId|player]");
            return true;
        }
        String action = args[0].toLowerCase();
        return switch (action) {
            case "start" -> handleStart(sender, args);
            case "stop" -> handleStop(sender, args);
            case "cancel" -> handleCancel(sender, args);
            case "force-extract" -> handleForceExtract(sender, args);
            default -> {
                sender.sendMessage("Unknown subcommand. Use /raidadmin <start|stop|cancel|force-extract>.");
                yield true;
            }
        };
    }

    private boolean handleStart(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /raidadmin start <raidId>");
            return true;
        }
        String raidId = args[1];
        if (!raidManager.definitions().containsKey(raidId)) {
            sender.sendMessage("Unknown raid id: " + raidId);
            return true;
        }
        boolean created = raidLifecycleCoordinator.startFromQueue(raidId).isPresent();
        sender.sendMessage(created
                ? "Raid created from queue for " + raidId + "."
                : "Not enough players queued for " + raidId + ".");
        return true;
    }

    private boolean handleStop(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /raidadmin stop <activeRaidId>");
            return true;
        }
        String raidId = args[1];
        boolean removed = raidLifecycleCoordinator.stopRaid(raidId, "Raid stopped by an admin.");
        if (removed) {
            sender.sendMessage("Ended raid " + raidId + ".");
        } else {
            String ids = raidManager.activeRaids().isEmpty()
                    ? "(none)"
                    : raidManager.activeRaids().stream().map(raid -> raid.id() + " (" + raid.definition().id() + ")").toList().toString();
            sender.sendMessage("No active raid with id " + raidId + ". Active raids: " + ids);
        }
        return true;
    }

    private boolean handleCancel(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /raidadmin cancel <activeRaidId>");
            return true;
        }
        String raidId = args[1];
        boolean removed = raidLifecycleCoordinator.stopRaid(raidId, "Raid cancelled by an admin.");
        if (removed) {
            sender.sendMessage("Cancelled raid " + raidId + " and returned players to the lobby.");
        } else {
            String ids = raidManager.activeRaids().isEmpty()
                    ? "(none)"
                    : raidManager.activeRaids().stream().map(raid -> raid.id() + " (" + raid.definition().id() + ")").toList().toString();
            sender.sendMessage("No active raid with id " + raidId + ". Active raids: " + ids);
        }
        return true;
    }

    private boolean handleForceExtract(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /raidadmin force-extract <playerName|playerUuid>");
            return true;
        }
        UUID targetId = resolvePlayer(args[1]);
        if (targetId == null) {
            sender.sendMessage("Could not find player '" + args[1] + "'. Provide an online name or UUID.");
            return true;
        }
        boolean success = raidLifecycleCoordinator.forceExtract(targetId);
        sender.sendMessage(success
                ? "Force-extracted player " + args[1] + " and committed their stash."
                : "Player is not currently in an active raid.");
        return true;
    }

    private UUID resolvePlayer(String input) {
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException ignored) {
            // fall through to name lookup
        }
        Player player = plugin.getServer().getPlayerExact(input);
        return player != null ? player.getUniqueId() : null;
    }
}
