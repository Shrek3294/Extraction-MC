package com.raidextraction.integration.paper;

import com.raidextraction.integration.WorldManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Paper implementation of WorldManager for V2.0.
 * Tracks editor sessions and prevents editing while raids are active.
 * V2.2 will add world cloning and instance management.
 */
public final class PaperWorldManager implements WorldManager {
    private final JavaPlugin plugin;
    private final Logger logger;
    private final Map<String, String> editorSessions = new HashMap<>(); // raidId -> worldName

    public PaperWorldManager(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = plugin.getLogger();
    }

    @Override
    public String getEditorWorld(String raidId) {
        Objects.requireNonNull(raidId, "raidId");
        return editorSessions.get(raidId);
    }

    @Override
    public String getEditorRaidId(String worldName) {
        Objects.requireNonNull(worldName, "worldName");
        for (Map.Entry<String, String> entry : editorSessions.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(worldName)) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Override
    public boolean isEditorWorld(String worldName) {
        Objects.requireNonNull(worldName, "worldName");
        return editorSessions.containsValue(worldName);
    }

    @Override
    public void enterEditorMode(String raidId, String worldName) {
        Objects.requireNonNull(raidId, "raidId");
        Objects.requireNonNull(worldName, "worldName");
        editorSessions.put(raidId, worldName);
        logger.info("Entered editor mode for raid: " + raidId + " in world: " + worldName);
    }

    @Override
    public void exitEditorMode(String raidId) {
        Objects.requireNonNull(raidId, "raidId");
        String worldName = editorSessions.remove(raidId);
        if (worldName != null) {
            logger.info("Exited editor mode for raid: " + raidId);
        }
    }

    @Override
    public boolean isBeingEdited(String raidId) {
        Objects.requireNonNull(raidId, "raidId");
        return editorSessions.containsKey(raidId);
    }
}
