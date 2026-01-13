package com.raidextraction.editor;

public record LootContainerEntry(
        String raidId,
        String world,
        int x,
        int y,
        int z,
        String facing,
        double chance) {
}
