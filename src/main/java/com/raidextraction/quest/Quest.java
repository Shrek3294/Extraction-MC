package com.raidextraction.quest;

import java.util.*;

/**
 * Represents a complete quest with multiple tasks.
 */
public class Quest {
    private final String questId;
    private final String name;
    private final String description;
    private final List<QuestTask> tasks;
    private final long rewardCredits;
    private final int rewardXp;
    private boolean completed;
    private boolean claimed;

    public Quest(String questId, String name, String description, long rewardCredits, int rewardXp) {
        this.questId = Objects.requireNonNull(questId, "questId");
        this.name = Objects.requireNonNull(name, "name");
        this.description = Objects.requireNonNull(description, "description");
        this.tasks = new ArrayList<>();
        this.rewardCredits = Math.max(0, rewardCredits);
        this.rewardXp = Math.max(0, rewardXp);
        this.completed = false;
        this.claimed = false;
    }

    // Getters
    public String getQuestId() { return questId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<QuestTask> getTasks() { return new ArrayList<>(tasks); }
    public long getRewardCredits() { return rewardCredits; }
    public int getRewardXp() { return rewardXp; }
    public boolean isCompleted() { return completed; }
    public boolean isClaimed() { return claimed; }

    // Task management
    public void addTask(QuestTask task) {
        Objects.requireNonNull(task, "task");
        tasks.add(task);
    }

    public Optional<QuestTask> getTask(String taskId) {
        return tasks.stream()
                .filter(task -> task.getTaskId().equals(taskId))
                .findFirst();
    }

    // Progress checking
    public boolean checkCompletion() {
        this.completed = tasks.stream().allMatch(QuestTask::isCompleted);
        return this.completed;
    }

    public double getOverallProgress() {
        if (tasks.isEmpty()) return 0.0;
        
        double totalProgress = tasks.stream()
                .mapToDouble(QuestTask::getCompletionPercentage)
                .sum();
        return totalProgress / tasks.size();
    }

    public List<QuestTask> getIncompleteTasks() {
        return tasks.stream()
                .filter(task -> !task.isCompleted())
                .toList();
    }

    // Claim management
    public void claimRewards() {
        if (!completed) {
            throw new IllegalStateException("Cannot claim rewards for incomplete quest");
        }
        this.claimed = true;
    }

    public boolean canBeClaimed() {
        return completed && !claimed;
    }
}