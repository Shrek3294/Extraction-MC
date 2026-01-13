package com.raidextraction.editor;

public record EvacZoneEntry(
        String raidId,
        String name,
        String world,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ) {
}
