package com.raidextraction.integration;

import com.raidextraction.config.ConfigManager;
import com.raidextraction.config.model.EvacZoneDefinition;
import com.raidextraction.config.model.RaidDefinition;
import com.raidextraction.extraction.EvacTracker;
import com.raidextraction.extraction.ExtractionService;
import com.raidextraction.loot.LootService;
import com.raidextraction.raid.QueueManager;
import com.raidextraction.raid.RaidInstance;
import com.raidextraction.raid.RaidManager;
import com.raidextraction.raid.RaidState;
import com.raidextraction.stash.ItemData;
import com.raidextraction.stash.StashService;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Coordinates raid lifecycle wiring that depends on Paper API: teleport, inventory handling, and cleanup.
 */
public final class RaidLifecycleCoordinator {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final RaidManager raidManager;
    private final QueueManager queueManager;
    private final ExtractionService extractionService;
    private final LootService lootService;
    private final StashService stashService;
    private final InventorySnapshotService inventorySnapshotService;
    private final TeleportService teleportService;
    private final RegionProvider regionProvider;
    private final ItemDataMapper itemDataMapper;
    private final Logger logger;
    private final RaidStateStore stateStore;
    private final Map<String, BukkitTask> raidTimeouts = new HashMap<>();
    private final Map<String, Instant> raidDeadlines = new HashMap<>();
    private final Map<UUID, String> activeEvacZones = new HashMap<>();
    private final Map<UUID, BukkitTask> evacCountdowns = new HashMap<>();
    private final int evacDurationSeconds;

    public RaidLifecycleCoordinator(JavaPlugin plugin,
                                    ConfigManager configManager,
                                    RaidManager raidManager,
                                    QueueManager queueManager,
                                    ExtractionService extractionService,
                                    LootService lootService,
                                    StashService stashService,
                                    InventorySnapshotService inventorySnapshotService,
                                    TeleportService teleportService,
                                    RegionProvider regionProvider,
                                    ItemDataMapper itemDataMapper) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.raidManager = Objects.requireNonNull(raidManager, "raidManager");
        this.queueManager = Objects.requireNonNull(queueManager, "queueManager");
        this.extractionService = Objects.requireNonNull(extractionService, "extractionService");
        this.lootService = Objects.requireNonNull(lootService, "lootService");
        this.stashService = Objects.requireNonNull(stashService, "stashService");
        this.inventorySnapshotService = Objects.requireNonNull(inventorySnapshotService, "inventorySnapshotService");
        this.teleportService = Objects.requireNonNull(teleportService, "teleportService");
        this.regionProvider = Objects.requireNonNull(regionProvider, "regionProvider");
        this.itemDataMapper = Objects.requireNonNull(itemDataMapper, "itemDataMapper");
        this.logger = plugin.getLogger();
        this.stateStore = new RaidStateStore(plugin.getDataFolder().toPath().resolve("raid_state.yml"), this.logger);
        this.evacDurationSeconds = Math.max(configManager.getMainConfig().getInt("evac_duration_seconds", 10), 1);
    }

    public Optional<RaidInstance> startFromQueue(String raidDefinitionId) {
        Optional<RaidInstance> raid = raidManager.tryCreateFromQueue(raidDefinitionId);
        raid.ifPresent(instance -> {
            logEvent(Level.INFO, "raid_created",
                    "raidId", instance.id(),
                    "definitionId", instance.definition().id(),
                    "players", instance.players().size());
            deployRaid(instance);
        });
        return raid;
    }

    public boolean stopRaid(String raidId, String message) {
        return endRaid(raidId, message, true, true);
    }

    public void rehydrateState() {
        stateStore.load().ifPresent(snapshot -> {
            int restoredRaids = 0;
            int restoredEvacs = 0;
            logEvent(Level.INFO, "raid_rehydrate_start", "raids", snapshot.raids().size(), "evacs", snapshot.evacSnapshots().size());
            for (RaidStateStore.RaidSnapshot raidSnapshot : snapshot.raids()) {
                if (raidSnapshot.state() != RaidState.IN_RAID && raidSnapshot.state() != RaidState.EXTRACTING) {
                    continue;
                }
                RaidDefinition definition = raidManager.definitions().get(raidSnapshot.definitionId());
                if (definition == null) {
                    logEvent(Level.WARNING, "raid_rehydrate_skip", "raidId", raidSnapshot.raidId(), "reason", "missing_definition");
                    continue;
                }
                Optional<RaidInstance> restored = raidManager.rehydrateRaid(
                        raidSnapshot.raidId(),
                        definition,
                        raidSnapshot.state(),
                        raidSnapshot.createdAt(),
                        raidSnapshot.stateChangedAt(),
                        raidSnapshot.players());
                if (restored.isEmpty()) {
                    logEvent(Level.WARNING, "raid_rehydrate_skip", "raidId", raidSnapshot.raidId(), "reason", "duplicate_or_invalid");
                    continue;
                }
                restoredRaids++;
                Instant deadline = raidSnapshot.raidDeadline();
                if (deadline == null && raidSnapshot.state() == RaidState.IN_RAID) {
                    deadline = raidSnapshot.stateChangedAt().plusSeconds(definition.durationSeconds());
                }
                if (deadline != null) {
                    scheduleRaidTimeout(restored.get(), deadline);
                }
            }

            Set<String> activeRaidIds = new HashSet<>();
            for (RaidInstance raid : raidManager.activeRaids()) {
                activeRaidIds.add(raid.id());
            }

            List<EvacTracker.EvacSnapshot> evacSnapshots = new ArrayList<>();
            for (EvacTracker.EvacSnapshot evacSnapshot : snapshot.evacSnapshots()) {
                if (activeRaidIds.contains(evacSnapshot.raidId())) {
                    evacSnapshots.add(evacSnapshot);
                }
            }
            extractionService.restoreEvacSnapshots(evacSnapshots);

            for (EvacTracker.EvacSnapshot evacSnapshot : evacSnapshots) {
                Optional<RaidInstance> raidOptional = raidManager.getRaid(evacSnapshot.raidId());
                if (raidOptional.isEmpty()) {
                    continue;
                }
                RaidInstance raidInstance = raidOptional.get();
                if (!raidInstance.players().contains(evacSnapshot.playerId())) {
                    continue;
                }
                Optional<EvacZoneDefinition> zoneOptional = raidInstance.definition().evacZones().stream()
                        .filter(zone -> zone.name().equals(evacSnapshot.zoneName()))
                        .findFirst();
                if (zoneOptional.isEmpty()) {
                    logEvent(Level.WARNING, "evac_rehydrate_skip", "raidId", evacSnapshot.raidId(), "playerId", evacSnapshot.playerId());
                    continue;
                }
                activeEvacZones.put(evacSnapshot.playerId(), evacSnapshot.zoneName());
                startEvacCountdownTask(raidInstance, evacSnapshot.playerId(), zoneOptional.get());
                restoredEvacs++;
            }

            logEvent(Level.INFO, "raid_rehydrate_complete", "raids", restoredRaids, "evacs", restoredEvacs);
        });
    }

    public void persistState() {
        RaidStateStore.RaidStateSnapshot snapshot = buildSnapshot();
        if (snapshot.raids().isEmpty() && snapshot.evacSnapshots().isEmpty()) {
            stateStore.clear();
            return;
        }
        stateStore.save(snapshot);
    }

    public boolean forceExtract(UUID playerId) {
        Optional<RaidInstance> raidOptional = findRaidByPlayer(playerId);
        if (raidOptional.isEmpty()) {
            return false;
        }
        RaidInstance raidInstance = raidOptional.get();
        logEvent(Level.INFO, "raid_force_extract", "raidId", raidInstance.id(), "playerId", playerId);
        extractionService.forceComplete(raidInstance.id(), playerId);
        activeEvacZones.remove(playerId);
        cancelEvacCountdown(playerId);
        handleExtractionSuccess(raidInstance, playerId);
        return true;
    }

    public void handlePlayerQuit(UUID playerId) {
        queueManager.remove(playerId);
        for (RaidInstance raid : raidManager.activeRaids()) {
            if (!raid.players().contains(playerId)) {
                continue;
            }
            raid.removePlayer(playerId);
            activeEvacZones.remove(playerId);
            cancelEvacCountdown(playerId);
            extractionService.cancelExtraction(raid.id(), playerId);
            inventorySnapshotService.restore(playerId);
            inventorySnapshotService.clear(playerId);
            if (raid.players().isEmpty()) {
                endRaid(raid.id(), "Raid ended because all players left.", false, false);
            }
            break;
        }
    }

    public void handlePlayerDeath(UUID playerId) {
        findRaidByPlayer(playerId).ifPresent(raid -> handleFailure(raid, playerId, "You died in the raid. Returning to lobby with your pre-raid loadout."));
    }

    public void handlePlayerMove(UUID playerId) {
        Optional<RaidInstance> raidOptional = findRaidByPlayer(playerId);
        if (raidOptional.isEmpty()) {
            return;
        }
        RaidInstance raid = raidOptional.get();
        if (raid.state() != RaidState.IN_RAID && raid.state() != RaidState.EXTRACTING) {
            return;
        }
        if (!regionProvider.isInRaidWorld(playerId, raid.definition())) {
            handleFailure(raid, playerId, "You left the raid area. Returning to lobby.");
            return;
        }
        if (!raid.definition().evacZones().isEmpty()) {
            handleEvacZoneCheck(raid, playerId);
        }
    }

    private void deployRaid(RaidInstance raidInstance) {
        RaidDefinition definition = raidInstance.definition();
        raidInstance.markDeploying();
        logEvent(Level.INFO, "raid_deploying",
                "raidId", raidInstance.id(),
                "definitionId", definition.id(),
                "players", raidInstance.players().size());

        int deployed = 0;
        for (UUID playerId : new ArrayList<>(raidInstance.players())) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                raidInstance.removePlayer(playerId);
                continue;
            }
            inventorySnapshotService.snapshot(playerId);
            player.getInventory().clear();
            if (!teleportService.sendToRaid(playerId, definition)) {
                raidInstance.removePlayer(playerId);
                inventorySnapshotService.restore(playerId);
                inventorySnapshotService.clear(playerId);
                player.sendMessage("Raid deployment failed; please try joining again.");
                continue;
            }
            deployed++;
            player.sendMessage("Raid starting: " + definition.id() + ". Extract before the timer ends!");
        }

        if (deployed < definition.minPlayers()) {
            logEvent(Level.WARNING, "raid_deploy_failed",
                    "raidId", raidInstance.id(),
                    "definitionId", definition.id(),
                    "deployed", deployed,
                    "minPlayers", definition.minPlayers());
            endRaid(raidInstance.id(), "Raid canceled because not enough players could deploy.", true, true);
            return;
        }

        raidInstance.markInRaid();
        logEvent(Level.INFO, "raid_started",
                "raidId", raidInstance.id(),
                "definitionId", definition.id(),
                "players", raidInstance.players().size());
        spawnRaidLoot(raidInstance);
        scheduleRaidTimeout(raidInstance);
    }

    private void spawnRaidLoot(RaidInstance raidInstance) {
        RaidDefinition definition = raidInstance.definition();
        World world = plugin.getServer().getWorld(definition.world());
        if (world == null) {
            logEvent(Level.WARNING, "loot_spawn_failed",
                    "raidId", raidInstance.id(),
                    "definitionId", definition.id(),
                    "world", definition.world());
            return;
        }
        int rolls = Math.max(raidInstance.players().size(), 1);
        List<ItemData> loot = lootService.roll(definition.lootTableId(), rolls);
        if (loot.isEmpty()) {
            logEvent(Level.WARNING, "loot_roll_empty",
                    "raidId", raidInstance.id(),
                    "definitionId", definition.id(),
                    "tableId", definition.lootTableId());
            return;
        }

        var dropLocation = world.getSpawnLocation().add(0.5, 1.0, 0.5);
        for (ItemData itemData : loot) {
            for (ItemStack stack : toItemStacks(itemData)) {
                world.dropItemNaturally(dropLocation, stack);
            }
        }
        broadcastToRaid(raidInstance, "Supply drop inbound! Loot has spawned near the raid start.");
    }

    private void scheduleRaidTimeout(RaidInstance raidInstance) {
        cancelRaidTimeout(raidInstance.id());
        long durationSeconds = Math.max(raidInstance.definition().durationSeconds(), 1);
        Instant deadline = Instant.now().plusSeconds(durationSeconds);
        scheduleRaidTimeout(raidInstance, deadline);
    }

    private void scheduleRaidTimeout(RaidInstance raidInstance, Instant deadline) {
        cancelRaidTimeout(raidInstance.id());
        long remainingSeconds = Duration.between(Instant.now(), deadline).getSeconds();
        if (remainingSeconds <= 0) {
            endRaid(raidInstance.id(), "Raid ended: time expired.", true, true);
            return;
        }
        long ticks = remainingSeconds * 20L;
        BukkitTask task = plugin.getServer().getScheduler()
                .runTaskLater(plugin, () -> endRaid(raidInstance.id(), "Raid ended: time expired.", true, true), ticks);
        raidTimeouts.put(raidInstance.id(), task);
        raidDeadlines.put(raidInstance.id(), deadline);
    }

    private void cancelRaidTimeout(String raidId) {
        BukkitTask task = raidTimeouts.remove(raidId);
        if (task != null) {
            task.cancel();
        }
        raidDeadlines.remove(raidId);
    }

    private Optional<RaidInstance> findRaidByPlayer(UUID playerId) {
        for (RaidInstance raid : raidManager.activeRaids()) {
            if (raid.players().contains(playerId)) {
                return Optional.of(raid);
            }
        }
        return Optional.empty();
    }

    private RaidStateStore.RaidStateSnapshot buildSnapshot() {
        List<RaidStateStore.RaidSnapshot> raids = new ArrayList<>();
        for (RaidInstance raid : raidManager.activeRaids()) {
            if (raid.state() != RaidState.IN_RAID && raid.state() != RaidState.EXTRACTING) {
                continue;
            }
            raids.add(new RaidStateStore.RaidSnapshot(
                    raid.id(),
                    raid.definition().id(),
                    raid.state(),
                    raid.createdAt(),
                    raid.stateChangedAt(),
                    raidDeadlines.get(raid.id()),
                    raid.players()));
        }
        Set<String> activeRaidIds = new HashSet<>();
        for (RaidInstance raid : raidManager.activeRaids()) {
            activeRaidIds.add(raid.id());
        }
        List<EvacTracker.EvacSnapshot> evacSnapshots = new ArrayList<>();
        for (EvacTracker.EvacSnapshot evacSnapshot : extractionService.evacSnapshots()) {
            if (activeRaidIds.contains(evacSnapshot.raidId())) {
                evacSnapshots.add(evacSnapshot);
            }
        }
        return new RaidStateStore.RaidStateSnapshot(Instant.now(), raids, evacSnapshots);
    }

    private void handleEvacZoneCheck(RaidInstance raidInstance, UUID playerId) {
        if (extractionService.isExtracted(raidInstance.id(), playerId)) {
            return;
        }
        Optional<EvacZoneDefinition> zoneOptional = raidInstance.definition().evacZones().stream()
                .filter(zone -> regionProvider.isInEvacZone(playerId, zone))
                .findFirst();
        if (zoneOptional.isEmpty()) {
            if (activeEvacZones.remove(playerId) != null) {
                extractionService.cancelExtraction(raidInstance.id(), playerId);
                cancelEvacCountdown(playerId);
                logEvent(Level.INFO, "extraction_cancelled",
                        "raidId", raidInstance.id(),
                        "playerId", playerId,
                        "reason", "left_zone");
                Player player = plugin.getServer().getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    player.sendMessage("Extraction cancelled; you left the evac zone.");
                }
            }
            return;
        }

        EvacZoneDefinition evacZone = zoneOptional.get();
        ExtractionService.ExtractionResult result = extractionService.beginExtraction(
                raidInstance.id(),
                playerId,
                evacZone.name(),
                Duration.ofSeconds(evacDurationSeconds));
        if (result == ExtractionService.ExtractionResult.STARTED) {
            activeEvacZones.put(playerId, evacZone.name());
            raidInstance.markExtracting();
            logEvent(Level.INFO, "extraction_started",
                    "raidId", raidInstance.id(),
                    "playerId", playerId,
                    "zone", evacZone.name(),
                    "durationSeconds", evacDurationSeconds);
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage("Extraction started at " + evacZone.name() + ". Hold for " + evacDurationSeconds + "s.");
            }
            startEvacCountdownTask(raidInstance, playerId, evacZone);
        } else if (result == ExtractionService.ExtractionResult.ALREADY_TRACKING) {
            activeEvacZones.putIfAbsent(playerId, evacZone.name());
            startEvacCountdownTask(raidInstance, playerId, evacZone);
        }

        if (extractionService.completeIfReady(raidInstance.id(), playerId) == ExtractionService.ExtractionResult.EXTRACTED) {
            activeEvacZones.remove(playerId);
            cancelEvacCountdown(playerId);
            handleExtractionSuccess(raidInstance, playerId);
        }
    }

    private void handleExtractionSuccess(RaidInstance raidInstance, UUID playerId) {
        Player player = plugin.getServer().getPlayer(playerId);
        List<ItemData> carried = player != null ? snapshotInventory(player) : List.of();
        logEvent(Level.INFO, "extraction_success",
                "raidId", raidInstance.id(),
                "playerId", playerId,
                "stacks", carried.size());
        if (inventorySnapshotService.restore(playerId)) {
            inventorySnapshotService.clear(playerId);
        }

        String lobbyWorld = configManager.getMainConfig().getString("lobby_world", "world");
        teleportService.sendToLobby(playerId, lobbyWorld);
        raidInstance.removePlayer(playerId);
        extractionService.cancelExtraction(raidInstance.id(), playerId);
        cancelEvacCountdown(playerId);
        cleanupRaidIfEmpty(raidInstance, "Raid complete. All players have extracted or left.");

        if (carried.isEmpty()) {
            if (player != null && player.isOnline()) {
                player.sendMessage("Extraction complete! No items to stash.");
            }
            return;
        }

        persistStashAsync(raidInstance.id(), playerId, carried, carried.size());
    }

    private void handleFailure(RaidInstance raidInstance, UUID playerId, String message) {
        logEvent(Level.INFO, "raid_failure",
                "raidId", raidInstance.id(),
                "playerId", playerId,
                "reason", message);
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && player.isOnline()) {
            inventorySnapshotService.restore(playerId);
            teleportService.sendToLobby(playerId, configManager.getMainConfig().getString("lobby_world", "world"));
            if (message != null && !message.isEmpty()) {
                player.sendMessage(message);
            }
        }
        inventorySnapshotService.clear(playerId);
        raidInstance.removePlayer(playerId);
        extractionService.cancelExtraction(raidInstance.id(), playerId);
        activeEvacZones.remove(playerId);
        cancelEvacCountdown(playerId);
        cleanupRaidIfEmpty(raidInstance, "Raid ended because all players were eliminated or left.");
    }

    private void cleanupRaidIfEmpty(RaidInstance raidInstance, String message) {
        if (raidInstance.players().isEmpty()) {
            endRaid(raidInstance.id(), message, false, false);
        }
    }

    private List<ItemData> snapshotInventory(Player player) {
        List<ItemData> items = new ArrayList<>();
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                continue;
            }
            items.add(itemDataMapper.toItemData(stack));
        }
        player.getInventory().clear();
        player.updateInventory();
        return items;
    }

    private void persistStashAsync(String raidId, UUID playerId, List<ItemData> items, int deliveredStacks) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean success = false;
            try {
                List<ItemData> stashItems = new ArrayList<>(stashService.load(playerId));
                stashItems.addAll(items);
                stashService.replace(playerId, stashItems);
                success = true;
            } catch (Exception error) {
                logger.log(Level.WARNING, formatEvent("stash_save_failed",
                        "raidId", raidId,
                        "playerId", playerId,
                        "reason", error.getMessage()), error);
            }
            boolean finalSuccess = success;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Player player = plugin.getServer().getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    if (finalSuccess) {
                        player.sendMessage("Extraction complete! " + deliveredStacks + " item stack(s) sent to your stash.");
                    } else {
                        player.sendMessage("Extraction finished, but saving to your stash failed. Contact an admin.");
                    }
                }
            });
        });
    }

    private void startEvacCountdownTask(RaidInstance raidInstance, UUID playerId, EvacZoneDefinition evacZoneDefinition) {
        cancelEvacCountdown(playerId);
        AtomicInteger lastAnnounced = new AtomicInteger(Integer.MAX_VALUE);
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                cancelEvacCountdown(playerId);
                handleFailure(raidInstance, playerId, null);
                return;
            }
            if (!regionProvider.isInEvacZone(playerId, evacZoneDefinition)) {
                cancelEvacCountdown(playerId);
                extractionService.cancelExtraction(raidInstance.id(), playerId);
                activeEvacZones.remove(playerId);
                logEvent(Level.INFO, "extraction_cancelled",
                        "raidId", raidInstance.id(),
                        "playerId", playerId,
                        "reason", "left_zone");
                player.sendMessage("Extraction cancelled; you left the evac zone.");
                return;
            }

            ExtractionService.ExtractionResult result = extractionService.completeIfReady(raidInstance.id(), playerId);
            if (result == ExtractionService.ExtractionResult.EXTRACTED) {
                cancelEvacCountdown(playerId);
                activeEvacZones.remove(playerId);
                handleExtractionSuccess(raidInstance, playerId);
                return;
            }

            long millisRemaining = extractionService.remaining(raidInstance.id(), playerId).toMillis();
            int secondsRemaining = (int) Math.ceil(millisRemaining / 1000.0);
            if (secondsRemaining > 0 && secondsRemaining <= 5 && secondsRemaining != lastAnnounced.get()) {
                player.sendMessage("Extraction in " + secondsRemaining + "...");
                lastAnnounced.set(secondsRemaining);
            }
        }, 0L, 20L);
        evacCountdowns.put(playerId, task);
    }

    private void cancelEvacCountdown(UUID playerId) {
        BukkitTask task = evacCountdowns.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    private List<ItemStack> toItemStacks(ItemData itemData) {
        ItemStack baseStack = itemDataMapper.toItemStack(itemData);
        if (baseStack.getType().isAir()) {
            return List.of();
        }
        int remaining = Math.max(itemData.amount(), 1);
        int maxStack = Math.max(baseStack.getMaxStackSize(), 1);
        List<ItemStack> stacks = new ArrayList<>();
        while (remaining > 0) {
            ItemStack next = baseStack.clone();
            int amount = Math.min(maxStack, remaining);
            next.setAmount(amount);
            stacks.add(next);
            remaining -= amount;
        }
        return stacks;
    }

    private void broadcastToRaid(RaidInstance raidInstance, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        for (UUID playerId : raidInstance.players()) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }

    private void logEvent(Level level, String event, Object... fields) {
        logger.log(level, formatEvent(event, fields));
    }

    private String formatEvent(String event, Object... fields) {
        StringBuilder message = new StringBuilder("event=").append(event);
        if (fields != null) {
            for (int i = 0; i + 1 < fields.length; i += 2) {
                message.append(' ')
                        .append(String.valueOf(fields[i]))
                        .append('=')
                        .append(String.valueOf(fields[i + 1]));
            }
        }
        return message.toString();
    }

    private boolean endRaid(String raidId, String message, boolean restoreInventory, boolean sendToLobby) {
        Optional<RaidInstance> raidOptional = raidManager.getRaid(raidId);
        if (raidOptional.isEmpty()) {
            return false;
        }
        RaidInstance raidInstance = raidOptional.get();
        raidInstance.markEnded();
        logEvent(Level.INFO, "raid_ended", "raidId", raidId, "reason", message);
        cancelRaidTimeout(raidId);

        String lobbyWorld = configManager.getMainConfig().getString("lobby_world", "world");
        for (UUID playerId : raidInstance.players()) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null && player.isOnline()) {
                activeEvacZones.remove(playerId);
                cancelEvacCountdown(playerId);
                extractionService.cancelExtraction(raidId, playerId);
                if (restoreInventory) {
                    inventorySnapshotService.restore(playerId);
                }
                if (sendToLobby) {
                    teleportService.sendToLobby(playerId, lobbyWorld);
                }
                if (message != null && !message.isEmpty()) {
                    player.sendMessage(message);
                }
            }
            inventorySnapshotService.clear(playerId);
        }
        extractionService.clearRaid(raidId);
        raidManager.endRaid(raidId);
        return true;
    }
}
