package com.raidextraction.command;

import com.raidextraction.integration.RaidLifecycleCoordinator;
import com.raidextraction.raid.QueueManager;
import com.raidextraction.raid.RaidInstance;
import com.raidextraction.raid.RaidManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class RaidCommand implements CommandExecutor {
    private final RaidManager raidManager;
    private final QueueManager queueManager;
    private final RaidLifecycleCoordinator raidLifecycleCoordinator;

    public RaidCommand(RaidManager raidManager, QueueManager queueManager, RaidLifecycleCoordinator raidLifecycleCoordinator) {
        this.raidManager = Objects.requireNonNull(raidManager, "raidManager");
        this.queueManager = Objects.requireNonNull(queueManager, "queueManager");
        this.raidLifecycleCoordinator = Objects.requireNonNull(raidLifecycleCoordinator, "raidLifecycleCoordinator");
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
        if (!queued) {
            sender.sendMessage("You are already queued for a raid.");
            return true;
        }
        sender.sendMessage("Queued for raid " + raidId + ". Waiting for enough players to deploy.");
        raidLifecycleCoordinator.startFromQueue(raidId).ifPresent(instance ->
                sender.sendMessage("Raid found enough players; deploying raid " + instance.definition().id() + " (id: " + instance.id() + ")."));
        return true;
    }

    private boolean handleLeave(CommandSender sender, UUID playerId) {
        boolean removed = queueManager.remove(playerId);
        sender.sendMessage(removed ? "You have left the raid queue." : "You are not in a raid queue.");
        return true;
    }

    private boolean handleStatus(CommandSender sender, UUID playerId) {
        Optional<RaidInstance> activeRaid = raidManager.activeRaids().stream()
                .filter(raid -> raid.players().contains(playerId))
                .findFirst();
        if (activeRaid.isPresent()) {
            RaidInstance raid = activeRaid.get();
            String eta = formatEta(raid);
            int players = raid.players().size();
            int maxPlayers = raid.definition().maxPlayers();
            sender.sendMessage("You are in raid " + raid.definition().id() + " [" + raid.state() + "] players: " + players + "/" + maxPlayers + " ETA: " + eta + " (id=" + raid.id() + ").");
            return true;
        }
        Optional<String> queuedRaid = queueManager.queuedRaid(playerId);
        if (queuedRaid.isEmpty()) {
            sender.sendMessage("You are not queued for any raid.");
            return true;
        }
        String raidId = queuedRaid.get();
        int size = queueManager.size(raidId);
        var definition = raidManager.definitions().get(raidId);
        int minPlayers = definition != null ? definition.minPlayers() : 0;
        String target = minPlayers > 0 ? Integer.toString(minPlayers) : "?";
        int needed = Math.max(0, minPlayers - size);
        String eta = needed == 0 ? "deploying when ready" : ("waiting for " + needed + " more");
        sender.sendMessage("Queued for raid " + raidId + ". Queue size: " + size + "/" + target + " (" + eta + ").");
        return true;
    }

    private String formatEta(RaidInstance raid) {
        Instant endsAt = raid.raidEndsAt();
        if (endsAt == null) {
            return "pending";
        }
        Duration remaining = Duration.between(Instant.now(), endsAt);
        if (remaining.isNegative()) {
            remaining = Duration.ZERO;
        }
        long minutes = remaining.toMinutes();
        long seconds = remaining.minusMinutes(minutes).toSeconds();
        if (minutes <= 0) {
            return seconds + "s";
        }
        return minutes + "m " + String.format("%02d", seconds) + "s";
    }
}
