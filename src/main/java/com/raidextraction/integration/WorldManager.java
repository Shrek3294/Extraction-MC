package com.raidextraction.integration;

/**
 * Manages world operations for raid template editing and instance management.
 * V2.0: Minimal foundation for template editing (no instancing yet).
 * V2.2: Full world cloning and instance management.
 */
public interface WorldManager {

    /**
     * Get the world name for editing a raid template.
     * In V2.0, this returns the raid's configured world (no cloning).
     * In V2.2+, this will return a cloned instance world.
     */
    String getEditorWorld(String raidId);

    /**
     * Resolve the raidId currently mapped to an editor world name.
     */
    String getEditorRaidId(String worldName);

    /**
     * Check if a world is currently in editor mode.
     */
    boolean isEditorWorld(String worldName);

    /**
     * Enter editor mode for a raid template.
     * Marks the raid as being edited and prevents raid starts.
     */
    void enterEditorMode(String raidId, String worldName);

    /**
     * Exit editor mode for a raid template.
     */
    void exitEditorMode(String raidId);

    /**
     * Check if a raid is currently being edited.
     */
    boolean isBeingEdited(String raidId);
}
