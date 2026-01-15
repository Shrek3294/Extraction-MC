package com.raidextraction.quest;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a player's progress on a specific quest task.
 * This is the data model for database persistence.
 */
public record PlayerQuestTask(
        UUID playerId,
        String questId,
        String taskId,
        int progress,
        boolean completed,
        long createdAtMs,
        long updatedAtMs
) {
    public PlayerQuestTask {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(questId, "questId");
        Objects.requireNonNull(taskId, "taskId");
        if (progress < 0) {
            throw new IllegalArgumentException("progress cannot be negative");
        }
    }
    
    public static PlayerQuestTask createNew(UUID playerId, String questId, String taskId) {
        long now = System.currentTimeMillis();
        return new PlayerQuestTask(playerId, questId, taskId, 0, false, now, now);
    }
    
    public PlayerQuestTask withProgress(int newProgress, boolean isCompleted) {
        long now = System.currentTimeMillis();
        return new PlayerQuestTask(
                playerId, questId, taskId, 
                Math.max(0, newProgress), 
                isCompleted, 
                createdAtMs, 
                now
        );
    }
}