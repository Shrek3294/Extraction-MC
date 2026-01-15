package com.raidextraction.listener;

import com.raidextraction.item.CustomEnchantRegistry;
import com.raidextraction.item.CustomItemType;
import com.raidextraction.item.ItemKeys;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class CustomEnchantListener implements Listener {
    private static final long MOMENTUM_WINDOW_MS = 1500;
    private static final long BLEED_DURATION_MS = 4500;

    private final JavaPlugin plugin;
    private final ItemKeys itemKeys;
    private final CustomEnchantRegistry enchantRegistry;

    private final Map<UUID, MomentumState> momentum = new HashMap<>();
    private final Map<UUID, BleedState> bleeds = new HashMap<>();
    private BukkitTask bleedTask;

    public CustomEnchantListener(JavaPlugin plugin, ItemKeys itemKeys, CustomEnchantRegistry enchantRegistry) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.itemKeys = Objects.requireNonNull(itemKeys, "itemKeys");
        this.enchantRegistry = Objects.requireNonNull(enchantRegistry, "enchantRegistry");
        startBleedTask();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (!isWeapon(weapon)) {
            return;
        }

        int backstab = getEnchantLevel(weapon, "backstab");
        if (backstab > 0 && isBehind(player, target)) {
            double multiplier = 1.0 + (0.25 * backstab);
            event.setDamage(event.getDamage() * multiplier);
            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1.0, 0), 10, 0.2, 0.2, 0.2, 0.1);
        }

        int momentumLevel = getEnchantLevel(weapon, "momentum");
        if (momentumLevel > 0) {
            applyMomentum(player, event, momentumLevel);
        }

        int bleed = getEnchantLevel(weapon, "bleed");
        if (bleed > 0) {
            applyBleed(player, target, bleed);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        if (!(event.getEntity().getKiller() instanceof Player player)) {
            return;
        }
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (!isWeapon(weapon)) {
            return;
        }
        int harvest = getEnchantLevel(weapon, "grim_harvest");
        if (harvest <= 0) {
            return;
        }
        double heal = 1.0 + (0.5 * harvest);
        double maxHealth = maxHealth(player);
        player.setHealth(Math.min(maxHealth, player.getHealth() + heal));
    }

    private double maxHealth(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return attribute != null ? attribute.getValue() : player.getHealth();
    }

    private void applyMomentum(Player player, EntityDamageByEntityEvent event, int level) {
        long now = System.currentTimeMillis();
        MomentumState state = momentum.get(player.getUniqueId());
        if (state == null || now - state.lastHitMs() > MOMENTUM_WINDOW_MS) {
            state = new MomentumState(0, now);
        }
        int nextStacks = Math.min(6, state.stacks() + 1);
        momentum.put(player.getUniqueId(), new MomentumState(nextStacks, now));

        double bonus = Math.min(0.25, (0.05 * nextStacks * level));
        event.setDamage(event.getDamage() * (1.0 + bonus));
    }

    private void applyBleed(Player attacker, LivingEntity target, int level) {
        long now = System.currentTimeMillis();
        UUID victimId = target.getUniqueId();
        BleedState state = bleeds.get(victimId);
        int maxStacks = Math.min(3, Math.max(1, level));
        int stacks = 1;
        if (state != null && now < state.expiresAtMs()) {
            stacks = Math.min(maxStacks, state.stacks() + 1);
        }
        bleeds.put(victimId, new BleedState(stacks, now + BLEED_DURATION_MS, attacker.getUniqueId()));
        target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation().add(0, 1.0, 0), 6, 0.2, 0.3, 0.2, 0.1);
    }

    private void startBleedTask() {
        if (bleedTask != null) {
            bleedTask.cancel();
        }
        bleedTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<UUID, BleedState>> iterator = bleeds.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, BleedState> entry = iterator.next();
                BleedState state = entry.getValue();
                var entity = plugin.getServer().getEntity(entry.getKey());
                if (!(entity instanceof LivingEntity victim) || victim.isDead() || now >= state.expiresAtMs()) {
                    iterator.remove();
                    continue;
                }
                double damage = 0.5 * state.stacks();
                Player attacker = plugin.getServer().getPlayer(state.attackerId());
                if (attacker != null) {
                    victim.damage(damage, attacker);
                } else {
                    victim.damage(damage);
                }
                victim.getWorld().spawnParticle(Particle.DUST, victim.getLocation().add(0, 1.0, 0), 4, 0.15, 0.2, 0.15, 0,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(180, 30, 30), 0.8f));
            }
        }, 20L, 20L);
    }

    private boolean isWeapon(ItemStack stack) {
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

    private int getEnchantLevel(ItemStack stack, String enchantId) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return 0;
        }
        String raw = meta.getPersistentDataContainer().get(itemKeys.enchants(), PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            int colon = trimmed.indexOf(':');
            if (colon <= 0 || colon == trimmed.length() - 1) {
                continue;
            }
            String id = trimmed.substring(0, colon).trim();
            if (!id.equalsIgnoreCase(enchantId)) {
                continue;
            }
            try {
                return Integer.parseInt(trimmed.substring(colon + 1).trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private boolean isBehind(Player attacker, LivingEntity target) {
        Vector facing = target.getLocation().getDirection().normalize();
        Vector toAttacker = attacker.getLocation().toVector().subtract(target.getLocation().toVector()).normalize();
        return facing.dot(toAttacker) < -0.5;
    }

    private record MomentumState(int stacks, long lastHitMs) {
    }

    private record BleedState(int stacks, long expiresAtMs, UUID attackerId) {
    }
}
