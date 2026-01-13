package com.raidextraction.magic;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;

public final class PlayerManaService implements Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey manaKey;
    private final NamespacedKey manaMaxKey;
    private final int defaultMax;
    private final int regenPerSecond;
    private BukkitTask regenTask;

    public PlayerManaService(JavaPlugin plugin, int defaultMax, int regenPerSecond) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.manaKey = new NamespacedKey(plugin, "mana");
        this.manaMaxKey = new NamespacedKey(plugin, "mana_max");
        this.defaultMax = Math.max(0, defaultMax);
        this.regenPerSecond = Math.max(0, regenPerSecond);
    }

    public void start() {
        if (regenTask != null) {
            regenTask.cancel();
        }
        if (regenPerSecond <= 0) {
            return;
        }
        regenTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                regen(player, regenPerSecond);
            }
        }, 20L, 20L);
    }

    public void stop() {
        if (regenTask != null) {
            regenTask.cancel();
            regenTask = null;
        }
    }

    public int getMax(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        Integer max = pdc.get(manaMaxKey, PersistentDataType.INTEGER);
        if (max == null || max <= 0) {
            max = defaultMax;
            pdc.set(manaMaxKey, PersistentDataType.INTEGER, max);
        }
        return max;
    }

    public int get(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        Integer value = pdc.get(manaKey, PersistentDataType.INTEGER);
        if (value == null) {
            int max = getMax(player);
            value = max;
            pdc.set(manaKey, PersistentDataType.INTEGER, value);
        }
        return Math.max(0, value);
    }

    public void set(Player player, int value) {
        int max = getMax(player);
        player.getPersistentDataContainer().set(manaKey, PersistentDataType.INTEGER, clamp(value, 0, max));
    }

    public boolean consume(Player player, int amount) {
        if (amount <= 0) {
            return true;
        }
        int current = get(player);
        if (current < amount) {
            player.sendActionBar(Component.text("Not enough mana (" + current + "/" + getMax(player) + ")", NamedTextColor.RED));
            return false;
        }
        set(player, current - amount);
        return true;
    }

    public void regen(Player player, int amount) {
        if (amount <= 0) {
            return;
        }
        int current = get(player);
        int max = getMax(player);
        if (current >= max) {
            return;
        }
        set(player, current + amount);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        int max = getMax(player);
        Integer current = player.getPersistentDataContainer().get(manaKey, PersistentDataType.INTEGER);
        if (current == null) {
            player.getPersistentDataContainer().set(manaKey, PersistentDataType.INTEGER, max);
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

