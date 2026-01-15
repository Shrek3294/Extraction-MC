package com.raidextraction.editor;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public final class MapEditorStorage {
    private static final int SCHEMA_VERSION = 1;

    private final JavaPlugin plugin;
    private final Logger logger;
    private final File file;
    private FileConfiguration config;

    public MapEditorStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.file = new File(plugin.getDataFolder(), "locations.yml");
        load();
    }

    public void load() {
        if (!file.exists()) {
            config = new YamlConfiguration();
            config.set("schemaVersion", SCHEMA_VERSION);
            config.set("raids", new HashMap<>());
            save();
            return;
        }
        config = YamlConfiguration.loadConfiguration(file);
        int schemaVersion = config.getInt("schemaVersion", SCHEMA_VERSION);
        if (schemaVersion != SCHEMA_VERSION) {
            logger.warning("locations.yml schema mismatch: expected " + SCHEMA_VERSION + ", got " + schemaVersion);
        }
    }

    public LootContainerEntry addLootContainer(String raidId, Location location, BlockFace facing, double chance) {
        String path = "raids." + raidId + ".loot_containers";
        List<Map<?, ?>> entries = new ArrayList<>(config.getMapList(path));

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("world", location.getWorld().getName());
        map.put("x", location.getBlockX());
        map.put("y", location.getBlockY());
        map.put("z", location.getBlockZ());
        map.put("facing", facing.name());
        map.put("chance", chance);
        entries.add(map);

        config.set(path, entries);
        save();

        return new LootContainerEntry(
                raidId,
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ(),
                facing.name(),
                chance);
    }

    public SpawnPointEntry setSpawnPoint(String raidId, Location location) {
        String path = "raids." + raidId + ".spawn";
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("world", location.getWorld().getName());
        map.put("x", location.getX());
        map.put("y", location.getY());
        map.put("z", location.getZ());
        map.put("yaw", location.getYaw());
        map.put("pitch", location.getPitch());
        config.set(path, map);
        save();

        return new SpawnPointEntry(
                raidId,
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch());
    }

    public EvacZoneEntry addEvacZone(String raidId, String world, int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ, String name) {
        String path = "raids." + raidId + ".evac_zones";
        List<Map<?, ?>> entries = new ArrayList<>(config.getMapList(path));

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("world", world);
        map.put("minX", minX);
        map.put("minY", minY);
        map.put("minZ", minZ);
        map.put("maxX", maxX);
        map.put("maxY", maxY);
        map.put("maxZ", maxZ);
        entries.add(map);

        config.set(path, entries);
        save();

        return new EvacZoneEntry(raidId, name, world, minX, minY, minZ, maxX, maxY, maxZ);
    }

    public GuardSpawnEntry addGuardSpawn(String raidId, Location location, String guardType) {
        String path = "raids." + raidId + ".guards";
        List<Map<?, ?>> entries = new ArrayList<>(config.getMapList(path));

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("world", location.getWorld().getName());
        map.put("x", location.getX());
        map.put("y", location.getY());
        map.put("z", location.getZ());
        map.put("yaw", location.getYaw());
        map.put("pitch", location.getPitch());
        map.put("type", guardType);
        entries.add(map);

        config.set(path, entries);
        save();

        return new GuardSpawnEntry(
                raidId,
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                guardType);
    }

    public boolean removeLootContainer(LootContainerEntry entry) {
        String path = "raids." + entry.raidId() + ".loot_containers";
        List<Map<?, ?>> entries = new ArrayList<>(config.getMapList(path));
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (matchesEntry(entries.get(i), entry)) {
                entries.remove(i);
                config.set(path, entries);
                save();
                return true;
            }
        }
        return false;
    }

    public LootContainerEntry updateLootContainerChance(String raidId, Location location, double chance) {
        String path = "raids." + raidId + ".loot_containers";
        List<Map<?, ?>> entries = new ArrayList<>(config.getMapList(path));
        String worldName = location.getWorld().getName();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        for (int i = 0; i < entries.size(); i++) {
            Map<?, ?> map = entries.get(i);
            String world = asString(map.get("world"), "");
            int entryX = asInt(map.get("x"), Integer.MIN_VALUE);
            int entryY = asInt(map.get("y"), Integer.MIN_VALUE);
            int entryZ = asInt(map.get("z"), Integer.MIN_VALUE);
            if (!worldName.equals(world) || x != entryX || y != entryY || z != entryZ) {
                continue;
            }
            String facing = asString(map.get("facing"), "");
            Map<String, Object> updated = new LinkedHashMap<>();
            updated.put("world", world);
            updated.put("x", entryX);
            updated.put("y", entryY);
            updated.put("z", entryZ);
            updated.put("facing", facing);
            updated.put("chance", chance);
            entries.set(i, updated);
            config.set(path, entries);
            save();
            return new LootContainerEntry(raidId, world, entryX, entryY, entryZ, facing, chance);
        }
        return null;
    }

    public SpawnPointEntry getSpawnPoint(String raidId) {
        var section = config.getConfigurationSection("raids." + raidId + ".spawn");
        if (section == null) {
            return null;
        }
        String world = section.getString("world", "");
        double x = section.getDouble("x", 0.0);
        double y = section.getDouble("y", 0.0);
        double z = section.getDouble("z", 0.0);
        float yaw = (float) section.getDouble("yaw", 0.0);
        float pitch = (float) section.getDouble("pitch", 0.0);
        if (world.isBlank()) {
            return null;
        }
        return new SpawnPointEntry(raidId, world, x, y, z, yaw, pitch);
    }

    public List<EvacZoneEntry> getEvacZones(String raidId) {
        String path = "raids." + raidId + ".evac_zones";
        List<Map<?, ?>> entries = new ArrayList<>(config.getMapList(path));
        List<EvacZoneEntry> zones = new ArrayList<>();
        for (Map<?, ?> map : entries) {
            String name = asString(map.get("name"), "");
            String world = asString(map.get("world"), "");
            int minX = asInt(map.get("minX"), Integer.MIN_VALUE);
            int minY = asInt(map.get("minY"), Integer.MIN_VALUE);
            int minZ = asInt(map.get("minZ"), Integer.MIN_VALUE);
            int maxX = asInt(map.get("maxX"), Integer.MIN_VALUE);
            int maxY = asInt(map.get("maxY"), Integer.MIN_VALUE);
            int maxZ = asInt(map.get("maxZ"), Integer.MIN_VALUE);
            if (world.isBlank()) {
                continue;
            }
            zones.add(new EvacZoneEntry(raidId, name, world, minX, minY, minZ, maxX, maxY, maxZ));
        }
        return zones;
    }

    public List<LootContainerEntry> getLootContainers(String raidId) {
        String path = "raids." + raidId + ".loot_containers";
        List<Map<?, ?>> entries = new ArrayList<>(config.getMapList(path));
        List<LootContainerEntry> containers = new ArrayList<>();
        for (Map<?, ?> map : entries) {
            String world = asString(map.get("world"), "");
            int x = asInt(map.get("x"), Integer.MIN_VALUE);
            int y = asInt(map.get("y"), Integer.MIN_VALUE);
            int z = asInt(map.get("z"), Integer.MIN_VALUE);
            String facing = asString(map.get("facing"), "");
            double chance = asDouble(map.get("chance"), 1.0);
            if (world.isBlank()) {
                continue;
            }
            containers.add(new LootContainerEntry(raidId, world, x, y, z, facing, chance));
        }
        return containers;
    }

    public List<GuardSpawnEntry> getGuardSpawns(String raidId) {
        String path = "raids." + raidId + ".guards";
        List<Map<?, ?>> entries = new ArrayList<>(config.getMapList(path));
        List<GuardSpawnEntry> guards = new ArrayList<>();
        for (Map<?, ?> map : entries) {
            String world = asString(map.get("world"), "");
            double x = asDouble(map.get("x"), 0.0);
            double y = asDouble(map.get("y"), 0.0);
            double z = asDouble(map.get("z"), 0.0);
            float yaw = (float) asDouble(map.get("yaw"), 0.0);
            float pitch = (float) asDouble(map.get("pitch"), 0.0);
            String type = asString(map.get("type"), "BASIC");
            if (world.isBlank()) {
                continue;
            }
            guards.add(new GuardSpawnEntry(raidId, world, x, y, z, yaw, pitch, type));
        }
        return guards;
    }

    public int nextEvacZoneIndex(String raidId) {
        return getEvacZones(raidId).size() + 1;
    }

    public void saveNow() {
        save();
    }

    private boolean matchesEntry(Map<?, ?> map, LootContainerEntry entry) {
        String world = asString(map.get("world"), "");
        int x = asInt(map.get("x"), Integer.MIN_VALUE);
        int y = asInt(map.get("y"), Integer.MIN_VALUE);
        int z = asInt(map.get("z"), Integer.MIN_VALUE);
        String facing = asString(map.get("facing"), "");
        if (!world.equals(entry.world())) {
            return false;
        }
        if (x != entry.x() || y != entry.y() || z != entry.z()) {
            return false;
        }
        if (!facing.isBlank() && !facing.equalsIgnoreCase(entry.facing())) {
            return false;
        }
        return true;
    }

    private int asInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return fallback;
    }

    private double asDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return fallback;
    }

    private String asString(Object value, String fallback) {
        return value != null ? value.toString() : fallback;
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException exception) {
            logger.warning("Failed to save locations.yml: " + exception.getMessage());
        }
    }
}
