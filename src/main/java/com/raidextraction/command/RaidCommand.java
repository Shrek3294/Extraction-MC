package com.raidextraction.command;

import com.raidextraction.raid.QueueManager;
import com.raidextraction.raid.RaidManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class RaidCommand implements CommandExecutor {
    private final RaidManager raidManager;
    private final QueueManager queueManager;

    public RaidCommand(RaidManager raidManager, QueueManager queueManager) {
        this.raidManager = Objects.requireNonNull(raidManager, "raidManager");
        this.queueManager = Objects.requireNonNull(queueManager, "queueManager");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("Usage: /raid <join|leave|status> [raidId]");
            return true;
        }
        String action = args[0].toLowerCase();
        UUID playerId = player.getUniqueId();
        return switch (action) {
            case "join" -> handleJoin(sender, playerId, args);
            case "leave" -> handleLeave(sender, playerId);
            case "status" -> handleStatus(sender, playerId);
            default -> {
                sender.sendMessage("Unknown subcommand. Use /raid <join|leave|status>.");
                yield true;
            }
        };
    }

    private boolean handleJoin(CommandSender sender, UUID playerId, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /raid join <raidId>");
            return true;
        }
        String raidId = args[1];
        if (!raidManager.definitions().containsKey(raidId)) {
            sender.sendMessage("Unknown raid id: " + raidId);
            return true;
        }
        boolean queued = queueManager.enqueue(raidId, playerId);
        sender.sendMessage(queued
                ? "Queued for raid " + raidId + "."
                : "You are already queued for a raid.");
        return true;
    }

    private boolean handleLeave(CommandSender sender, UUID playerId) {
        boolean removed = queueManager.remove(playerId);
        sender.sendMessage(removed ? "You have left the raid queue." : "You are not in a raid queue.");
        return true;
    }

    private boolean handleStatus(CommandSender sender, UUID playerId) {
        Optional<String> queuedRaid = queueManager.queuedRaid(playerId);
        if (queuedRaid.isEmpty()) {
            sender.sendMessage("You are not queued for any raid.");
            return true;
        }
        sender.sendMessage("Queued for raid " + queuedRaid.get() + ".");
        return true;
    }
}
