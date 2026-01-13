package com.raidextraction.config.model;

/**
 * Configuration for a raid's explicit spawn point.
 * Overrides the default world spawn if present.
 */
public record RaidSpawnConfig(double x, double y, double z, float yaw, float pitch) {
}
