package com.raidextraction.quest;

import java.util.Objects;

/**
 * Represents a single task within a quest.
 */
public class QuestTask {
    private final String taskId;
    private final TaskType type;
    private final String target; // Item name, mob type, etc.
    private final int requiredAmount;
    private int progress;
    private boolean completed;

    public QuestTask(String taskId, TaskType type, String target, int requiredAmount) {
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.type = Objects.requireNonNull(type, "type");
        this.target = Objects.requireNonNull(target, "target");
        this.requiredAmount = requiredAmount;
        this.progress = 0;
        this.completed = false;
        
        if (requiredAmount <= 0) {
            throw new IllegalArgumentException("requiredAmount must be positive");
        }
    }

    // Getters
    public String getTaskId() { return taskId; }
    public TaskType getType() { return type; }
    public String getTarget() { return target; }
    public int getRequiredAmount() { return requiredAmount; }
    public int getProgress() { return progress; }
    public boolean isCompleted() { return completed; }
    
    // Progress management
    public void addProgress(int amount) {
        if (amount <= 0) return;
        this.progress = Math.min(requiredAmount, this.progress + amount);
        checkCompletion();
    }
    
    public void setProgress(int progress) {
        this.progress = Math.max(0, Math.min(requiredAmount, progress));
        checkCompletion();
    }
    
    private void checkCompletion() {
        if (progress >= requiredAmount) {
            this.completed = true;
            this.progress = requiredAmount;
        }
    }
    
    public double getCompletionPercentage() {
        return (double) progress / requiredAmount;
    }
    
    public int getRemainingAmount() {
        return Math.max(0, requiredAmount - progress);
    }
}