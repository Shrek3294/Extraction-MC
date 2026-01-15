package com.raidextraction.quest;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a player's progress on a specific quest.
 * This is the data model for database persistence.
 */
public record PlayerQuest(
        UUID playerId,
        String questId,
        boolean completed,
        boolean claimed,
        long createdAtMs,
        long updatedAtMs
) {
    public PlayerQuest {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(questId, "questId");
    }
    
    public static PlayerQuest createNew(UUID playerId, String questId) {
        long now = System.currentTimeMillis();
        return new PlayerQuest(playerId, questId, false, false, now, now);
    }
}