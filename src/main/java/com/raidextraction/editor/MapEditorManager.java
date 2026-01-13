package com.raidextraction.editor;

import com.raidextraction.integration.WorldManager;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MapEditorManager {
    private final WorldManager worldManager;
    private final MapEditorStorage storage;
    private final NamespacedKey lootMarkerKey;
    private final NamespacedKey spawnToolKey;
    private final NamespacedKey evacToolKey;
    private final Map<UUID, EditorSession> sessions = new HashMap<>();
    private final Map<UUID, Deque<LootContainerEntry>> undoStacks = new HashMap<>();
    private final Map<UUID, BlockSelection> evacSelections = new HashMap<>();
    private final Map<UUID, Double> lootMarkerChances = new HashMap<>();
    private final Set<UUID> lootPreviewEnabled = new HashSet<>();
    private final Map<UUID, Set<BlockKey>> lootPreviewPlaced = new HashMap<>();

    public MapEditorManager(JavaPlugin plugin, WorldManager worldManager, MapEditorStorage storage) {
        this.worldManager = worldManager;
        this.storage = storage;
        this.lootMarkerKey = new NamespacedKey(plugin, "loot_marker_tool");
        this.spawnToolKey = new NamespacedKey(plugin, "spawn_tool");
        this.evacToolKey = new NamespacedKey(plugin, "evac_tool");
    }

    public void enterSession(Player player, String raidId, String worldName) {
        sessions.put(player.getUniqueId(), new EditorSession(raidId, worldName));
        lootMarkerChances.putIfAbsent(player.getUniqueId(), 1.0);
        ensureEditorTools(player);
        sendToolActionBar(player, player.getInventory().getItemInMainHand());
    }

    public void exitSession(Player player) {
        EditorSession session = sessions.remove(player.getUniqueId());
        if (session != null && lootPreviewEnabled.remove(player.getUniqueId())) {
            disableLootPreview(player.getUniqueId(), session.raidId(), session.worldName());
        }
        undoStacks.remove(player.getUniqueId());
        evacSelections.remove(player.getUniqueId());
        lootMarkerChances.remove(player.getUniqueId());
        lootPreviewPlaced.remove(player.getUniqueId());
        clearToolActionBar(player);
    }

    public boolean isEditing(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public String getEditingRaidId(Player player) {
        EditorSession session = sessions.get(player.getUniqueId());
        return session != null ? session.raidId() : null;
    }

    public LootContainerEntry markLootContainer(Player player, Block block, BlockFace face) {
        EditorSession session = ensureSession(player);
        if (session == null) {
            return null;
        }
        if (!worldManager.isEditorWorld(player.getWorld().getName())) {
            return null;
        }
        double chance = lootMarkerChances.getOrDefault(player.getUniqueId(), 1.0);
        BlockFace facing = face != null && face != BlockFace.SELF ? face : resolveFacing(block);
        LootContainerEntry entry = storage.addLootContainer(
                session.raidId(),
                block.getLocation(),
                facing,
                chance);
        undoStacks.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>()).push(entry);
        return entry;
    }

    public double setDefaultLootChance(Player player, double chance) {
        double clamped = Math.max(0.0, Math.min(1.0, chance));
        lootMarkerChances.put(player.getUniqueId(), clamped);
        return clamped;
    }

    public double getDefaultLootChance(Player player) {
        return lootMarkerChances.getOrDefault(player.getUniqueId(), 1.0);
    }

    public LootContainerEntry updateLootContainerChance(Player player, Block block, double chance) {
        EditorSession session = ensureSession(player);
        if (session == null) {
            return null;
        }
        if (!worldManager.isEditorWorld(player.getWorld().getName())) {
            return null;
        }
        double clamped = Math.max(0.0, Math.min(1.0, chance));
        LootContainerEntry updated = storage.updateLootContainerChance(session.raidId(), block.getLocation(), clamped);
        if (updated != null) {
            return updated;
        }
        if (block.getBlockData() instanceof Directional directional) {
            Block behind = block.getRelative(directional.getFacing().getOppositeFace());
            return storage.updateLootContainerChance(session.raidId(), behind.getLocation(), clamped);
        }
        return null;
    }

    public LootPreviewOutcome toggleLootPreview(Player player) {
        EditorSession session = ensureSession(player);
        if (session == null) {
            return new LootPreviewOutcome(LootPreviewStatus.NOT_EDITING, 0);
        }
        if (!worldManager.isEditorWorld(player.getWorld().getName())) {
            return new LootPreviewOutcome(LootPreviewStatus.NOT_IN_EDITOR_WORLD, 0);
        }
        if (lootPreviewEnabled.contains(player.getUniqueId())) {
            lootPreviewEnabled.remove(player.getUniqueId());
            int cleared = disableLootPreview(player.getUniqueId(), session.raidId(), player.getWorld().getName());
            return new LootPreviewOutcome(LootPreviewStatus.DISABLED, cleared);
        }
        lootPreviewEnabled.add(player.getUniqueId());
        int placed = enableLootPreview(player.getUniqueId(), session.raidId(), player.getWorld().getName());
        return new LootPreviewOutcome(LootPreviewStatus.ENABLED, placed);
    }

    public boolean undoLast(Player player) {
        EditorSession session = ensureSession(player);
        if (session == null) {
            return false;
        }
        Deque<LootContainerEntry> stack = undoStacks.get(player.getUniqueId());
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        LootContainerEntry entry = stack.pop();
        if (!entry.raidId().equals(session.raidId())) {
            stack.push(entry);
            return false;
        }
        boolean removed = storage.removeLootContainer(entry);
        if (!removed) {
            stack.push(entry);
        }
        return removed;
    }

    public SpawnPointEntry setSpawnPoint(Player player) {
        EditorSession session = ensureSession(player);
        if (session == null) {
            return null;
        }
        if (!worldManager.isEditorWorld(player.getWorld().getName())) {
            return null;
        }
        return storage.setSpawnPoint(session.raidId(), player.getLocation());
    }

    public boolean setEvacPos1(Player player, Block block) {
        EditorSession session = ensureSession(player);
        if (session == null) {
            return false;
        }
        if (!worldManager.isEditorWorld(player.getWorld().getName())) {
            return false;
        }
        evacSelections.put(player.getUniqueId(),
                new BlockSelection(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
        return true;
    }

    public EvacSelectionOutcome setEvacPos2(Player player, Block block) {
        EditorSession session = ensureSession(player);
        if (session == null) {
            return new EvacSelectionOutcome(EvacSelectionStatus.NOT_EDITING, null);
        }
        if (!worldManager.isEditorWorld(player.getWorld().getName())) {
            return new EvacSelectionOutcome(EvacSelectionStatus.NOT_IN_EDITOR_WORLD, null);
        }
        BlockSelection pos1 = evacSelections.get(player.getUniqueId());
        if (pos1 == null) {
            return new EvacSelectionOutcome(EvacSelectionStatus.NO_POS1, null);
        }
        if (!pos1.world().equals(block.getWorld().getName())) {
            return new EvacSelectionOutcome(EvacSelectionStatus.WORLD_MISMATCH, null);
        }

        int minX = Math.min(pos1.x(), block.getX());
        int minY = Math.min(pos1.y(), block.getY());
        int minZ = Math.min(pos1.z(), block.getZ());
        int maxX = Math.max(pos1.x(), block.getX());
        int maxY = Math.max(pos1.y(), block.getY());
        int maxZ = Math.max(pos1.z(), block.getZ());
        String name = "Evac " + storage.nextEvacZoneIndex(session.raidId());
        EvacZoneEntry entry = storage.addEvacZone(session.raidId(), pos1.world(),
                minX, minY, minZ, maxX, maxY, maxZ, name);
        evacSelections.remove(player.getUniqueId());
        return new EvacSelectionOutcome(EvacSelectionStatus.SAVED, entry);
    }

    public void save() {
        storage.saveNow();
    }

    public MapEditorValidation validate(String raidId) {
        SpawnPointEntry spawn = storage.getSpawnPoint(raidId);
        int evacCount = storage.getEvacZones(raidId).size();
        int lootCount = storage.getLootContainers(raidId).size();
        return new MapEditorValidation(raidId, spawn != null, evacCount, lootCount);
    }

    public boolean isLootMarker(ItemStack stack) {
        if (stack == null || stack.getType() != Material.STICK) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(lootMarkerKey, PersistentDataType.BYTE);
    }

    public boolean isSpawnTool(ItemStack stack) {
        if (stack == null || stack.getType() != Material.COMPASS) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(spawnToolKey, PersistentDataType.BYTE);
    }

    public boolean isEvacTool(ItemStack stack) {
        if (stack == null || stack.getType() != Material.BLAZE_ROD) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(evacToolKey, PersistentDataType.BYTE);
    }

    public String getToolLabel(ItemStack stack) {
        if (isSpawnTool(stack)) {
            return "Spawn";
        }
        if (isEvacTool(stack)) {
            return "Evac";
        }
        if (isLootMarker(stack)) {
            return "Loot";
        }
        return null;
    }

    public void sendToolActionBar(Player player, ItemStack stack) {
        if (player == null || !isEditing(player)) {
            return;
        }
        String label = getToolLabel(stack);
        if (label == null) {
            player.sendActionBar("");
            return;
        }
        player.sendActionBar("Tool: " + label);
    }

    private void clearToolActionBar(Player player) {
        if (player != null) {
            player.sendActionBar("");
        }
    }

    private void ensureEditorTools(Player player) {
        boolean added = false;
        if (!hasLootMarker(player)) {
            player.getInventory().addItem(createLootMarker());
            added = true;
        }
        if (!hasSpawnTool(player)) {
            player.getInventory().addItem(createSpawnTool());
            added = true;
        }
        if (!hasEvacTool(player)) {
            player.getInventory().addItem(createEvacTool());
            added = true;
        }
        if (added) {
            player.sendMessage(ChatColor.GREEN + "Editor tools added to your inventory.");
        }
    }

    private EditorSession ensureSession(Player player) {
        if (player == null) {
            return null;
        }
        EditorSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            return session;
        }
        String worldName = player.getWorld().getName();
        if (!worldManager.isEditorWorld(worldName)) {
            return null;
        }
        String raidId = worldManager.getEditorRaidId(worldName);
        if (raidId == null || raidId.isBlank()) {
            return null;
        }
        EditorSession restored = new EditorSession(raidId, worldName);
        sessions.put(player.getUniqueId(), restored);
        lootMarkerChances.putIfAbsent(player.getUniqueId(), 1.0);
        ensureEditorTools(player);
        sendToolActionBar(player, player.getInventory().getItemInMainHand());
        return restored;
    }

    private boolean hasLootMarker(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isLootMarker(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSpawnTool(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isSpawnTool(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasEvacTool(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isEvacTool(item)) {
                return true;
            }
        }
        return false;
    }

    private ItemStack createLootMarker() {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Loot Marker");
        meta.setLore(List.of(
                ChatColor.GRAY + "Shift-right-click a block",
                ChatColor.GRAY + "to save a loot spawn point."));
        meta.getPersistentDataContainer().set(lootMarkerKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private int enableLootPreview(UUID playerId, String raidId, String worldName) {
        List<LootContainerEntry> entries = storage.getLootContainers(raidId);
        Set<BlockKey> placed = new HashSet<>();
        for (LootContainerEntry entry : entries) {
            if (!entry.world().equalsIgnoreCase(worldName)) {
                continue;
            }
            var world = Bukkit.getWorld(entry.world());
            if (world == null) {
                continue;
            }
        Block chestBlock = world.getBlockAt(entry.x(), entry.y(), entry.z());
        BlockFace facing = parseFacing(entry.facing());
        if (!chestBlock.getType().isAir()) {
            continue;
        }
        chestBlock.setType(Material.CHEST, false);
        BlockData data = chestBlock.getBlockData();
        if (data instanceof Directional directional) {
                directional.setFacing(facing);
                chestBlock.setBlockData(directional, false);
            }
            placed.add(new BlockKey(world.getName(), chestBlock.getX(), chestBlock.getY(), chestBlock.getZ()));
        }
        lootPreviewPlaced.put(playerId, placed);
        return placed.size();
    }

    private int disableLootPreview(UUID playerId, String raidId, String worldName) {
        Set<BlockKey> placed = lootPreviewPlaced.getOrDefault(playerId, Set.of());
        int touched = 0;
        for (BlockKey key : placed) {
            if (!key.world().equalsIgnoreCase(worldName)) {
                continue;
            }
            var world = Bukkit.getWorld(key.world());
            if (world == null) {
                continue;
            }
            Block block = world.getBlockAt(key.x(), key.y(), key.z());
            if (block.getType() == Material.CHEST) {
                block.setType(Material.AIR, false);
                touched++;
            }
        }
        lootPreviewPlaced.remove(playerId);
        return touched;
    }

    private BlockFace resolveFacing(Block block) {
        if (block == null) {
            return BlockFace.NORTH;
        }
        if (block.getBlockData() instanceof Directional directional) {
            return directional.getFacing();
        }
        return BlockFace.NORTH;
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

    private ItemStack createSpawnTool() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "Spawn Tool");
        meta.setLore(List.of(
                ChatColor.GRAY + "Left-click to set spawn",
                ChatColor.GRAY + "at your current location."));
        meta.getPersistentDataContainer().set(spawnToolKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createEvacTool() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "Evac Tool");
        meta.setLore(List.of(
                ChatColor.GRAY + "Left-click to set evac corner 1",
                ChatColor.GRAY + "Right-click to set evac corner 2."));
        meta.getPersistentDataContainer().set(evacToolKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private record BlockSelection(String world, int x, int y, int z) {
    }

    private record BlockKey(String world, int x, int y, int z) {
    }

    public enum LootPreviewStatus {
        NOT_EDITING,
        NOT_IN_EDITOR_WORLD,
        ENABLED,
        DISABLED
    }

    public enum EvacSelectionStatus {
        NOT_EDITING,
        NOT_IN_EDITOR_WORLD,
        NO_POS1,
        WORLD_MISMATCH,
        SAVED
    }

    public record LootPreviewOutcome(LootPreviewStatus status, int touchedContainers) {
    }

    public record EvacSelectionOutcome(EvacSelectionStatus status, EvacZoneEntry entry) {
    }

    public record MapEditorValidation(String raidId, boolean hasSpawn, int evacCount, int lootCount) {
    }
}
