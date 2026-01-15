package com.raidextraction.trader;

import com.raidextraction.profile.PlayerProfile;
import com.raidextraction.profile.PlayerProfileRepository;
import com.raidextraction.profile.PlayerProfileService;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class TraderView implements Listener {
    private static final int SIZE = 27;
    private static final int SELL_SLOT = 11;
    private static final int BUY_SLOT = 15;
    private static final int INFO_SLOT = 22;
    private static final String TITLE = "Trader";

    private final JavaPlugin plugin;
    private final TraderService traderService;
    private final PlayerProfileService profileService;
    private final Logger logger;
    private final Map<UUID, TraderSession> sessions = new HashMap<>();
    private final Map<UUID, Boolean> busy = new ConcurrentHashMap<>();

    public TraderView(JavaPlugin plugin, TraderService traderService, PlayerProfileService profileService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.traderService = Objects.requireNonNull(traderService, "traderService");
        this.profileService = Objects.requireNonNull(profileService, "profileService");
        this.logger = plugin.getLogger();
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
        if (!(event.getView().getTopInventory().getHolder() instanceof TraderMenuHolder holder)) {
            return;
        }
        if (!holder.playerId().equals(player.getUniqueId())) {
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
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof TraderMenuHolder holder) {
            sessions.remove(holder.playerId());
            busy.remove(holder.playerId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        sessions.remove(playerId);
        busy.remove(playerId);
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
        ItemStack info = createInfoItem(profile);

        inventory.setItem(SELL_SLOT, sell);
        inventory.setItem(BUY_SLOT, buy);
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

    private record TraderSession(UUID playerId, Inventory inventory) {
    }

    private record TraderMenuHolder(UUID playerId) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
