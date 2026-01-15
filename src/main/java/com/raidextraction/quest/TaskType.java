package com.raidextraction.quest;

/**
 * Defines the different types of tasks that can be part of quests.
 */
public enum TaskType {
    /**
     * Extract a specific number of items
     */
    EXTRACT_ITEMS,
    
    /**
     * Kill a specific number of mobs
     */
    KILL_MOBS,
    
    /**
     * Deposit credits to the trader
     */
    DEPOSIT_CREDITS,
    
    /**
     * Complete extractions successfully
     */
    COMPLETE_EXTRACTIONS,
    
    /**
     * Find and extract specific rare items
     */
    FIND_RARE_ITEMS
}