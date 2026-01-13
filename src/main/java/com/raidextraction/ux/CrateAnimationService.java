package com.raidextraction.ux;

import com.raidextraction.integration.ItemDataMapper;
import com.raidextraction.stash.ItemData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Objects;
import java.util.Random;

public class CrateAnimationService {

    private final JavaPlugin plugin;
    private final ItemDataMapper mapper;
    private final Random random = new Random();

    public CrateAnimationService(JavaPlugin plugin, ItemDataMapper mapper) {
        this.plugin = Objects.requireNonNull(plugin);
        this.mapper = Objects.requireNonNull(mapper);
    }

    public void startAnimation(Player player, List<ItemData> loot, Runnable onReveal) {
        Inventory inventory = Bukkit.createInventory(new CrateHolder(), 27,
                Component.text("Decrypting Crate...", NamedTextColor.DARK_AQUA));
        player.openInventory(inventory);

        // Start animation task
        new CrateAnimationTask(player, inventory, loot, onReveal).runTaskTimer(plugin, 1L, 2L);
    }

    // Holder to identify our custom inventory
    public static class CrateHolder implements InventoryHolder {
        private boolean animating = true;

        public boolean isAnimating() {
            return animating;
        }

        public void setAnimating(boolean animating) {
            this.animating = animating;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private class CrateAnimationTask extends BukkitRunnable {
        private final Player player;
        private final Inventory inventory;
        private final List<ItemData> finalLoot;
        private final Runnable onReveal;
        private int ticks = 0;
        private final int durationTicks = 40; // 2 seconds of rolling

        public CrateAnimationTask(Player player, Inventory inventory, List<ItemData> finalLoot, Runnable onReveal) {
            this.player = player;
            this.inventory = inventory;
            this.finalLoot = finalLoot;
            this.onReveal = onReveal;
        }

        @Override
        public void run() {
            // If player closed inventory, cancel
            if (player.getOpenInventory().getTopInventory() != inventory) {
                this.cancel();
                return;
            }

            if (ticks < durationTicks) {
                // Rolling phase
                if (ticks % 2 == 0) {
                    fillWithRandomGlass(inventory);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.5f);
                }
            } else {
                // Reveal phase
                revealLoot();
                this.cancel();
            }
            ticks += 2; // Scheduled every 2 ticks
        }

        private void revealLoot() {
            if (inventory.getHolder() instanceof CrateHolder holder) {
                holder.setAnimating(false);
            }
            inventory.clear();
            // Call the callback to confirm loot is being taken from pending
            onReveal.run();

            // Populate inventory
            for (ItemData item : finalLoot) {
                ItemStack stack = mapper.toItemStack(item);
                inventory.addItem(stack);
            }

            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.2f);
            player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 0.8f);

            // Note: Listener will handle "Close" event to give items to player
        }

        private void fillWithRandomGlass(Inventory inv) {
            Material[] glasses = {
                    Material.RED_STAINED_GLASS_PANE,
                    Material.LIME_STAINED_GLASS_PANE,
                    Material.BLUE_STAINED_GLASS_PANE,
                    Material.YELLOW_STAINED_GLASS_PANE,
                    Material.PURPLE_STAINED_GLASS_PANE
            };

            for (int i = 0; i < 27; i++) {
                if (random.nextBoolean()) {
                    inv.setItem(i, new ItemStack(glasses[random.nextInt(glasses.length)]));
                } else {
                    inv.setItem(i, new ItemStack(Material.GRAY_STAINED_GLASS_PANE));
                }
            }
        }
    }
}
