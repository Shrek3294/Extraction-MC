package com.raidextraction.integration.stub;

import com.raidextraction.integration.WorldManager;

import java.util.Objects;

/**
 * No-op stub implementation of WorldManager for testing.
 */
public final class NoopWorldManager implements WorldManager {

    @Override
    public String getEditorWorld(String raidId) {
        Objects.requireNonNull(raidId, "raidId");
        return null;
    }

    @Override
    public String getEditorRaidId(String worldName) {
        Objects.requireNonNull(worldName, "worldName");
        return null;
    }

    @Override
    public boolean isEditorWorld(String worldName) {
        Objects.requireNonNull(worldName, "worldName");
        return false;
    }

    @Override
    public void enterEditorMode(String raidId, String worldName) {
        Objects.requireNonNull(raidId, "raidId");
        Objects.requireNonNull(worldName, "worldName");
        // No-op
    }

    @Override
    public void exitEditorMode(String raidId) {
        Objects.requireNonNull(raidId, "raidId");
        // No-op
    }

    @Override
    public boolean isBeingEdited(String raidId) {
        Objects.requireNonNull(raidId, "raidId");
        return false;
    }
}
