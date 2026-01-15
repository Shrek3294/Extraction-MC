package com.raidextraction.quest;

import com.raidextraction.profile.PlayerProfile;
import com.raidextraction.profile.PlayerProfileService;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

/**
 * View component for displaying and managing quests in the trader interface.
 */
public class QuestView {
    private static final int QUEST_SLOT = 13; // Center slot for quests
    
    private final JavaPlugin plugin;
    private final QuestManager questManager;
    private final PlayerProfileService profileService;

    public QuestView(JavaPlugin plugin, QuestManager questManager, PlayerProfileService profileService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.questManager = Objects.requireNonNull(questManager, "questManager");
        this.profileService = Objects.requireNonNull(profileService, "profileService");
    }

    /**
     * Create the quest display item for the trader menu
     */
    public ItemStack createQuestButtonItem(Player player) {
        UUID playerId = player.getUniqueId();
        Collection<Quest> activeQuests = questManager.getPlayerActiveQuests(playerId);
        
        Material material = Material.WRITABLE_BOOK;
        String displayName;
        
        if (activeQuests.isEmpty()) {
            displayName = "Available Quests";
        } else {
            long claimableCount = activeQuests.stream()
                    .filter(Quest::canBeClaimed)
                    .count();
            
            if (claimableCount > 0) {
                material = Material.ENCHANTED_BOOK;
                displayName = "Quests (" + claimableCount + " Ready to Claim!)";
            } else {
                long completedCount = activeQuests.stream()
                        .filter(Quest::isCompleted)
                        .count();
                long inProgressCount = activeQuests.size() - completedCount;
                
                if (inProgressCount > 0) {
                    displayName = "Quests (" + inProgressCount + " In Progress)";
                } else {
                    displayName = "Quests (" + completedCount + " Completed)";
                }
            }
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(displayName));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Handle quest button click in the trader interface
     */
    public void handleQuestButtonClick(Player player, Inventory traderInventory) {
        UUID playerId = player.getUniqueId();
        
        // Open quest selection/management interface
        openQuestInterface(player);
    }

    /**
     * Open the quest management interface
     */
    private void openQuestInterface(Player player) {
        UUID playerId = player.getUniqueId();
        Collection<Quest> activeQuests = questManager.getPlayerActiveQuests(playerId);
        
        // For simplicity, we'll show a chat-based interface
        // In a full implementation, this would open a proper GUI
        
        player.sendMessage("=== Your Quests ===");
        
        if (activeQuests.isEmpty()) {
            player.sendMessage("No active quests! Available quests:");
            questManager.getAvailableQuests().forEach(quest -> {
                player.sendMessage("/quest accept " + quest.getQuestId() + " - " + quest.getName());
            });
        } else {
            activeQuests.forEach(quest -> {
                if (quest.canBeClaimed()) {
                    player.sendMessage("[CLAIM READY] " + quest.getName() + " - /quest claim " + quest.getQuestId());
                } else if (quest.isCompleted()) {
                    player.sendMessage("[COMPLETED] " + quest.getName());
                } else {
                    String progress = String.format("%.1f%%", quest.getOverallProgress() * 100);
                    player.sendMessage("[IN PROGRESS] " + quest.getName() + " (" + progress + ")");
                    quest.getIncompleteTasks().forEach(task -> {
                        player.sendMessage("  • " + task.getTarget() + ": " + task.getProgress() + "/" + task.getRequiredAmount());
                    });
                }
            });
        }
        
        player.sendMessage("==================");
    }

    /**
     * Handle quest acceptance command
     */
    public boolean acceptQuest(Player player, String questId) {
        UUID playerId = player.getUniqueId();
        
        if (questManager.hasActiveQuest(playerId, questId)) {
            player.sendMessage("You already have this quest!");
            return false;
        }
        
        boolean assigned = questManager.assignQuest(playerId, questId);
        if (assigned) {
            questManager.getQuestTemplate(questId).ifPresent(quest -> {
                player.sendMessage("Accepted quest: " + quest.getName());
                player.sendMessage(quest.getDescription());
            });
        } else {
            player.sendMessage("Could not accept that quest.");
        }
        
        return assigned;
    }

    /**
     * Handle quest claiming command
     */
    public boolean claimQuest(Player player, String questId) {
        UUID playerId = player.getUniqueId();
        
        boolean claimed = questManager.claimQuestRewards(playerId, questId);
        if (claimed) {
            questManager.getQuestTemplate(questId).ifPresent(quest -> {
                player.sendMessage("Claimed rewards for: " + quest.getName());
                player.sendMessage("+" + quest.getRewardCredits() + " credits, +" + quest.getRewardXp() + " XP");
            });
        } else {
            player.sendMessage("Could not claim that quest.");
        }
        
        return claimed;
    }

    /**
     * Add quest item to trader inventory
     */
    public void addToTraderMenu(Player player, Inventory traderInventory) {
        ItemStack questItem = createQuestButtonItem(player);
        traderInventory.setItem(QUEST_SLOT, questItem);
    }
}