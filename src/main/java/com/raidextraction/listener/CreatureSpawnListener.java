package com.raidextraction.listener;

import com.raidextraction.config.ConfigManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Prevents natural mob spawning in raid worlds.
 * Only allows spawning from plugin-managed sources (guards, custom encounters).
 */
public final class CreatureSpawnListener implements Listener {
    private final ConfigManager configManager;
    private final Set<String> raidWorlds = new HashSet<>();

    public CreatureSpawnListener(ConfigManager configManager) {
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        rebuildRaidWorldsList();
    }

    /**
     * Call this after raid definitions are reloaded to sync the raid world list.
     */
    public void rebuildRaidWorldsList() {
        raidWorlds.clear();
        configManager.getRaidDefinitions().values().forEach(def -> raidWorlds.add(def.world()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        String worldName = event.getLocation().getWorld().getName();

        // Allow spawning only in raid worlds
        if (!raidWorlds.contains(worldName)) {
            return;
        }

        // Allow plugin-controlled sources only
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason == CreatureSpawnEvent.SpawnReason.CUSTOM) {
            // Plugin-spawned entities (guards, encounters) are allowed
            return;
        }

        // Deny all natural spawns: NATURAL, SPAWNER, EGG, BREEDING, etc.
        event.setCancelled(true);
    }
}
