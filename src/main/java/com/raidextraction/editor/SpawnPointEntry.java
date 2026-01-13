package com.raidextraction.editor;

public record SpawnPointEntry(
        String raidId,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch) {
}
