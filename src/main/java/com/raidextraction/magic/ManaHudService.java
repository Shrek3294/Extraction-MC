package com.raidextraction.magic;

import com.raidextraction.item.CustomItemType;
import com.raidextraction.item.ItemKeys;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Displays mana using vanilla HUD: XP bar = mana percent, XP level = current mana.
 * Restores real XP when player is not holding a custom weapon.
 */
public final class ManaHudService {
    private final JavaPlugin plugin;
    private final ItemKeys itemKeys;
    private final PlayerManaService manaService;

    private final Map<UUID, XpSnapshot> snapshots = new HashMap<>();
    private BukkitTask task;

    public ManaHudService(JavaPlugin plugin, ItemKeys itemKeys, PlayerManaService manaService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.itemKeys = Objects.requireNonNull(itemKeys, "itemKeys");
        this.manaService = Objects.requireNonNull(manaService, "manaService");
    }

    public void start() {
        if (task != null) {
            task.cancel();
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            restore(player);
        }
        snapshots.clear();
    }

    private void tick() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (shouldShowManaHud(player)) {
                show(player);
            } else {
                restore(player);
            }
        }
    }

    private boolean shouldShowManaHud(Player player) {
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        String type = meta.getPersistentDataContainer().get(itemKeys.itemType(), PersistentDataType.STRING);
        return CustomItemType.WEAPON.name().equals(type);
    }

    private void show(Player player) {
        UUID id = player.getUniqueId();
        snapshots.computeIfAbsent(id, ignored -> new XpSnapshot(player.getLevel(), player.getExp(), player.getTotalExperience()));

        int mana = manaService.get(player);
        int max = Math.max(1, manaService.getMax(player));
        float progress = Math.max(0f, Math.min(1f, (float) mana / (float) max));

        player.setLevel(mana);
        player.setExp(progress);
        player.setTotalExperience(0);
    }

    private void restore(Player player) {
        XpSnapshot snapshot = snapshots.remove(player.getUniqueId());
        if (snapshot == null) {
            return;
        }
        player.setLevel(snapshot.level());
        player.setExp(snapshot.exp());
        player.setTotalExperience(snapshot.totalExp());
    }

    private record XpSnapshot(int level, float exp, int totalExp) {
    }
}

