package com.raidextraction.command;

import com.raidextraction.extraction.ExtractionService;
import com.raidextraction.integration.RaidLifecycleCoordinator;
import com.raidextraction.raid.QueueManager;
import com.raidextraction.raid.RaidInstance;
import com.raidextraction.raid.RaidManager;
import com.raidextraction.ux.HudService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class RaidCommand implements CommandExecutor {
    private final RaidManager raidManager;
    private final QueueManager queueManager;
    private final RaidLifecycleCoordinator raidLifecycleCoordinator;
    private final ExtractionService extractionService;
    private final HudService hudService;

    public RaidCommand(RaidManager raidManager,
                       QueueManager queueManager,
                       RaidLifecycleCoordinator raidLifecycleCoordinator,
                       ExtractionService extractionService,
                       HudService hudService) {
        this.raidManager = Objects.requireNonNull(raidManager, "raidManager");
        this.queueManager = Objects.requireNonNull(queueManager, "queueManager");
        this.raidLifecycleCoordinator = Objects.requireNonNull(raidLifecycleCoordinator, "raidLifecycleCoordinator");
        this.extractionService = Objects.requireNonNull(extractionService, "extractionService");
        this.hudService = Objects.requireNonNull(hudService, "hudService");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("Usage: /raid <join|leave|status|hud> [raidId]");
            return true;
        }
        String action = args[0].toLowerCase();
        UUID playerId = player.getUniqueId();
        ExtractionService.CooldownResult cooldown = extractionService.checkCommandCooldown(playerId);
        if (!cooldown.allowed()) {
            long seconds = Math.max(1L, (long) Math.ceil(cooldown.remaining().toMillis() / 1000.0));
            sender.sendMessage("Slow down. Try again in " + seconds + "s.");
            raidLifecycleCoordinator.logCommandCooldown(playerId, "/raid " + action, cooldown.remaining());
            return true;
        }
        return switch (action) {
            case "join" -> handleJoin(sender, playerId, args);
            case "leave" -> handleLeave(sender, playerId);
            case "status" -> handleStatus(sender, playerId);
            case "hud" -> handleHud(player);
            default -> {
                sender.sendMessage("Unknown subcommand. Use /raid <join|leave|status|hud>.");
                yield true;
            }
        };
    }

    private boolean handleHud(Player player) {
        hudService.toggleFor(player);
        return true;
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
        if (raidManager.hasActiveRaidForDefinition(raidId)) {
            sender.sendMessage("Raid " + raidId + " is already in progress. Please wait for it to finish.");
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
            List<RaidInstance> activeRaids = raidManager.activeRaids();
            if (activeRaids.isEmpty()) {
                sender.sendMessage("No active raids right now.");
            } else {
                sender.sendMessage("Active raids:");
                for (RaidInstance raid : activeRaids) {
                    sender.sendMessage(" - " + formatRaidSummary(raid));
                }
            }
            sender.sendMessage("Queues:");
            for (var entry : raidManager.definitions().entrySet()) {
                String raidId = entry.getKey();
                int size = queueManager.size(raidId);
                int minPlayers = entry.getValue().minPlayers();
                int maxPlayers = entry.getValue().maxPlayers();
                String active = raidManager.hasActiveRaidForDefinition(raidId) ? "active" : "idle";
                sender.sendMessage(" - " + raidId + " queue " + size + "/" + minPlayers + " (" + active
                        + ", max " + maxPlayers + ")");
            }
            sender.sendMessage("Use /raid join <raidId> to queue.");
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

    private String formatRaidSummary(RaidInstance raid) {
        String eta = formatEta(raid);
        int players = raid.players().size();
        int maxPlayers = raid.definition().maxPlayers();
        return raid.definition().id() + " [id=" + raid.id() + "] " + raid.state()
                + " players: " + players + "/" + maxPlayers + " ETA: " + eta;
    }
}
