package com.raidextraction.config.model;

/**
 * Defines the playable boundaries for a raid using a cuboid region.
 */
public record RaidBoundsDefinition(
        String world,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ) {
    public boolean isValid() {
        return world != null && !world.isBlank()
                && minX <= maxX
                && minY <= maxY
                && minZ <= maxZ;
    }

    /**
     * Check if the given coordinates are within the bounds.
     */
    public boolean contains(double x, double y, double z) {
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }
}
