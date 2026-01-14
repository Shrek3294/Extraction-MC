package com.raidextraction.config;

import com.raidextraction.config.model.EvacZoneDefinition;
import com.raidextraction.config.model.LobbySpawnConfig;
import com.raidextraction.config.model.LootEntry;
import com.raidextraction.config.model.LootTableDefinition;
import com.raidextraction.config.model.RaidBoundsDefinition;
import com.raidextraction.config.model.RaidDefinition;
import com.raidextraction.config.model.RaidSpawnConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public final class ConfigManager {
    private final JavaPlugin plugin;
    private final Logger logger;

    private FileConfiguration mainConfig;
    private FileConfiguration raidsConfig;
    private FileConfiguration lootTablesConfig;
    private FileConfiguration directorConfig;
    private FileConfiguration itemsConfig;
    private FileConfiguration traderConfig;

    private Map<String, RaidDefinition> raidDefinitions = Collections.emptyMap();
    private Map<String, LootTableDefinition> lootTableDefinitions = Collections.emptyMap();
    private com.raidextraction.item.ItemsConfig itemsDefinition = com.raidextraction.item.ItemsConfig.empty();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void load() {
        ensureResource("config.yml");
        ensureResource("raids.yml");
        ensureResource("loot_tables.yml");
        ensureResource("director.yml");
        ensureResource("items.yml");
        ensureResource("trader.yml");

        mainConfig = loadConfig("config.yml");
        raidsConfig = loadConfig("raids.yml");
        lootTablesConfig = loadConfig("loot_tables.yml");
        directorConfig = loadConfig("director.yml");
        itemsConfig = loadConfig("items.yml");
        traderConfig = loadConfig("trader.yml");

        lootTableDefinitions = parseLootTables();
        raidDefinitions = parseRaids();
        itemsDefinition = com.raidextraction.item.ItemsConfig.parse(itemsConfig, logger);
    }

    public FileConfiguration getMainConfig() {
        return mainConfig;
    }

    public String getLobbyWorld() {
        String value = mainConfig.getString("lobbyWorld");
        if (value != null && !value.isBlank()) {
            return value;
        }
        String legacyValue = mainConfig.getString("lobby_world");
        if (legacyValue != null && !legacyValue.isBlank()) {
            return legacyValue;
        }
        return "world";
    }

    public LobbySpawnConfig getLobbySpawnConfig() {
        String world = getLobbyWorld();
        ConfigurationSection lobbySpawn = mainConfig.getConfigurationSection("lobbySpawn");
        if (lobbySpawn == null) {
            // Only warn once or if specifically debugging, to avoid log spam if defaults
            // are intentional
            // logger.warning("Missing lobbySpawn config; using defaults.");
            return new LobbySpawnConfig(world, 0, 64, 0, 0, 0);
        }
        return new LobbySpawnConfig(
                world,
                lobbySpawn.getDouble("x", 0),
                lobbySpawn.getDouble("y", 64),
                lobbySpawn.getDouble("z", 0),
                (float) lobbySpawn.getDouble("yaw", 0),
                (float) lobbySpawn.getDouble("pitch", 0));
    }

    public int getStashMaxStacks() {
        ConfigurationSection stashSection = mainConfig.getConfigurationSection("stash");
        if (stashSection == null) {
            return mainConfig.getInt("stash_max_stacks", 54);
        }
        return stashSection.getInt("max_stacks", 54);
    }

    public boolean isProgressionEnabled() {
        ConfigurationSection section = mainConfig.getConfigurationSection("progression");
        if (section == null) {
            return mainConfig.getBoolean("progression_enabled", false);
        }
        return section.getBoolean("enabled", false);
    }

    public long getExtractionSuccessXp() {
        ConfigurationSection section = mainConfig.getConfigurationSection("progression");
        if (section == null) {
            return mainConfig.getLong("extraction_success_xp", 25L);
        }
        return section.getLong("extraction_success_xp", 25L);
    }

    public long getXpPerExtractedStack() {
        ConfigurationSection section = mainConfig.getConfigurationSection("progression");
        if (section == null) {
            return mainConfig.getLong("xp_per_stack", 1L);
        }
        return section.getLong("xp_per_stack", 1L);
    }

    public int getXpPerLevel() {
        ConfigurationSection section = mainConfig.getConfigurationSection("progression");
        if (section == null) {
            return mainConfig.getInt("xp_per_level", 100);
        }
        return section.getInt("xp_per_level", 100);
    }

    public boolean isHudEnabled() {
        ConfigurationSection section = mainConfig.getConfigurationSection("hud");
        if (section == null) {
            return mainConfig.getBoolean("hud_enabled", true);
        }
        return section.getBoolean("enabled", true);
    }

    public String getHudServerName() {
        ConfigurationSection section = mainConfig.getConfigurationSection("hud");
        if (section == null) {
            return mainConfig.getString("server_name", "Raid Extraction");
        }
        return section.getString("server_name", "Raid Extraction");
    }

    public int getHudUpdateTicks() {
        ConfigurationSection section = mainConfig.getConfigurationSection("hud");
        if (section == null) {
            return mainConfig.getInt("hud_update_ticks", 40);
        }
        return Math.max(1, section.getInt("update_ticks", 40));
    }

    public void setLobbySpawn(org.bukkit.Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        mainConfig.set("lobbyWorld", location.getWorld().getName());
        ConfigurationSection section = mainConfig.createSection("lobbySpawn");
        section.set("x", location.getX());
        section.set("y", location.getY());
        section.set("z", location.getZ());
        section.set("yaw", location.getYaw());
        section.set("pitch", location.getPitch());
        try {
            mainConfig.save(new File(plugin.getDataFolder(), "config.yml"));
        } catch (Exception e) {
            logger.log(java.util.logging.Level.SEVERE, "Failed to save lobby spawn to config.yml", e);
        }
    }

    public FileConfiguration getDirectorConfig() {
        return directorConfig;
    }

    public FileConfiguration getTraderConfig() {
        return traderConfig;
    }

    public Map<String, RaidDefinition> getRaidDefinitions() {
        return raidDefinitions;
    }

    public Map<String, LootTableDefinition> getLootTableDefinitions() {
        return lootTableDefinitions;
    }

    public com.raidextraction.item.ItemsConfig getItemsDefinition() {
        return itemsDefinition;
    }

    private void ensureResource(String resourceName) {
        File target = new File(plugin.getDataFolder(), resourceName);
        if (!target.exists()) {
            plugin.saveResource(resourceName, false);
            logger.info("Saved default " + resourceName + " to data folder.");
        }
    }

    private FileConfiguration loadConfig(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        return YamlConfiguration.loadConfiguration(file);
    }

    private Map<String, LootTableDefinition> parseLootTables() {
        ConfigurationSection section = lootTablesConfig.getConfigurationSection("loot_tables");
        if (section == null) {
            logger.warning("No loot_tables section found in loot_tables.yml. Add at least one table.");
            return Collections.emptyMap();
        }

        Map<String, LootTableDefinition> tables = new HashMap<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection tableSection = section.getConfigurationSection(id);
            if (tableSection == null) {
                continue;
            }

            List<LootEntry> entries = new ArrayList<>();
            List<Map<?, ?>> entryMaps = tableSection.getMapList("entries");
            for (Map<?, ?> entryMap : entryMaps) {
                LootEntry entry = new LootEntry(
                        asString(entryMap.get("id"), ""),
                        asString(entryMap.get("material"), ""),
                        asString(entryMap.get("category"), ""),
                        asLong(entryMap.get("credits"), 0L),
                        asInt(entryMap.get("weight"), 1),
                        asInt(entryMap.get("min_amount"), 1),
                        asInt(entryMap.get("max_amount"), 1));
                if (!entry.isValid()) {
                    logger.warning("Invalid loot entry in table " + id + ": " + entry);
                    continue;
                }
                entries.add(entry);
            }

            LootTableDefinition definition = new LootTableDefinition(id, entries);
            if (!definition.isValid()) {
                logger.warning("Loot table " + id + " is missing required fields or has no valid entries.");
                continue;
            }
            tables.put(id, definition);
        }

        logger.info("Loaded " + tables.size() + " loot table(s).");
        return tables;
    }

    private Map<String, RaidDefinition> parseRaids() {
        ConfigurationSection section = raidsConfig.getConfigurationSection("raids");
        if (section == null) {
            logger.warning("No raids section found in raids.yml. Add at least one raid definition.");
            return Collections.emptyMap();
        }

        Map<String, RaidDefinition> raids = new HashMap<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection raidSection = section.getConfigurationSection(id);
            if (raidSection == null) {
                continue;
            }

            List<EvacZoneDefinition> evacZones = parseEvacZones(raidSection);
            RaidBoundsDefinition bounds = parseRaidBounds(raidSection);
            RaidSpawnConfig spawn = parseRaidSpawn(raidSection);
            RaidDefinition definition = new RaidDefinition(
                    id,
                    raidSection.getString("world", "world"),
                    raidSection.getInt("min_players", 1),
                    raidSection.getInt("max_players", 4),
                    raidSection.getInt("duration_seconds", 900),
                    raidSection.getString("loot_table", "default"),
                    evacZones,
                    bounds,
                    spawn,
                    raidSection.getInt("target_loot_count", 30));
            if (!definition.isValid()) {
                logger.warning("Raid " + id + " is missing required fields or has invalid limits.");
                continue;
            }

            if (!evacZones.isEmpty() && evacZones.stream().noneMatch(EvacZoneDefinition::isValid)) {
                logger.warning("Raid " + id + " has evac zones defined but none are valid.");
            }

            raids.put(id, definition);
        }

        logger.info("Loaded " + raids.size() + " raid definition(s).");
        return raids;
    }

    private List<EvacZoneDefinition> parseEvacZones(ConfigurationSection raidSection) {
        List<EvacZoneDefinition> zones = new ArrayList<>();
        List<Map<?, ?>> zoneMaps = raidSection.getMapList("evac_zones");
        for (Map<?, ?> map : zoneMaps) {
            EvacZoneDefinition zone = new EvacZoneDefinition(
                    asString(map.get("name"), ""),
                    asString(map.get("world"), raidSection.getString("world", "world")),
                    asInt(map.get("x"), 0),
                    asInt(map.get("y"), 64),
                    asInt(map.get("z"), 0),
                    asInt(map.get("radius"), 6));
            if (!zone.isValid()) {
                logger.warning("Invalid evac zone in raid " + raidSection.getName() + ": " + zone);
                continue;
            }
            zones.add(zone);
        }
        return zones;
    }

    private int asInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return fallback;
    }

    private long asLong(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return fallback;
    }

    private String asString(Object value, String fallback) {
        return value != null ? value.toString() : fallback;
    }

    private RaidBoundsDefinition parseRaidBounds(ConfigurationSection raidSection) {
        ConfigurationSection boundsSection = raidSection.getConfigurationSection("bounds");
        if (boundsSection == null) {
            return null; // Bounds are optional
        }
        String world = raidSection.getString("world", "world");
        RaidBoundsDefinition bounds = new RaidBoundsDefinition(
                world,
                boundsSection.getInt("minX", 0),
                boundsSection.getInt("minY", 0),
                boundsSection.getInt("minZ", 0),
                boundsSection.getInt("maxX", 0),
                boundsSection.getInt("maxY", 256),
                boundsSection.getInt("maxZ", 0));
        if (!bounds.isValid()) {
            logger.warning("Invalid bounds in raid " + raidSection.getName() + "; bounds will be ignored.");
            return null;
        }
        return bounds;
    }

    private RaidSpawnConfig parseRaidSpawn(ConfigurationSection raidSection) {
        ConfigurationSection spawnSection = raidSection.getConfigurationSection("spawn");
        if (spawnSection == null) {
            return null; // Spawn is optional
        }
        return new RaidSpawnConfig(
                spawnSection.getDouble("x", 0),
                spawnSection.getDouble("y", 64),
                spawnSection.getDouble("z", 0),
                (float) spawnSection.getDouble("yaw", 0),
                (float) spawnSection.getDouble("pitch", 0));
    }
}
