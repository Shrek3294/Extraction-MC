package com.raidextraction.config.model;

/**
 * Configuration for lobby spawn location where players return after raids.
 */
public record LobbySpawnConfig(
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch) {
    public boolean isValid() {
        return world != null && !world.isBlank();
    }
}
