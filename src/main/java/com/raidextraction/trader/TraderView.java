package com.raidextraction.trader;

import com.raidextraction.profile.PlayerProfile;
import com.raidextraction.profile.PlayerProfileRepository;
import com.raidextraction.profile.PlayerProfileService;
import com.raidextraction.quest.QuestView;
import com.raidextraction.quest.QuestManager;
import com.raidextraction.quest.Quest;
import com.raidextraction.quest.QuestTask;
import com.raidextraction.quest.TaskType;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class TraderView implements Listener {
    private static final int SIZE = 27;
    private static final int SELL_SLOT = 11;
    private static final int BUY_SLOT = 15;
    private static final int QUEST_TURN_IN_SLOT = 13; // Center slot for quest turn-in
    private static final int INFO_SLOT = 22;
    private static final String TITLE = "Trader";

    private final JavaPlugin plugin;
    private final TraderService traderService;
    private final PlayerProfileService profileService;
    private final Logger logger;
    private QuestView questView; // Reference to quest view for integration
    private QuestManager questManager; // Reference to quest manager for validation
    private final Map<UUID, TraderSession> sessions = new HashMap<>();
    private final Map<UUID, Boolean> busy = new ConcurrentHashMap<>();
    private final Map<UUID, QuestTurnInSession> questTurnInSessions = new ConcurrentHashMap<>();

    public TraderView(JavaPlugin plugin, TraderService traderService, PlayerProfileService profileService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.traderService = Objects.requireNonNull(traderService, "traderService");
        this.profileService = Objects.requireNonNull(profileService, "profileService");
        this.logger = plugin.getLogger();
    }

    public void setQuestView(QuestView questView) {
        this.questView = questView;
    }

    public void setQuestManager(QuestManager questManager) {
        this.questManager = questManager;
    }

    public void open(Player player) {
        Objects.requireNonNull(player, "player");
        if (!traderService.enabled()) {
            player.sendMessage("Trader is disabled.");
            return;
        }
        UUID playerId = player.getUniqueId();
        Inventory inventory = plugin.getServer().createInventory(new TraderMenuHolder(playerId), SIZE, Component.text(TITLE));
        sessions.put(playerId, new TraderSession(playerId, inventory));
        render(player, inventory);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!traderService.enabled()) {
            return;
        }
        Entity entity = event.getRightClicked();
        if (!traderService.isTraderNpc(entity)) {
            return;
        }
        event.setCancelled(true);
        open(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        
        // Handle trader menu clicks
        if (event.getView().getTopInventory().getHolder() instanceof TraderMenuHolder traderHolder) {
            if (!traderHolder.playerId().equals(player.getUniqueId())) {
                return;
            }
            event.setCancelled(true);

            TraderSession session = sessions.get(player.getUniqueId());
            if (session == null) {
                return;
            }

            int rawSlot = event.getRawSlot();
            if (rawSlot < 0 || rawSlot >= SIZE) {
                return;
            }

            if (rawSlot == SELL_SLOT) {
                handleSell(player, session);
            } else if (rawSlot == BUY_SLOT) {
                handleBuy(player, session);
            } else if (rawSlot == QUEST_TURN_IN_SLOT) {
                handleQuestTurnIn(player, session);
            }
        }
        // Handle quest turn-in inventory clicks
        else if (event.getView().getTopInventory().getHolder() instanceof QuestTurnInHolder questHolder) {
            if (!questHolder.playerId().equals(player.getUniqueId())) {
                return;
            }
            // Allow item placement but cancel taking items out
            if (event.getRawSlot() < event.getView().getTopInventory().getSize()) {
                event.setCancelled(false); // Allow placing items
            } else {
                event.setCancelled(true); // Prevent taking items from player inventory
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof TraderMenuHolder holder) {
            sessions.remove(holder.playerId());
            busy.remove(holder.playerId());
        } else if (event.getInventory().getHolder() instanceof QuestTurnInHolder questHolder) {
            if (event.getPlayer() instanceof Player player) {
                handleQuestTurnInSubmission(player, event.getInventory());
            }
            questTurnInSessions.remove(questHolder.playerId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        sessions.remove(playerId);
        busy.remove(playerId);
        questTurnInSessions.remove(playerId);
    }

    private void handleSell(Player player, TraderSession session) {
        if (!setBusy(player.getUniqueId(), true)) {
            return;
        }
        TraderService.SellPreview preview = traderService.previewSellAll(player);
        if (preview.credits() <= 0) {
            player.sendMessage("You have nothing sellable.");
            setBusy(player.getUniqueId(), false);
            return;
        }

        TraderService.SellResult sold = traderService.sellAll(player);
        if (sold.credits() <= 0 || sold.removedItems().isEmpty()) {
            player.sendMessage("You have nothing sellable.");
            setBusy(player.getUniqueId(), false);
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerProfile updated;
            try {
                updated = profileService.addCredits(player.getUniqueId(), sold.credits());
            } catch (Exception error) {
                logger.log(Level.WARNING, "Failed to save trader credits for " + player.getUniqueId(), error);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        traderService.restoreItems(player, sold.removedItems());
                        player.sendMessage("Trade failed; restored your items.");
                        render(player, session.inventory());
                    }
                    setBusy(player.getUniqueId(), false);
                });
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage("Sold " + sold.stacks() + " stack(s) for " + sold.credits() + " credits.");
                    render(player, session.inventory(), updated);
                }
                setBusy(player.getUniqueId(), false);
            });
        });
    }

    private void handleBuy(Player player, TraderSession session) {
        if (!setBusy(player.getUniqueId(), true)) {
            return;
        }
        String kitId = traderService.defaultKitId().orElse(null);
        TraderService.KitDefinition kit = kitId != null ? traderService.kit(kitId).orElse(null) : null;
        if (kitId == null || kit == null) {
            player.sendMessage("No kits are configured.");
            setBusy(player.getUniqueId(), false);
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerProfileRepository.SpendResult spendResult;
            try {
                spendResult = profileService.trySpendCredits(player.getUniqueId(), kit.price());
            } catch (Exception error) {
                logger.log(Level.WARNING, "Failed to spend credits for kit purchase: " + player.getUniqueId(), error);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendMessage("Purchase failed. Try again shortly.");
                    }
                    setBusy(player.getUniqueId(), false);
                });
                return;
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    setBusy(player.getUniqueId(), false);
                    return;
                }
                if (!spendResult.spent()) {
                    player.sendMessage("Not enough credits (" + kit.price() + " needed).");
                    render(player, session.inventory(), spendResult.profile());
                    setBusy(player.getUniqueId(), false);
                    return;
                }
                List<ItemStack> items = traderService.createKitStacks(kitId);
                try {
                    for (ItemStack stack : items) {
                        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
                        leftovers.values()
                                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
                    }
                    player.updateInventory();
                    player.sendMessage("Purchased kit '" + kitId + "' for " + kit.price() + " credits.");
                    render(player, session.inventory(), spendResult.profile());
                } catch (Exception giveError) {
                    logger.log(Level.WARNING, "Kit grant failed; refunding credits: " + player.getUniqueId(), giveError);
                    plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                            () -> profileService.addCredits(player.getUniqueId(), kit.price()));
                    player.sendMessage("Purchase failed; credits refunded.");
                } finally {
                    setBusy(player.getUniqueId(), false);
                }
            });
        });
    }

    private boolean setBusy(UUID playerId, boolean value) {
        if (!value) {
            busy.remove(playerId);
            return true;
        }
        return busy.putIfAbsent(playerId, true) == null;
    }

    private void render(Player player, Inventory inventory) {
        PlayerProfile profile = profileService.cached(player.getUniqueId()).orElse(null);
        render(player, inventory, profile);
    }

    private void render(Player player, Inventory inventory, PlayerProfile profile) {
        inventory.clear();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler());
        }

        ItemStack sell = button(Material.EMERALD, "Sell Loot");
        ItemStack buy = createBuyButton();
        ItemStack questTurnIn = createQuestTurnInButton();
        ItemStack info = createInfoItem(profile);

        inventory.setItem(SELL_SLOT, sell);
        inventory.setItem(BUY_SLOT, buy);
        inventory.setItem(QUEST_TURN_IN_SLOT, questTurnIn);
        inventory.setItem(INFO_SLOT, info);
    }

    private ItemStack createBuyButton() {
        String kitId = traderService.defaultKitId().orElse(null);
        TraderService.KitDefinition kit = kitId != null ? traderService.kit(kitId).orElse(null) : null;
        String label = kit == null ? "Buy Kit" : ("Buy Kit (" + kit.price() + ")");
        return button(Material.CHEST, label);
    }

    private ItemStack createInfoItem(PlayerProfile profile) {
        long credits = profile != null ? profile.credits() : 0L;
        int level = profile != null ? profile.level() : 1;
        ItemStack item = ItemStack.of(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(traderService.npcName() + " · Credits " + credits + " · Level " + level));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack filler() {
        ItemStack item = ItemStack.of(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack button(Material material, String name) {
        ItemStack item = ItemStack.of(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createQuestTurnInButton() {
        ItemStack item = ItemStack.of(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Quest Turn-In")); 
            List<Component> lore = Arrays.asList(
                Component.text("Click to turn in quest items"),
                Component.text("Accepts: Credits, Scrap, Materials"),
                Component.text("Right-click to view active quests")
            );
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void handleQuestTurnIn(Player player, TraderSession session) {
        // Check if shift-click for quest viewing
        if (player.isSneaking() && questView != null) {
            questView.handleQuestButtonClick(player, session.inventory());
            return;
        }
        
        // Show turn-in interface
        openQuestTurnInInterface(player);
    }

    private void handleQuestTurnInSubmission(Player player, Inventory inventory) {
        if (questManager == null) return;
        
        UUID playerId = player.getUniqueId();
        Collection<Quest> activeQuests = questManager.getPlayerActiveQuests(playerId);
        
        if (activeQuests.isEmpty()) {
            player.sendMessage("No active quests to submit items for.");
            return;
        }
        
        // Track items that were processed for quests vs items to return
        Set<ItemStack> processedItems = new HashSet<>();
        List<ItemStack> itemsToReturn = new java.util.ArrayList<>();
        
        // Process items in the submission inventory (slots 9-17)
        for (int i = 9; i < 18; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            
            boolean itemProcessed = false;
            
            // Check each active quest for matching requirements
            for (Quest quest : activeQuests) {
                if (quest.isCompleted()) continue;
                
                for (QuestTask task : quest.getIncompleteTasks()) {
                    if (processQuestItem(playerId, item, task)) {
                        processedItems.add(item.clone());
                        itemProcessed = true;
                        break;
                    }
                }
                if (itemProcessed) break;
            }
            
            // If item wasn't processed for any quest, mark for return
            if (!itemProcessed) {
                itemsToReturn.add(item.clone());
            }
        }
        
        // Return unprocessed items to player
        if (!itemsToReturn.isEmpty()) {
            for (ItemStack item : itemsToReturn) {
                Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
                // Drop any items that won't fit
                leftovers.values().forEach(leftover -> 
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            }
            player.sendMessage("Returned " + itemsToReturn.size() + " non-quest items to your inventory.");
        }
        
        // Check for completed quests
        Collection<Quest> questsAfter = questManager.getPlayerActiveQuests(playerId);
        Collection<Quest> completedQuests = activeQuests.stream()
                .filter(quest -> quest.isCompleted() && !questsAfter.contains(quest))
                .toList();
        
        // Notify player of successful submissions
        if (!processedItems.isEmpty()) {
            player.sendMessage("Submitted items for quest completion!");
            
            if (!completedQuests.isEmpty()) {
                player.sendMessage("=== Quests Completed! ===");
                completedQuests.forEach(quest -> {
                    player.sendMessage(quest.getName() + " - Ready to claim!");
                    player.sendMessage("Reward: " + quest.getRewardCredits() + " credits, " + quest.getRewardXp() + " XP");
                    player.sendMessage("Use: /quest claim " + quest.getQuestId());
                });
                player.sendMessage("=========================");
            }
            
            player.sendMessage("Check your progress with /quest list");
        }
        
        // Update player inventory
        player.updateInventory();
    }
    
    private boolean processQuestItem(UUID playerId, ItemStack item, QuestTask task) {
        TaskType taskType = task.getType();
        String target = task.getTarget();
        int requiredAmount = task.getRequiredAmount() - task.getProgress();
        
        if (requiredAmount <= 0) return false;
        
        int itemCount = item.getAmount();
        int itemsToProcess = Math.min(itemCount, requiredAmount);
        
        // Process based on task type
        switch (taskType) {
            case EXTRACT_ITEMS:
                // For item extraction quests, we need to match categories
                // This would require access to loot pricing service to determine item categories
                // For now, we'll treat any non-air item as potentially valid
                // In a full implementation, you'd check the item's category against the task target
                if (!"*".equals(target)) {
                    // Specific category targeting would go here
                    // For demo purposes, accept any item
                }
                break;
                
            case KILL_MOBS:
                // Mob kill quests can't be satisfied by items
                return false;
                
            case DEPOSIT_CREDITS:
                // Credit deposit quests can't be satisfied by items
                return false;
                
            default:
                return false;
        }
        
        // Update quest progress
        questManager.updateQuestProgress(playerId, taskType, target, itemsToProcess);
        
        // Reduce item stack size
        if (itemsToProcess >= itemCount) {
            item.setAmount(0); // Remove the item
        } else {
            item.setAmount(itemCount - itemsToProcess);
        }
        
        return true;
    }

    private void openQuestTurnInInterface(Player player) {
        if (questManager == null) {
            player.sendMessage("Quest system not available.");
            return;
        }
        
        UUID playerId = player.getUniqueId();
        Collection<Quest> activeQuests = questManager.getPlayerActiveQuests(playerId);
        
        if (activeQuests.isEmpty()) {
            player.sendMessage("You don't have any active quests. Check available quests with /quest list");
            return;
        }
        
        // Create quest turn-in inventory (chest size 27)
        Inventory inventory = plugin.getServer().createInventory(
            new QuestTurnInHolder(playerId), 
            27, 
            Component.text("Quest Item Submission")
        );
        
        // Fill with glass panes for visual separation
        ItemStack filler = ItemStack.of(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.displayName(Component.text(" "));
            filler.setItemMeta(fillerMeta);
        }
        
        for (int i = 0; i < 27; i++) {
            if (i < 9 || i >= 18) { // Top and bottom rows
                inventory.setItem(i, filler);
            }
        }
        
        // Add quest information
        ItemStack infoItem = ItemStack.of(Material.PAPER);
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            infoMeta.displayName(Component.text("Active Quests"));
            List<Component> infoLore = activeQuests.stream()
                .limit(5) // Show max 5 quests
                .map(quest -> Component.text("• " + quest.getName()))
                .collect(java.util.stream.Collectors.toList());
            infoLore.add(Component.text(""));
            infoLore.add(Component.text("Place quest items in the slots below"));
            infoLore.add(Component.text("Close inventory to submit"));
            infoMeta.lore(infoLore);
            infoItem.setItemMeta(infoMeta);
        }
        inventory.setItem(4, infoItem);
        
        // Store session
        questTurnInSessions.put(playerId, new QuestTurnInSession(playerId, inventory));
        
        // Open inventory
        player.openInventory(inventory);
    }
    

    private record TraderSession(UUID playerId, Inventory inventory) {
    }

    private record TraderMenuHolder(UUID playerId) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record QuestTurnInSession(UUID playerId, Inventory inventory) {
    }

    private record QuestTurnInHolder(UUID playerId) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
