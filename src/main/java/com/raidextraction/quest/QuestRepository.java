package com.raidextraction.quest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for quest persistence operations.
 */
public interface QuestRepository {
    
    /**
     * Load all quests for a player
     */
    List<PlayerQuest> loadPlayerQuests(UUID playerId);
    
    /**
     * Load all task progress for a player's quest
     */
    List<PlayerQuestTask> loadPlayerQuestTasks(UUID playerId, String questId);
    
    /**
     * Save or update a player's quest progress
     */
    void savePlayerQuest(PlayerQuest playerQuest);
    
    /**
     * Save or update a player's task progress
     */
    void savePlayerQuestTask(PlayerQuestTask playerQuestTask);
    
    /**
     * Mark a quest as claimed (rewards collected)
     */
    void markQuestClaimed(UUID playerId, String questId);
    
    /**
     * Reset a player's quest progress (for repeatable quests)
     */
    void resetPlayerQuest(UUID playerId, String questId);
}