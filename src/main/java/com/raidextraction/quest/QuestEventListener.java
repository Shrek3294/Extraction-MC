package com.raidextraction.quest;

import com.raidextraction.item.ItemKeys;
import com.raidextraction.loot.LootPricingService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;

/**
 * Event listener that tracks player actions and updates quest progress accordingly.
 */
public class QuestEventListener implements Listener {
    private final JavaPlugin plugin;
    private final QuestManager questManager;
    private final LootPricingService lootPricingService;
    private final ItemKeys itemKeys;

    public QuestEventListener(JavaPlugin plugin, QuestManager questManager, 
                             LootPricingService lootPricingService, ItemKeys itemKeys) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.questManager = Objects.requireNonNull(questManager, "questManager");
        this.lootPricingService = Objects.requireNonNull(lootPricingService, "lootPricingService");
        this.itemKeys = Objects.requireNonNull(itemKeys, "itemKeys");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // Load player's quests when they join
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            questManager.loadPlayerQuests(playerId);
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Could save any pending progress here if needed
        // For now, relying on automatic saves in QuestManager
    }

    // NOTE: Extraction completion is tracked through RaidLifecycleCoordinator.handleExtractionSuccess()
    // Quest progress for extractions should be updated there or through direct integration

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() instanceof Player player) {
            UUID playerId = player.getUniqueId();
            String mobType = event.getEntity().getType().name().toLowerCase();
            
            // Update kill quests
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                questManager.updateQuestProgress(playerId, TaskType.KILL_MOBS, mobType, 1);
            });
        }
    }

    /**
     * Method to be called when items are extracted to update extract item quests
     */
    public void onItemsExtracted(Player player, ItemStack[] extractedItems) {
        UUID playerId = player.getUniqueId();
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            // Count items by category for quest progress
            for (ItemStack item : extractedItems) {
                if (item == null || item.getAmount() <= 0) continue;
                
                lootPricingService.lookup(item, itemKeys).ifPresent(value -> {
                    String category = value.category().toLowerCase();
                    questManager.updateQuestProgress(playerId, TaskType.EXTRACT_ITEMS, category, item.getAmount());
                });
            }
        });
    }

    /**
     * Method to be called when credits are deposited to update deposit quests
     */
    public void onCreditsDeposited(Player player, long credits) {
        UUID playerId = player.getUniqueId();
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            questManager.updateQuestProgress(playerId, TaskType.DEPOSIT_CREDITS, "credits", (int) credits);
        });
    }
}