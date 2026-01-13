package com.raidextraction.config.model;

import java.util.List;

public record RaidDefinition(
        String id,
        String world,
        int minPlayers,
        int maxPlayers,
        int durationSeconds,
        String lootTableId,
        List<EvacZoneDefinition> evacZones,
        RaidBoundsDefinition bounds, // Optional, null if not configured
        RaidSpawnConfig spawn, // New field,
        int targetLootCount) {
    public boolean isValid() {
        return world != null && !world.isEmpty() && minPlayers > 0 && maxPlayers >= minPlayers && durationSeconds > 0
                && lootTableId != null && !lootTableId.isEmpty() && targetLootCount >= 0;
    }
}
