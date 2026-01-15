package com.raidextraction.editor;

public record GuardSpawnEntry(
        String raidId,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        String guardType) {
}
