package com.raidextraction.ux;

import com.raidextraction.item.CustomItemType;
import com.raidextraction.item.ItemKeys;
import com.raidextraction.item.WeaponUpgradeService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class WeaponBenchView implements Listener {
    private static final int SIZE = 27;
    private static final int WEAPON_SLOT = 11;
    private static final int MOD_SLOT = 15;
    private static final int APPLY_SLOT = 22;

    private final JavaPlugin plugin;
    private final ItemKeys itemKeys;
    private final WeaponUpgradeService upgradeService;

    public WeaponBenchView(JavaPlugin plugin, ItemKeys itemKeys, WeaponUpgradeService upgradeService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.itemKeys = Objects.requireNonNull(itemKeys, "itemKeys");
        this.upgradeService = Objects.requireNonNull(upgradeService, "upgradeService");
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new BenchHolder(), SIZE,
                Component.text("Weapon Bench", NamedTextColor.DARK_AQUA));
        fill(inv);
        player.openInventory(inv);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BenchHolder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int raw = event.getRawSlot();
        boolean top = raw < event.getView().getTopInventory().getSize();

        if (!top) {
            if (event.isShiftClick()) {
                ItemStack current = event.getCurrentItem();
                if (current == null || current.getType().isAir()) {
                    return;
                }
                Inventory topInv = event.getView().getTopInventory();
                if (isWeapon(current) && isEmpty(topInv.getItem(WEAPON_SLOT))) {
                    topInv.setItem(WEAPON_SLOT, current.clone());
                    event.getClickedInventory().setItem(event.getSlot(), null);
                    event.setCancelled(true);
                    return;
                }
                if (isMod(current) && isEmpty(topInv.getItem(MOD_SLOT))) {
                    topInv.setItem(MOD_SLOT, current.clone());
                    event.getClickedInventory().setItem(event.getSlot(), null);
                    event.setCancelled(true);
                    return;
                }
                event.setCancelled(true);
            }
            return;
        }

        if (raw == APPLY_SLOT) {
            event.setCancelled(true);
            apply(player, event.getView().getTopInventory());
            return;
        }

        if (raw != WEAPON_SLOT && raw != MOD_SLOT) {
            event.setCancelled(true);
            return;
        }

        if (event.isShiftClick()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof BenchHolder)) {
            return;
        }
        for (int raw : event.getRawSlots()) {
            if (raw < SIZE && raw != WEAPON_SLOT && raw != MOD_SLOT) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof BenchHolder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        returnItem(player, event.getInventory().getItem(WEAPON_SLOT));
        returnItem(player, event.getInventory().getItem(MOD_SLOT));
        event.getInventory().clear();
    }

    private void apply(Player player, Inventory inv) {
        ItemStack weapon = inv.getItem(WEAPON_SLOT);
        ItemStack mod = inv.getItem(MOD_SLOT);
        if (weapon == null || weapon.getType().isAir() || mod == null || mod.getType().isAir()) {
            player.sendActionBar(Component.text("Place a weapon + mod first.", NamedTextColor.GRAY));
            return;
        }
        var outcome = upgradeService.applyMod(player, weapon, mod);
        var result = outcome.result();
        if (!result.ok()) {
            player.sendActionBar(Component.text(result.message(), NamedTextColor.RED));
            return;
        }
        ItemStack updatedWeapon = outcome.weaponStack();
        ItemStack updatedMod = outcome.modStack();
        inv.setItem(WEAPON_SLOT, updatedWeapon);
        inv.setItem(MOD_SLOT, updatedMod == null || updatedMod.getType().isAir() ? null : updatedMod);
    }

    private void fill(Inventory inv) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, pane);
        }
        inv.setItem(WEAPON_SLOT, null);
        inv.setItem(MOD_SLOT, null);
        inv.setItem(APPLY_SLOT, button(Material.ANVIL, "Apply Mod"));
        inv.setItem(WEAPON_SLOT - 1, button(Material.IRON_SWORD, "Weapon"));
        inv.setItem(MOD_SLOT + 1, button(Material.AMETHYST_SHARD, "Mod"));
    }

    private ItemStack button(Material material, String label) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(label, NamedTextColor.AQUA));
        stack.setItemMeta(meta);
        return stack;
    }

    private void returnItem(Player player, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        var leftovers = player.getInventory().addItem(stack);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItem(player.getLocation(), leftover);
        }
    }

    private boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir();
    }

    private boolean isWeapon(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        String type = meta.getPersistentDataContainer().get(itemKeys.itemType(), PersistentDataType.STRING);
        return CustomItemType.WEAPON.name().equals(type);
    }

    private boolean isMod(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        String type = meta.getPersistentDataContainer().get(itemKeys.itemType(), PersistentDataType.STRING);
        return CustomItemType.MOD.name().equals(type);
    }

    private static final class BenchHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
