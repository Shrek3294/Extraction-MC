package com.raidextraction.integration.paper;

import com.raidextraction.integration.ItemDataMapper;
import com.raidextraction.stash.ItemData;
import com.raidextraction.stash.StashService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple chest-style GUI for browsing stash contents. Click items in the stash to withdraw;
 * shift-click items from your inventory to deposit into the stash.
 */
public final class StashView implements Listener {
    private static final int INVENTORY_SIZE = 54;
    private static final int PAGE_SIZE = 45;
    private static final int PREV_SLOT = 45;
    private static final int INFO_SLOT = 49;
    private static final int NEXT_SLOT = 53;
    private static final String INVENTORY_TITLE = "Raid Stash";

    private final JavaPlugin plugin;
    private final StashService stashService;
    private final ItemDataMapper itemDataMapper;
    private final Logger logger;
    private final Map<UUID, StashSession> sessions = new HashMap<>();

    public StashView(JavaPlugin plugin, StashService stashService, ItemDataMapper itemDataMapper) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.stashService = Objects.requireNonNull(stashService, "stashService");
        this.itemDataMapper = Objects.requireNonNull(itemDataMapper, "itemDataMapper");
        this.logger = plugin.getLogger();
    }

    public void open(Player player) {
        Objects.requireNonNull(player, "player");
        UUID playerId = player.getUniqueId();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<ItemData> loaded;
            try {
                loaded = stashService.load(playerId);
            } catch (Exception error) {
                logger.log(Level.WARNING, "Failed to load stash for " + playerId, error);
                plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage("Could not load your stash. Try again shortly."));
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                Inventory inventory = plugin.getServer().createInventory(player, INVENTORY_SIZE, INVENTORY_TITLE);
                StashSession session = new StashSession(playerId, inventory, new ArrayList<>(loaded),
                        stashService.capacity(playerId));
                sessions.put(playerId, session);
                render(session);
                player.openInventory(inventory);
            });
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        StashSession session = sessions.get(player.getUniqueId());
        if (session == null || event.getView().getTopInventory() != session.inventory()) {
            return;
        }
        if (event.getClickedInventory() == null) {
            return;
        }

        boolean topClick = event.getClickedInventory().equals(session.inventory());
        boolean shiftFromBottom = event.isShiftClick()
                && event.getClickedInventory().equals(event.getView().getBottomInventory());
        if (topClick || shiftFromBottom) {
            event.setCancelled(true);
        }
        if (shiftFromBottom) {
            if (session.busy()) {
                player.sendMessage("Stash is syncing; please wait a moment.");
                return;
            }
            ItemStack current = event.getCurrentItem();
            if (current == null || current.getType().isAir() || current.getAmount() <= 0) {
                return;
            }
            session.busy(true);
            ItemStack toDeposit = current.clone();
            event.setCurrentItem(null);
            player.updateInventory();
            deposit(player, session, toDeposit);
            return;
        }
        if (!topClick) {
            return;
        }

        int rawSlot = event.getRawSlot();
        if (session.busy()) {
            player.sendMessage("Stash is syncing; please wait a moment.");
            return;
        }
        if (rawSlot == PREV_SLOT) {
            changePage(session, session.page() - 1);
            return;
        }
        if (rawSlot == NEXT_SLOT) {
            changePage(session, session.page() + 1);
            return;
        }
        if (rawSlot >= PAGE_SIZE) {
            return;
        }

        int index = session.page() * PAGE_SIZE + rawSlot;
        if (index < 0 || index >= session.items().size()) {
            return;
        }
        if (session.busy()) {
            player.sendMessage("Stash is syncing; please wait a moment.");
            return;
        }
        session.busy(true);
        withdraw(player, session, session.items().get(index));
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        StashSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        boolean affectsStash = event.getInventory().equals(session.inventory())
                || event.getRawSlots().stream().anyMatch(slot -> slot < session.inventory().getSize());
        if (affectsStash) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            sessions.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    private void changePage(StashSession session, int nextPage) {
        int maxPage = Math.max((session.items().size() - 1) / PAGE_SIZE, 0);
        int clamped = Math.max(0, Math.min(nextPage, maxPage));
        session.page(clamped);
        render(session);
    }

    private void render(StashSession session) {
        Inventory inventory = session.inventory();
        inventory.clear();
        int start = session.page() * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, session.items().size());
        int slot = 0;
        for (int i = start; i < end && slot < PAGE_SIZE; i++, slot++) {
            ItemStack stack = itemDataMapper.toItemStack(session.items().get(i));
            if (stack.getType().isAir()) {
                continue;
            }
            inventory.setItem(slot, stack);
        }

        boolean hasPrev = session.page() > 0;
        boolean hasNext = end < session.items().size();
        inventory.setItem(PREV_SLOT, navigationItem(Material.ARROW, hasPrev ? "Previous" : "First page"));
        inventory.setItem(INFO_SLOT, infoItem(session.page(), Math.max((session.items().size() - 1) / PAGE_SIZE, 0),
                session.items().size(), session.capacity()));
        inventory.setItem(NEXT_SLOT, navigationItem(Material.ARROW, hasNext ? "Next" : "Last page"));
    }

    private ItemStack navigationItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack infoItem(int page, int maxPage, int totalItems, int capacity) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("Page " + (page + 1) + "/" + (maxPage + 1) + " • " + totalItems + "/"
                    + Math.max(0, capacity) + " stack(s)");
            item.setItemMeta(meta);
        }
        return item;
    }

    private void deposit(Player player, StashSession session, ItemStack stack) {
        UUID playerId = player.getUniqueId();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            StashService.AppendResult result = null;
            Exception error = null;
            try {
                ItemData itemData = itemDataMapper.toItemData(stack);
                result = stashService.addItems(playerId, List.of(itemData));
            } catch (Exception thrown) {
                error = thrown;
            }
            StashService.AppendResult finalResult = result;
            Exception finalError = error;
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> handleDepositResult(playerId, session, stack, finalResult, finalError));
        });
    }

    private void handleDepositResult(UUID playerId,
            StashSession session,
            ItemStack depositedStack,
            StashService.AppendResult result,
            Exception error) {
        try {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                restoreItemAsync(playerId, itemDataMapper.toItemData(depositedStack));
                return;
            }

            if (error != null || result == null) {
                logger.log(Level.WARNING, "Failed to deposit stash item for " + playerId, error);
                restoreToInventory(player, depositedStack);
                player.sendMessage("Could not deposit that item to your stash. Try again shortly.");
                return;
            }

            StashSession current = sessions.get(playerId);
            if (current != null && current == session) {
                session.items(new ArrayList<>(result.stash()));
                session.capacity(result.capacity());
                int maxPage = Math.max((session.items().size() - 1) / PAGE_SIZE, 0);
                if (session.page() > maxPage) {
                    session.page(maxPage);
                }
                render(session);
            }

            if (result.added().isEmpty()) {
                restoreToInventory(player, depositedStack);
                player.sendMessage("Your stash is full (" + result.stash().size() + "/" + result.capacity()
                        + "). Deposit blocked.");
                return;
            }

            player.sendMessage("Deposited " + depositedStack.getAmount() + "x " + depositedStack.getType().name()
                    + " to your stash.");
        } finally {
            session.busy(false);
        }
    }

    private void restoreToInventory(Player player, ItemStack stack) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
        leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        player.updateInventory();
    }

    private void withdraw(Player player, StashSession session, ItemData itemData) {
        UUID playerId = player.getUniqueId();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<ItemData> updatedItems;
            boolean removed = false;
            try {
                updatedItems = new ArrayList<>(stashService.load(playerId));
                int index = updatedItems.indexOf(itemData);
                if (index >= 0) {
                    updatedItems.remove(index);
                    stashService.replace(playerId, updatedItems);
                    removed = true;
                }
            } catch (Exception error) {
                logger.log(Level.WARNING, "Failed to withdraw stash item for " + playerId, error);
                updatedItems = new ArrayList<>(session.items());
            }
            boolean removedFinal = removed;
            List<ItemData> finalItems = updatedItems;
            plugin.getServer().getScheduler().runTask(plugin, () -> handleWithdrawResult(playerId, session, itemData, finalItems, removedFinal));
        });
    }

    private void handleWithdrawResult(UUID playerId, StashSession session, ItemData itemData, List<ItemData> updatedItems, boolean removed) {
        try {
            StashSession current = sessions.get(playerId);
            if (current != null && current == session) {
                session.items(new ArrayList<>(updatedItems));
                int maxPage = Math.max((session.items().size() - 1) / PAGE_SIZE, 0);
                if (session.page() > maxPage) {
                    session.page(maxPage);
                }
                render(session);
            }
            if (!removed) {
                Player player = plugin.getServer().getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    player.sendMessage("That stash slot is no longer available. Refreshed your stash.");
                }
                return;
            }

            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                restoreItemAsync(playerId, itemData);
                return;
            }

            ItemStack stack = itemDataMapper.toItemStack(itemData);
            if (stack.getType().isAir()) {
                player.sendMessage("Could not deliver that stash item because it is invalid.");
                return;
            }
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
            leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            player.updateInventory();
            player.sendMessage("Withdrew " + stack.getAmount() + "x " + stack.getType().name() + " from your stash.");
        } finally {
            session.busy(false);
        }
    }

    private void restoreItemAsync(UUID playerId, ItemData itemData) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                stashService.forceAddItems(playerId, List.of(itemData));
            } catch (Exception error) {
                logger.log(Level.WARNING, "Failed to restore stash item for offline player " + playerId, error);
            }
        });
    }

    private static final class StashSession {
        private final UUID playerId;
        private final Inventory inventory;
        private List<ItemData> items;
        private int page;
        private boolean busy;
        private int capacity;

        private StashSession(UUID playerId, Inventory inventory, List<ItemData> items, int capacity) {
            this.playerId = playerId;
            this.inventory = inventory;
            this.items = items;
            this.page = 0;
            this.busy = false;
            this.capacity = capacity;
        }

        private UUID playerId() {
            return playerId;
        }

        private Inventory inventory() {
            return inventory;
        }

        private List<ItemData> items() {
            return items;
        }

        private void items(List<ItemData> items) {
            this.items = items;
        }

        private int page() {
            return page;
        }

        private void page(int page) {
            this.page = page;
        }

        private boolean busy() {
            return busy;
        }

        private void busy(boolean busy) {
            this.busy = busy;
        }

        private int capacity() {
            return capacity;
        }

        private void capacity(int capacity) {
            this.capacity = capacity;
        }
    }
}
