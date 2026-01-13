package com.raidextraction.integration;

import com.raidextraction.config.ConfigManager;
import com.raidextraction.config.model.EvacZoneDefinition;
import com.raidextraction.config.model.RaidDefinition;
import com.raidextraction.editor.EvacZoneEntry;
import com.raidextraction.editor.LootContainerEntry;
import com.raidextraction.editor.MapEditorStorage;
import com.raidextraction.extraction.EvacTracker;
import com.raidextraction.extraction.ExtractionService;
import com.raidextraction.loot.LootService;
import com.raidextraction.raid.QueueManager;
import com.raidextraction.raid.RaidInstance;
import com.raidextraction.raid.RaidManager;
import com.raidextraction.raid.RaidState;
import com.raidextraction.stash.ItemData;
import com.raidextraction.stash.StashService;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

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
 * Coordinates raid lifecycle wiring that depends on Paper API: teleport,
 * inventory handling, and cleanup.
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
    private final MapEditorStorage mapEditorStorage;
    private final Logger logger;
    private final RaidStateStore stateStore;
    private final Map<String, BukkitTask> raidTimeouts = new HashMap<>();
    private final Map<String, Instant> raidDeadlines = new HashMap<>();
    private final Map<String, BossBar> raidBossBars = new HashMap<>();
    private final Map<UUID, Instant> outOfBoundsTracker = new HashMap<>();
    private final Set<UUID> boundsDebuggers = new HashSet<>();
    private final Map<String, BukkitTask> raidBossBarTasks = new HashMap<>();
    private final Map<String, Set<UUID>> extractionSuccessNotified = new HashMap<>();
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
            ItemDataMapper itemDataMapper,
            MapEditorStorage mapEditorStorage) {
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
        this.mapEditorStorage = Objects.requireNonNull(mapEditorStorage, "mapEditorStorage");
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
        return endRaid(raidId, message, true, true, false);
    }

    public void rehydrateState() {
        stateStore.load().ifPresent(snapshot -> {
            int restoredRaids = 0;
            int restoredEvacs = 0;
            logEvent(Level.INFO, "raid_rehydrate_start", "raids", snapshot.raids().size(), "evacs",
                    snapshot.evacSnapshots().size());
            for (RaidStateStore.RaidSnapshot raidSnapshot : snapshot.raids()) {
                if (raidSnapshot.state() != RaidState.IN_RAID && raidSnapshot.state() != RaidState.EXTRACTING) {
                    continue;
                }
                RaidDefinition definition = raidManager.definitions().get(raidSnapshot.definitionId());
                if (definition == null) {
                    logEvent(Level.WARNING, "raid_rehydrate_skip", "raidId", raidSnapshot.raidId(), "reason",
                            "missing_definition");
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
                    logEvent(Level.WARNING, "raid_rehydrate_skip", "raidId", raidSnapshot.raidId(), "reason",
                            "duplicate_or_invalid");
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

            for (RaidInstance raid : raidManager.activeRaids()) {
                startRaidBossBar(raid);
            }

            for (EvacTracker.EvacSnapshot evacSnapshot : evacSnapshots) {
                Optional<RaidInstance> raidOptional = raidManager.getRaid(evacSnapshot.raidId());
                if (raidOptional.isEmpty()) {
                    continue;
                }
                RaidInstance raidInstance = raidOptional.get();
                if (!raidInstance.players().contains(evacSnapshot.playerId())) {
                    continue;
                }
                activeEvacZones.put(evacSnapshot.playerId(), evacSnapshot.zoneName());
                Optional<EvacZoneDefinition> zoneOptional = raidInstance.definition().evacZones().stream()
                        .filter(zone -> zone.name().equals(evacSnapshot.zoneName()))
                        .findFirst();
                if (zoneOptional.isPresent()) {
                    startEvacCountdownTask(raidInstance, evacSnapshot.playerId(), zoneOptional.get());
                    restoredEvacs++;
                    continue;
                }
                Optional<EvacZoneEntry> editorZone = mapEditorStorage.getEvacZones(raidInstance.definition().id())
                        .stream()
                        .filter(zone -> zone.name().equalsIgnoreCase(evacSnapshot.zoneName()))
                        .findFirst();
                if (editorZone.isPresent()) {
                    startEditorEvacCountdownTask(raidInstance, evacSnapshot.playerId(), editorZone.get());
                    restoredEvacs++;
                    continue;
                }
                logEvent(Level.WARNING, "evac_rehydrate_skip", "raidId", evacSnapshot.raidId(), "playerId",
                        evacSnapshot.playerId());
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
        // Stop any active countdown for this player
        outOfBoundsTracker.remove(playerId);
        if (evacCountdowns.containsKey(playerId)) {
            cancelEvacCountdown(playerId);
        }
        queueManager.remove(playerId);
        for (RaidInstance raid : raidManager.activeRaids()) {
            if (!raid.players().contains(playerId)) {
                continue;
            }
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null && player.isOnline()) {
                sendFailureFeedback(player, "You abandoned the raid.");
            }
            logEvent(Level.INFO, "raid_failure",
                    "raidId", raid.id(),
                    "playerId", playerId,
                    "reason", "disconnect");
            raid.removePlayer(playerId);
            removeBossBarPlayer(raid.id(), playerId);
            activeEvacZones.remove(playerId);
            cancelEvacCountdown(playerId);
            extractionService.cancelExtraction(raid.id(), playerId);
            inventorySnapshotService.restore(playerId);
            inventorySnapshotService.clear(playerId);
            if (raid.players().isEmpty()) {
                endRaid(raid.id(), "Raid ended because all players left.", false, false, false);
            }
            break;
        }
    }

    public void handlePlayerDeath(UUID playerId) {
        findRaidByPlayer(playerId).ifPresent(raid -> handleFailure(raid, playerId,
                "You died in the raid. Returning to lobby with your pre-raid loadout."));
    }

    public void handlePlayerMove(UUID playerId) {
        // Handle Debug Mode (works without active raid)
        if (boundsDebuggers.contains(playerId)) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                String worldName = player.getWorld().getName();
                Optional<RaidDefinition> def = raidManager.definitions().values().stream()
                        .filter(d -> d.world().equalsIgnoreCase(worldName))
                        .findFirst();

                if (def.isPresent() && def.get().bounds() != null) {
                    boolean inBounds = regionProvider.isInRaidBounds(playerId, def.get().bounds());
                    if (inBounds) {
                        player.sendActionBar(Component.text("configured-bounds: In Bounds", NamedTextColor.GREEN));
                    } else {
                        player.sendActionBar(
                                Component.text("configured-bounds: ⚠ OUT OF BOUNDS ⚠", NamedTextColor.RED));
                    }
                } else {
                    player.sendActionBar(
                            Component.text("No raid bounds configured for this world", NamedTextColor.GRAY));
                }
            }
        }

        Optional<RaidInstance> raidOpt = raidManager.getRaidForPlayer(playerId);
        if (raidOpt.isEmpty()) {
            return;
        }
        RaidInstance raid = raidOpt.get();
        if (raid.state() != RaidState.IN_RAID && raid.state() != RaidState.EXTRACTING) {
            return;
        }
        if (!regionProvider.isInRaidWorld(playerId, raid.definition())) {
            handleFailure(raid, playerId, "You left the raid area. Returning to lobby.");
            return;
        }

        // Soft bounds check (V2.0 feature)
        handleBoundsCheck(raid, playerId);

        if (!raid.definition().evacZones().isEmpty()) {
            handleEvacZoneCheck(raid, playerId);
        }
    }

    private void handleBoundsCheck(RaidInstance raid, UUID playerId) {
        // Bounds checking
        boolean inBounds = regionProvider.isInRaidBounds(playerId, raid.definition().bounds());

        if (!inBounds) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null)
                return;

            Instant firstOut = outOfBoundsTracker.computeIfAbsent(playerId, k -> Instant.now());
            long secondsOut = Duration.between(firstOut, Instant.now()).getSeconds();

            if (secondsOut >= 5) {
                player.sendActionBar(Component.text("⚠ TURN BACK! You are taking damage! ⚠", NamedTextColor.RED));
                player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 40, 1));
            } else {
                long remaining = 5 - secondsOut;
                player.sendActionBar(Component.text("⚠ LEAVING RAID AREA! Turn back in " + remaining + "s! ⚠",
                        NamedTextColor.YELLOW));
            }
        } else {
            // Player returned to bounds
            outOfBoundsTracker.remove(playerId);
        }
    }

    public boolean toggleBoundsDebug(UUID playerId) {
        if (boundsDebuggers.contains(playerId)) {
            boundsDebuggers.remove(playerId);
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null)
                player.sendActionBar(Component.empty());
            return false;
        } else {
            boundsDebuggers.add(playerId);
            return true;
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
            playRaidStartSound(player);
        }

        if (deployed < definition.minPlayers()) {
            logEvent(Level.WARNING, "raid_deploy_failed",
                    "raidId", raidInstance.id(),
                    "definitionId", definition.id(),
                    "deployed", deployed,
                    "minPlayers", definition.minPlayers());
            endRaid(raidInstance.id(), "Raid canceled because not enough players could deploy.", true, true, false);
            return;
        }

        raidInstance.markInRaid();
        logEvent(Level.INFO, "raid_started",
                "raidId", raidInstance.id(),
                "definitionId", definition.id(),
                "players", raidInstance.players().size());
        spawnRaidLoot(raidInstance);
        scheduleRaidTimeout(raidInstance);
        startRaidBossBar(raidInstance);
    }

    private void spawnRaidLoot(RaidInstance raidInstance) {
        logger.info("[Debug] spawnRaidLoot called for raid " + raidInstance.id());
        try {
            RaidDefinition definition = raidInstance.definition();
            World world = plugin.getServer().getWorld(definition.world());
            if (world == null) {
                logEvent(Level.WARNING, "loot_spawn_failed",
                        "raidId", raidInstance.id(),
                        "definitionId", definition.id(),
                        "world", definition.world());
                return;
            }
            int rolls = Math.max(definition.targetLootCount(), 1);
            List<ItemData> loot = lootService.roll(definition.lootTableId(), rolls);
            if (loot.isEmpty()) {
                logEvent(Level.WARNING, "loot_roll_empty",
                        "raidId", raidInstance.id(),
                        "definitionId", definition.id(),
                        "tableId", definition.lootTableId());
                return;
            }

            List<LootContainerEntry> containers = mapEditorStorage.getLootContainers(definition.id());
            logger.info("[Debug] Found " + containers.size() + " configured containers for raid " + definition.id());

            if (!containers.isEmpty()) {
                boolean filled = fillLootContainers(raidInstance, world, containers, loot);
                logger.info("[Debug] fillLootContainers result: " + filled + ". Pending loot map size: "
                        + raidInstance.getPendingLootMapSize());
                if (filled) {
                    broadcastToRaid(raidInstance, "Loot caches have been stocked. Search the raid for containers.");
                    return;
                }
            } else {
                logger.info("[Debug] No containers found for " + definition.id());
            }

            var dropLocation = world.getSpawnLocation().add(0.5, 1.0, 0.5);
            for (ItemData itemData : loot) {
                for (ItemStack stack : toItemStacks(itemData)) {
                    world.dropItemNaturally(dropLocation, stack);
                }
            }
            broadcastToRaid(raidInstance, "Supply drop inbound! Loot has spawned near the raid start.");
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Failed to spawn raid loot", t);
        }
    }

    private boolean fillLootContainers(RaidInstance raidInstance, World world, List<LootContainerEntry> containers,
            List<ItemData> loot) {
        List<LootContainerEntry> eligible = containers.stream()
                .filter(entry -> world.getName().equalsIgnoreCase(entry.world()))
                .toList();

        logger.info("[Debug] Eligible containers in world " + world.getName() + ": " + eligible.size());

        if (eligible.isEmpty()) {
            return false;
        }
        clearLootChests(world, eligible);
        List<LootContainerEntry> selected = new ArrayList<>();
        for (LootContainerEntry entry : eligible) {
            double chance = Math.max(0.0, Math.min(1.0, entry.chance()));
            if (chance >= 1.0 || Math.random() <= chance) {
                selected.add(entry);
            }
        }

        logger.info("[Debug] Selected " + selected.size() + " containers after chance roll.");

        if (selected.isEmpty()) {
            return false;
        }

        List<ItemStack> stacks = new ArrayList<>();
        for (ItemData itemData : loot) {
            stacks.addAll(toItemStacks(itemData));
        }
        if (stacks.isEmpty()) {
            logger.info("[Debug] No item stacks generated from item data.");
            return false;
        }

        Map<LootContainerEntry, Block> containerByEntry = new HashMap<>();
        List<LootContainerEntry> readyEntries = new ArrayList<>();
        for (LootContainerEntry entry : selected) {
            Block containerBlock = resolveLootContainerBlock(world, entry);
            if (containerBlock == null) {
                continue;
            }
            if (!(containerBlock.getState() instanceof org.bukkit.block.Container)) {
                continue;
            }
            containerByEntry.put(entry, containerBlock);
            readyEntries.add(entry);
        }

        logger.info("[Debug] Ready entries (valid containers): " + readyEntries.size());

        if (readyEntries.isEmpty()) {
            return false;
        }

        // Map loot to containers (distribute stacks cyclically)
        int containerIndex = 0;
        boolean placedAny = false;
        // Temporary map to aggregate loot per container
        Map<LootContainerEntry, List<ItemData>> distribution = new HashMap<>();

        for (ItemStack stack : stacks) {
            LootContainerEntry entry = readyEntries.get(containerIndex % readyEntries.size());
            containerIndex++;
            if (!containerByEntry.containsKey(entry)) {
                continue;
            }

            // Convert ItemStack back to ItemData for storage (or keep as ItemData
            // initially)
            // Ideally we distribute ItemData, but the loop used stacks.
            // We can just create new ItemData from the stack.
            ItemData data = itemDataMapper.toItemData(stack);

            distribution.computeIfAbsent(entry, k -> new ArrayList<>()).add(data);
            placedAny = true;
        }

        logger.info("[Debug] Distributing loot to " + distribution.size() + " containers.");

        // Commit distribution to RaidInstance
        for (Map.Entry<LootContainerEntry, List<ItemData>> entry : distribution.entrySet()) {
            raidInstance.addPendingLoot(entry.getKey().x(), entry.getKey().y(), entry.getKey().z(), entry.getValue());
            logger.info("[Debug] Added pending loot at " + entry.getKey().x() + "," + entry.getKey().y() + ","
                    + entry.getKey().z() + " size=" + entry.getValue().size());
        }

        return placedAny;
    }

    private void clearLootChests(World world, List<LootContainerEntry> entries) {
        for (LootContainerEntry entry : entries) {
            Block block = getLootContainerBlock(world, entry);
            if (block == null) {
                continue;
            }
            if (block.getType() == org.bukkit.Material.CHEST
                    || block.getType() == org.bukkit.Material.TRAPPED_CHEST
                    || block.getType() == org.bukkit.Material.BARREL) {
                block.setType(org.bukkit.Material.AIR, false);
            }
        }
    }

    private Block resolveLootContainerBlock(World world, LootContainerEntry entry) {
        if (world == null || entry == null) {
            return null;
        }
        Block containerBlock = getLootContainerBlock(world, entry);
        if (containerBlock == null) {
            return null;
        }
        BlockFace facing = parseFacing(entry.facing());
        if (containerBlock.getType().isAir()) {
            containerBlock.setType(org.bukkit.Material.CHEST, false);
            if (containerBlock.getBlockData() instanceof Directional directional) {
                directional.setFacing(facing);
                containerBlock.setBlockData(directional, false);
            }
        }
        if (!containerBlock.getType().isAir()) {
            return containerBlock;
        }
        return null;
    }

    private Block getLootContainerBlock(World world, LootContainerEntry entry) {
        if (world == null || entry == null) {
            return null;
        }
        return world.getBlockAt(entry.x(), entry.y(), entry.z());
    }

    private BlockFace parseFacing(String facing) {
        if (facing == null || facing.isBlank()) {
            return BlockFace.NORTH;
        }
        try {
            return BlockFace.valueOf(facing.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return BlockFace.NORTH;
        }
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
            endRaid(raidInstance.id(), "Raid ended: time expired.", true, true, true);
            return;
        }
        long ticks = remainingSeconds * 20L;
        BukkitTask task = plugin.getServer().getScheduler()
                .runTaskLater(plugin, () -> endRaid(raidInstance.id(), "Raid ended: time expired.", true, true, true),
                        ticks);
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
        List<EvacZoneEntry> editorZones = mapEditorStorage.getEvacZones(raidInstance.definition().id());
        if (!editorZones.isEmpty()) {
            Optional<EvacZoneEntry> zoneOptional = editorZones.stream()
                    .filter(zone -> isInEditorEvacZone(playerId, zone))
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
                        clearActionBar(player);
                    }
                }
                return;
            }

            EvacZoneEntry evacZone = zoneOptional.get();
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
                    player.sendMessage(
                            "Extraction started at " + evacZone.name() + ". Hold for " + evacDurationSeconds + "s.");
                }
                startEditorEvacCountdownTask(raidInstance, playerId, evacZone);
            } else if (result == ExtractionService.ExtractionResult.COOLDOWN) {
                logEvent(Level.INFO, "extraction_throttled",
                        "raidId", raidInstance.id(),
                        "playerId", playerId,
                        "reason", "cooldown");
            } else if (result == ExtractionService.ExtractionResult.ALREADY_TRACKING) {
                activeEvacZones.putIfAbsent(playerId, evacZone.name());
                startEditorEvacCountdownTask(raidInstance, playerId, evacZone);
            }

            if (extractionService.completeIfReady(raidInstance.id(),
                    playerId) == ExtractionService.ExtractionResult.EXTRACTED) {
                activeEvacZones.remove(playerId);
                cancelEvacCountdown(playerId);
                handleExtractionSuccess(raidInstance, playerId);
            }
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
                    clearActionBar(player);
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
                player.sendMessage(
                        "Extraction started at " + evacZone.name() + ". Hold for " + evacDurationSeconds + "s.");
            }
            startEvacCountdownTask(raidInstance, playerId, evacZone);
        } else if (result == ExtractionService.ExtractionResult.COOLDOWN) {
            logEvent(Level.INFO, "extraction_throttled",
                    "raidId", raidInstance.id(),
                    "playerId", playerId,
                    "reason", "cooldown");
        } else if (result == ExtractionService.ExtractionResult.ALREADY_TRACKING) {
            activeEvacZones.putIfAbsent(playerId, evacZone.name());
            startEvacCountdownTask(raidInstance, playerId, evacZone);
        }

        if (extractionService.completeIfReady(raidInstance.id(),
                playerId) == ExtractionService.ExtractionResult.EXTRACTED) {
            activeEvacZones.remove(playerId);
            cancelEvacCountdown(playerId);
            handleExtractionSuccess(raidInstance, playerId);
        }
    }

    private void handleExtractionSuccess(RaidInstance raidInstance, UUID playerId) {
        if (!extractionService.markExtractionHandled(raidInstance.id(), playerId)) {
            logEvent(Level.WARNING, "extraction_duplicate",
                    "raidId", raidInstance.id(),
                    "playerId", playerId,
                    "reason", "already_handled");
            return;
        }
        Player player = plugin.getServer().getPlayer(playerId);
        List<ItemData> carried = player != null ? snapshotInventory(player) : List.of();
        logEvent(Level.INFO, "extraction_success",
                "raidId", raidInstance.id(),
                "playerId", playerId,
                "stacks", carried.size());
        if (player != null && player.isOnline()) {
            if (markExtractionSuccessNotified(raidInstance.id(), playerId)) {
                sendSuccessFeedback(player);
            }
            clearActionBar(player);
        }
        if (inventorySnapshotService.restore(playerId)) {
            inventorySnapshotService.clear(playerId);
        }

        teleportService.sendToLobby(playerId, configManager.getLobbySpawnConfig());
        raidInstance.removePlayer(playerId);
        removeBossBarPlayer(raidInstance.id(), playerId);
        extractionService.cancelExtraction(raidInstance.id(), playerId);
        cancelEvacCountdown(playerId);
        outOfBoundsTracker.remove(playerId);
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
            sendFailureFeedback(player, "Extraction failed.");
            clearActionBar(player);
            inventorySnapshotService.restore(playerId);
            teleportService.sendToLobby(playerId, configManager.getLobbySpawnConfig());
            if (message != null && !message.isEmpty()) {
                player.sendMessage(message);
            }
        }
        inventorySnapshotService.clear(playerId);
        raidInstance.removePlayer(playerId);
        removeBossBarPlayer(raidInstance.id(), playerId);
        extractionService.cancelExtraction(raidInstance.id(), playerId);
        activeEvacZones.remove(playerId);
        cancelEvacCountdown(playerId);
        cleanupRaidIfEmpty(raidInstance, "Raid ended because all players were eliminated or left.");
    }

    private void cleanupRaidIfEmpty(RaidInstance raidInstance, String message) {
        if (raidInstance.players().isEmpty()) {
            endRaid(raidInstance.id(), message, false, false, false);
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
                        player.sendMessage(
                                "Extraction complete! " + deliveredStacks + " item stack(s) sent to your stash.");
                    } else {
                        player.sendMessage("Extraction finished, but saving to your stash failed. Contact an admin.");
                    }
                }
            });
        });
    }

    private void startEvacCountdownTask(RaidInstance raidInstance, UUID playerId,
            EvacZoneDefinition evacZoneDefinition) {
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
                clearActionBar(player);
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
            if (secondsRemaining > 0) {
                player.sendActionBar("Extracting: " + secondsRemaining + "s");
                playExtractionTickSound(player);
            } else {
                player.sendActionBar("Extracting...");
            }
            if (secondsRemaining > 0 && secondsRemaining <= 5 && secondsRemaining != lastAnnounced.get()) {
                player.sendMessage("Extraction in " + secondsRemaining + "...");
                lastAnnounced.set(secondsRemaining);
            }
        }, 0L, 20L);
        evacCountdowns.put(playerId, task);
    }

    private void startEditorEvacCountdownTask(RaidInstance raidInstance, UUID playerId, EvacZoneEntry evacZone) {
        cancelEvacCountdown(playerId);
        AtomicInteger lastAnnounced = new AtomicInteger(Integer.MAX_VALUE);
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                cancelEvacCountdown(playerId);
                handleFailure(raidInstance, playerId, null);
                return;
            }
            if (!isInEditorEvacZone(playerId, evacZone)) {
                cancelEvacCountdown(playerId);
                extractionService.cancelExtraction(raidInstance.id(), playerId);
                activeEvacZones.remove(playerId);
                logEvent(Level.INFO, "extraction_cancelled",
                        "raidId", raidInstance.id(),
                        "playerId", playerId,
                        "reason", "left_zone");
                player.sendMessage("Extraction cancelled; you left the evac zone.");
                clearActionBar(player);
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
            if (secondsRemaining > 0) {
                player.sendActionBar("Extracting: " + secondsRemaining + "s");
                playExtractionTickSound(player);
            } else {
                player.sendActionBar("Extracting...");
            }
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

    private boolean isInEditorEvacZone(UUID playerId, EvacZoneEntry evacZone) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (!player.getWorld().getName().equalsIgnoreCase(evacZone.world())) {
            return false;
        }
        int x = player.getLocation().getBlockX();
        int y = player.getLocation().getBlockY();
        int z = player.getLocation().getBlockZ();
        return x >= evacZone.minX() && x <= evacZone.maxX()
                && y >= evacZone.minY() && y <= evacZone.maxY()
                && z >= evacZone.minZ() && z <= evacZone.maxZ();
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

    public void logCommandCooldown(UUID playerId, String command, Duration remaining) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(command, "command");
        Duration remainingSafe = remaining == null ? Duration.ZERO : remaining;
        logEvent(Level.INFO, "command_throttled",
                "playerId", playerId,
                "command", command,
                "remainingMs", remainingSafe.toMillis());
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

    private boolean endRaid(String raidId,
            String message,
            boolean restoreInventory,
            boolean sendToLobby,
            boolean showFailureFeedback) {
        Optional<RaidInstance> raidOptional = raidManager.getRaid(raidId);
        if (raidOptional.isEmpty()) {
            return false;
        }
        RaidInstance raidInstance = raidOptional.get();
        raidInstance.markEnded();
        logEvent(Level.INFO, "raid_ended", "raidId", raidId, "reason", message);
        cancelRaidTimeout(raidId);
        stopRaidBossBar(raidId);

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
                    teleportService.sendToLobby(playerId, configManager.getLobbySpawnConfig());
                }
                if (showFailureFeedback) {
                    sendFailureFeedback(player, "Raid timed out.");
                }
                clearActionBar(player);
                removeBossBarPlayer(raidId, playerId);
                if (message != null && !message.isEmpty()) {
                    player.sendMessage(message);
                }
            }
            inventorySnapshotService.clear(playerId);
        }
        extractionService.clearRaid(raidId);
        extractionSuccessNotified.remove(raidId);
        raidManager.endRaid(raidId);
        return true;
    }

    private boolean markExtractionSuccessNotified(String raidId, UUID playerId) {
        Set<UUID> notified = extractionSuccessNotified.computeIfAbsent(raidId, key -> new HashSet<>());
        return notified.add(playerId);
    }

    private void startRaidBossBar(RaidInstance raidInstance) {
        if (raidInstance.state() != RaidState.IN_RAID && raidInstance.state() != RaidState.EXTRACTING) {
            return;
        }
        BossBar bossBar = raidBossBars.computeIfAbsent(raidInstance.id(),
                key -> plugin.getServer().createBossBar("", BarColor.RED, BarStyle.SOLID));
        bossBar.setVisible(true);
        updateBossBarPlayers(raidInstance, bossBar);
        if (raidBossBarTasks.containsKey(raidInstance.id())) {
            updateRaidBossBar(raidInstance, bossBar);
            return;
        }
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            BossBar activeBar = raidBossBars.get(raidInstance.id());
            if (activeBar == null) {
                return;
            }
            updateRaidBossBar(raidInstance, activeBar);
            updateBossBarPlayers(raidInstance, activeBar);
        }, 0L, 20L);
        raidBossBarTasks.put(raidInstance.id(), task);
    }

    private void updateRaidBossBar(RaidInstance raidInstance, BossBar bossBar) {
        Instant deadline = raidDeadlines.get(raidInstance.id());
        if (deadline == null) {
            Instant endsAt = raidInstance.raidEndsAt();
            if (endsAt != null) {
                deadline = endsAt;
                raidDeadlines.put(raidInstance.id(), deadline);
            }
        }
        long totalSeconds = Math.max(raidInstance.definition().durationSeconds(), 1);
        long remainingSeconds = deadline != null ? Math.max(0, Duration.between(Instant.now(), deadline).getSeconds())
                : 0;
        double progress = Math.max(0.0, Math.min(1.0, remainingSeconds / (double) totalSeconds));
        bossBar.setProgress(progress);
        bossBar.setTitle("Raid time: " + formatTimer(remainingSeconds));
    }

    private void updateBossBarPlayers(RaidInstance raidInstance, BossBar bossBar) {
        Set<UUID> raidPlayers = raidInstance.players();
        for (Player player : new ArrayList<>(bossBar.getPlayers())) {
            if (!raidPlayers.contains(player.getUniqueId())) {
                bossBar.removePlayer(player);
            }
        }
        for (UUID playerId : raidPlayers) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null && player.isOnline() && !bossBar.getPlayers().contains(player)) {
                bossBar.addPlayer(player);
            }
        }
    }

    private void removeBossBarPlayer(String raidId, UUID playerId) {
        BossBar bossBar = raidBossBars.get(raidId);
        if (bossBar == null) {
            return;
        }
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            bossBar.removePlayer(player);
        }
    }

    private void stopRaidBossBar(String raidId) {
        BossBar bossBar = raidBossBars.remove(raidId);
        if (bossBar != null) {
            bossBar.removeAll();
        }
        BukkitTask task = raidBossBarTasks.remove(raidId);
        if (task != null) {
            task.cancel();
        }
    }

    private String formatTimer(long remainingSeconds) {
        long minutes = remainingSeconds / 60;
        long seconds = remainingSeconds % 60;
        return minutes + ":" + String.format("%02d", seconds);
    }

    private void sendSuccessFeedback(Player player) {
        player.sendTitle("Extraction complete", "Loot secured", 10, 50, 10);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.1f);
    }

    private void sendFailureFeedback(Player player, String subtitle) {
        String finalSubtitle = subtitle == null ? "" : subtitle;
        player.sendTitle("Raid failed", finalSubtitle, 10, 50, 10);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }

    private void playRaidStartSound(Player player) {
        int[] offsets = { 0, 6, 12 };
        for (int offset : offsets) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.2f);
                }
            }, offset);
        }
    }

    private void playExtractionTickSound(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
    }

    private void clearActionBar(Player player) {
        player.sendActionBar("");
    }
}
