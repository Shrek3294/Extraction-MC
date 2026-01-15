package com.raidextraction.quest;

import com.raidextraction.profile.PlayerProfileService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Main service for managing quests and player progress.
 */
public class QuestManager {
    private final JavaPlugin plugin;
    private final QuestRepository repository;
    private final PlayerProfileService profileService;
    private final Logger logger;
    
    // In-memory cache of active quests
    private final Map<String, Quest> questTemplates;
    private final Map<UUID, Map<String, Quest>> playerActiveQuests;

    public QuestManager(JavaPlugin plugin, QuestRepository repository, PlayerProfileService profileService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.profileService = Objects.requireNonNull(profileService, "profileService");
        this.logger = plugin.getLogger();
        this.questTemplates = new ConcurrentHashMap<>();
        this.playerActiveQuests = new ConcurrentHashMap<>();
        
        initializeDefaultQuests();
    }

    /**
     * Initialize some default quests for testing and demonstration
     */
    private void initializeDefaultQuests() {
        // Quest 1: Extract Junk Items
        Quest extractJunk = new Quest("extract_junk_a", "Junk Collector", 
                "Extract 3 junk items from raids", 500L, 100);
        extractJunk.addTask(new QuestTask("extract_junk_task", TaskType.EXTRACT_ITEMS, "junk", 3));
        questTemplates.put(extractJunk.getQuestId(), extractJunk);
        
        // Quest 2: Kill Raiders
        Quest killRaiders = new Quest("kill_raiders", "Raider Hunter", 
                "Kill 5 raiders in combat", 750L, 150);
        killRaiders.addTask(new QuestTask("kill_raiders_task", TaskType.KILL_MOBS, "raider", 5));
        questTemplates.put(killRaiders.getQuestId(), killRaiders);
        
        // Quest 3: Deposit Credits
        Quest depositCredits = new Quest("deposit_credits", "Investor", 
                "Deposit 5000 credits to the trader", 1000L, 200);
        depositCredits.addTask(new QuestTask("deposit_credits_task", TaskType.DEPOSIT_CREDITS, "credits", 5000));
        questTemplates.put(depositCredits.getQuestId(), depositCredits);
        
        logger.info("Initialized " + questTemplates.size() + " default quest templates");
    }

    /**
     * Get all available quest templates
     */
    public Collection<Quest> getAvailableQuests() {
        return new ArrayList<>(questTemplates.values());
    }

    /**
     * Get a specific quest template by ID
     */
    public Optional<Quest> getQuestTemplate(String questId) {
        return Optional.ofNullable(questTemplates.get(questId));
    }

    /**
     * Load a player's active quests from database
     */
    public void loadPlayerQuests(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        
        List<PlayerQuest> playerQuestRecords = repository.loadPlayerQuests(playerId);
        Map<String, Quest> activeQuests = new HashMap<>();
        
        for (PlayerQuest pq : playerQuestRecords) {
            Quest template = questTemplates.get(pq.questId());
            if (template == null) continue;
            
            // Create a copy of the template for this player
            Quest playerQuest = createPlayerQuestFromTemplate(template, pq);
            
            // Load task progress
            List<PlayerQuestTask> taskRecords = repository.loadPlayerQuestTasks(playerId, pq.questId());
            for (PlayerQuestTask pqt : taskRecords) {
                playerQuest.getTask(pqt.taskId()).ifPresent(task -> {
                    task.setProgress(pqt.progress());
                });
            }
            
            // Check if quest is completed
            playerQuest.checkCompletion();
            
            activeQuests.put(pq.questId(), playerQuest);
        }
        
        playerActiveQuests.put(playerId, activeQuests);
        logger.fine("Loaded " + activeQuests.size() + " quests for player " + playerId);
    }

    /**
     * Assign a new quest to a player
     */
    public boolean assignQuest(UUID playerId, String questId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(questId, "questId");
        
        // Check if player already has this quest
        if (hasActiveQuest(playerId, questId)) {
            return false;
        }
        
        Quest template = questTemplates.get(questId);
        if (template == null) {
            return false;
        }
        
        // Create new player quest record
        PlayerQuest playerQuest = PlayerQuest.createNew(playerId, questId);
        repository.savePlayerQuest(playerQuest);
        
        // Create player-specific quest instance
        Quest playerQuestInstance = createPlayerQuestFromTemplate(template, playerQuest);
        playerActiveQuests.computeIfAbsent(playerId, k -> new HashMap<>())
                .put(questId, playerQuestInstance);
        
        logger.info("Assigned quest " + questId + " to player " + playerId);
        return true;
    }

    /**
     * Check if player has an active (non-completed) quest
     */
    public boolean hasActiveQuest(UUID playerId, String questId) {
        Map<String, Quest> playerQuests = playerActiveQuests.get(playerId);
        if (playerQuests == null) return false;
        
        Quest quest = playerQuests.get(questId);
        return quest != null && !quest.isCompleted();
    }

    /**
     * Get player's active quests
     */
    public Collection<Quest> getPlayerActiveQuests(UUID playerId) {
        Map<String, Quest> playerQuests = playerActiveQuests.get(playerId);
        if (playerQuests == null) return Collections.emptyList();
        
        return playerQuests.values().stream()
                .filter(q -> !q.isCompleted())
                .collect(Collectors.toList());
    }

    /**
     * Update quest progress based on player actions
     */
    public void updateQuestProgress(UUID playerId, TaskType taskType, String target, int amount) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(taskType, "taskType");
        Objects.requireNonNull(target, "target");
        
        Map<String, Quest> playerQuests = playerActiveQuests.get(playerId);
        if (playerQuests == null) return;
        
        boolean progressUpdated = false;
        
        for (Quest quest : playerQuests.values()) {
            if (quest.isCompleted()) continue;
            
            for (QuestTask task : quest.getIncompleteTasks()) {
                if (task.getType() == taskType && 
                    (task.getTarget().equals(target) || "*".equals(target))) {
                    
                    task.addProgress(amount);
                    progressUpdated = true;
                    
                    // Save task progress
                    PlayerQuestTask pqt = PlayerQuestTask.createNew(playerId, quest.getQuestId(), task.getTaskId())
                            .withProgress(task.getProgress(), task.isCompleted());
                    repository.savePlayerQuestTask(pqt);
                    
                    // Check if quest is now complete
                    if (quest.checkCompletion()) {
                        PlayerQuest completedQuest = PlayerQuest.createNew(playerId, quest.getQuestId());
                        // Save the completed quest status
                        repository.savePlayerQuest(completedQuest);
                        
                        // Send completion notification to player
                        Player player = plugin.getServer().getPlayer(playerId);
                        if (player != null && player.isOnline()) {
                            player.sendMessage("=== Quest Completed! ===");
                            player.sendMessage("Quest: " + quest.getName());
                            player.sendMessage("Reward: " + quest.getRewardCredits() + " credits, " + quest.getRewardXp() + " XP");
                            player.sendMessage("Use /quest claim " + quest.getQuestId() + " to claim your rewards!");
                            player.sendMessage("========================");
                        }
                        
                        logger.info("Player " + playerId + " completed quest " + quest.getQuestId());
                    }
                }
            }
        }
        
        if (progressUpdated) {
            logger.fine("Updated quest progress for player " + playerId + 
                       " (type=" + taskType + ", target=" + target + ", amount=" + amount + ")");
        }
    }

    /**
     * Claim rewards for a completed quest
     */
    public boolean claimQuestRewards(UUID playerId, String questId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(questId, "questId");
        
        Map<String, Quest> playerQuests = playerActiveQuests.get(playerId);
        if (playerQuests == null) return false;
        
        Quest quest = playerQuests.get(questId);
        if (quest == null || !quest.canBeClaimed()) {
            return false;
        }
        
        // Award rewards
        profileService.addCredits(playerId, quest.getRewardCredits());
        profileService.awardExtractionXp("quest_reward", playerId, quest.getRewardXp(), 100);
        
        // Mark as claimed
        quest.claimRewards();
        repository.markQuestClaimed(playerId, questId);
        
        logger.info("Player " + playerId + " claimed rewards for quest " + questId + 
                   " (" + quest.getRewardCredits() + " credits, " + quest.getRewardXp() + " XP)");
        return true;
    }

    /**
     * Reset a completed quest to allow replaying (for repeatable quests)
     */
    public boolean resetQuest(UUID playerId, String questId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(questId, "questId");
        
        Map<String, Quest> playerQuests = playerActiveQuests.get(playerId);
        if (playerQuests == null) return false;
        
        Quest quest = playerQuests.get(questId);
        if (quest == null || !quest.isCompleted()) {
            return false;
        }
        
        repository.resetPlayerQuest(playerId, questId);
        playerQuests.remove(questId);
        
        logger.info("Reset quest " + questId + " for player " + playerId);
        return true;
    }

    private Quest createPlayerQuestFromTemplate(Quest template, PlayerQuest playerQuestRecord) {
        Quest playerQuest = new Quest(
                template.getQuestId(),
                template.getName(),
                template.getDescription(),
                template.getRewardCredits(),
                template.getRewardXp()
        );
        
        // Copy tasks
        for (QuestTask templateTask : template.getTasks()) {
            QuestTask playerTask = new QuestTask(
                    templateTask.getTaskId(),
                    templateTask.getType(),
                    templateTask.getTarget(),
                    templateTask.getRequiredAmount()
            );
            playerQuest.addTask(playerTask);
        }
        
        // Apply completion status
        if (playerQuestRecord.completed()) {
            playerQuest.checkCompletion(); // This will mark it as completed
        }
        
        return playerQuest;
    }
}