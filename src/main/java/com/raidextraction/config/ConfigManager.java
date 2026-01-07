package com.raidextraction.config;

import com.raidextraction.config.model.EvacZoneDefinition;
import com.raidextraction.config.model.LootEntry;
import com.raidextraction.config.model.LootTableDefinition;
import com.raidextraction.config.model.RaidDefinition;
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

    private Map<String, RaidDefinition> raidDefinitions = Collections.emptyMap();
    private Map<String, LootTableDefinition> lootTableDefinitions = Collections.emptyMap();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void load() {
        ensureResource("config.yml");
        ensureResource("raids.yml");
        ensureResource("loot_tables.yml");
        ensureResource("director.yml");

        mainConfig = loadConfig("config.yml");
        raidsConfig = loadConfig("raids.yml");
        lootTablesConfig = loadConfig("loot_tables.yml");
        directorConfig = loadConfig("director.yml");

        lootTableDefinitions = parseLootTables();
        raidDefinitions = parseRaids();
    }

    public FileConfiguration getMainConfig() {
        return mainConfig;
    }

    public FileConfiguration getDirectorConfig() {
        return directorConfig;
    }

    public Map<String, RaidDefinition> getRaidDefinitions() {
        return raidDefinitions;
    }

    public Map<String, LootTableDefinition> getLootTableDefinitions() {
        return lootTableDefinitions;
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
                        asInt(entryMap.get("weight"), 1),
                        asInt(entryMap.get("min_amount"), 1),
                        asInt(entryMap.get("max_amount"), 1)
                );
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
            RaidDefinition definition = new RaidDefinition(
                    id,
                    raidSection.getString("world", "world"),
                    raidSection.getInt("min_players", 1),
                    raidSection.getInt("max_players", 4),
                    raidSection.getInt("duration_seconds", 900),
                    raidSection.getString("loot_table", "default"),
                    evacZones
            );

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
                    asInt(map.get("radius"), 6)
            );
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

    private String asString(Object value, String fallback) {
        return value != null ? value.toString() : fallback;
    }
}
