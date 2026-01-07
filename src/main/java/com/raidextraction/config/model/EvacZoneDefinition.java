package com.raidextraction.config.model;

public record EvacZoneDefinition(
        String name,
        String world,
        int x,
        int y,
        int z,
        int radius
) {
    public boolean isValid() {
        return world != null && !world.isEmpty() && radius > 0;
    }
}
