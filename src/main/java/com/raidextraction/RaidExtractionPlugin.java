package com.raidextraction;

import com.raidextraction.config.ConfigManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Base plugin entrypoint for the Raid Extraction Paper plugin.
 */
public final class RaidExtractionPlugin extends JavaPlugin {

    private ConfigManager configManager;

    @Override
    public void onLoad() {
        getLogger().info("RaidExtraction plugin loading (preparing data folder and config scaffolding).");
        prepareDataFolder();
    }

    @Override
    public void onEnable() {
        getLogger().info("RaidExtraction plugin enabling (commands and listeners will register in later phases).");
        prepareDataFolder();
        initializeConfigurationScaffolding();
    }

    @Override
    public void onDisable() {
        getLogger().info("RaidExtraction plugin disabling. Cleaning up resources.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    private void prepareDataFolder() {
        if (!getDataFolder().exists() && getDataFolder().mkdirs()) {
            getLogger().info("Created plugin data folder at " + getDataFolder().getAbsolutePath());
        }
    }

    private void initializeConfigurationScaffolding() {
        configManager = new ConfigManager(this);
        configManager.load();

        if (configManager.getRaidDefinitions().isEmpty()) {
            getLogger().warning("No raid definitions found; add entries to raids.yml to enable raid creation.");
        }

        if (configManager.getLootTableDefinitions().isEmpty()) {
            getLogger().warning("No loot tables found; add entries to loot_tables.yml to enable loot rolls.");
        }

        getLogger().info("Configuration scaffolding ready; raid and loot definitions loaded for future phases.");
    }
}
