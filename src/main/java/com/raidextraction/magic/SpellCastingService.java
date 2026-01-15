package com.raidextraction.magic;

import com.raidextraction.item.CustomItemType;
import com.raidextraction.item.ItemKeys;
import com.raidextraction.item.ItemsConfig;
import com.raidextraction.item.SpellDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.block.Block;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SpellCastingService implements Listener {
    private static final int VOID_ORB_PROJECTILE_CUSTOM_MODEL_DATA = 4001;

    private final JavaPlugin plugin;
    private final ItemKeys itemKeys;
    private final ItemsConfig itemsConfig;
    private final PlayerManaService manaService;

    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final org.bukkit.NamespacedKey projectileSpellKey;
    private final org.bukkit.NamespacedKey debugSpellsKey;
    private final Map<UUID, Long> lastDebugMs = new HashMap<>();
    private final long debugThrottleMs = 500;

    public SpellCastingService(JavaPlugin plugin, ItemKeys itemKeys, ItemsConfig itemsConfig, PlayerManaService manaService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.itemKeys = Objects.requireNonNull(itemKeys, "itemKeys");
        this.itemsConfig = Objects.requireNonNull(itemsConfig, "itemsConfig");
        this.manaService = Objects.requireNonNull(manaService, "manaService");
        this.projectileSpellKey = new org.bukkit.NamespacedKey(plugin, "spell_projectile");
        this.debugSpellsKey = new org.bukkit.NamespacedKey(plugin, "debug_spells");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!event.getAction().isRightClick()) {
            return;
        }

        Player player = event.getPlayer();
        if (isDebug(player)) {
            debug(player, "interact action=" + event.getAction()
                    + " useBlock=" + event.useInteractedBlock()
                    + " useItem=" + event.useItemInHand()
                    + " block=" + (event.getClickedBlock() != null ? event.getClickedBlock().getType().name() : "none"));
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            debug(player, "no mainhand item");
            return;
        }
        if (!isWeapon(item)) {
            debug(player, "mainhand not a custom weapon");
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked != null && isLikelyInteractable(clicked) && !player.isSneaking()) {
            player.sendActionBar(Component.text("Sneak-right-click to cast spells on interactable blocks.", NamedTextColor.GRAY));
            debug(player, "blocked by interactable block without sneak: " + clicked.getType().name());
            return;
        }

        String spellId = readString(item, itemKeys.spellId());
        if (spellId == null || spellId.isBlank()) {
            debug(player, "weapon has no spell_id tag");
            return;
        }
        if (!isSpellUnlocked(item)) {
            player.sendActionBar(Component.text("Spell locked. Socket a core mod to unlock.", NamedTextColor.DARK_GRAY));
            debug(player, "spell locked: " + spellId);
            return;
        }

        SpellDefinition spell = itemsConfig.spells().get(spellId);
        long cooldownMs = spell != null ? spell.cooldownMs() : 1500;
        int manaCost = spell != null ? spell.manaCost() : 10;

        long now = System.currentTimeMillis();
        long next = nextAvailable(player.getUniqueId(), spellId);
        if (now < next) {
            long remaining = Math.max(0, next - now);
            player.sendActionBar(Component.text("Cooldown " + (remaining / 100) / 10.0 + "s", NamedTextColor.GRAY));
            debug(player, "cooldown remainingMs=" + remaining + " spell=" + spellId);
            return;
        }
        debug(player, "mana=" + manaService.get(player) + "/" + manaService.getMax(player) + " cost=" + manaCost + " spell=" + spellId);
        if (!manaService.consume(player, manaCost)) {
            debug(player, "not enough mana");
            return;
        }

        boolean casted = castSpell(player, spellId);
        if (!casted) {
            manaService.regen(player, manaCost);
            debug(player, "castSpell returned false for " + spellId);
            return;
        }

        event.setCancelled(true);
        setNextAvailable(player.getUniqueId(), spellId, now + cooldownMs);
        player.setCooldown(item.getType(), Math.max(1, (int) Math.ceil(cooldownMs / 50.0)));
        decayDurability(item, 2);
        player.swingMainHand();
        player.playSound(player.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 0.6f, 1.3f);
        debug(player, "cast ok spell=" + spellId + " cooldownMs=" + cooldownMs);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }
        String spellId = event.getEntity().getPersistentDataContainer().get(projectileSpellKey, PersistentDataType.STRING);
        if (spellId == null || spellId.isBlank()) {
            return;
        }
        if ("void_orb".equals(spellId)) {
            Location at = event.getEntity().getLocation();
            at.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, at, 55, 0.25, 0.25, 0.25, 0.18);
            at.getWorld().playSound(at, Sound.BLOCK_PORTAL_TRAVEL, 0.4f, 1.6f);
        }
        if (!(event.getHitEntity() instanceof LivingEntity hit)) {
            return;
        }

        switch (spellId) {
            case "poison_needle" -> {
                hit.damage(2.0, player);
                hit.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.POISON, 60, 0));
            }
            case "wind_slash" -> {
                hit.damage(1.0, player);
                Vector push = hit.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(0.8);
                push.setY(0.2);
                hit.setVelocity(push);
            }
            case "bone_shard" -> hit.damage(2.5, player);
            case "ice_lance" -> {
                hit.damage(2.0, player);
                hit.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 80, 1));
                hit.setFreezeTicks(Math.max(hit.getFreezeTicks(), 120));
            }
            case "void_orb" -> {
                hit.damage(1.5, player);
                hit.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WEAKNESS, 100, 0));
                Vector pull = player.getLocation().toVector().subtract(hit.getLocation().toVector()).normalize().multiply(0.4);
                pull.setY(0.1);
                hit.setVelocity(pull);
            }
            case "time_rift" -> {
                hit.damage(1.0, player);
                hit.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 100, 2));
                hit.setFreezeTicks(Math.max(hit.getFreezeTicks(), 140));
            }
            default -> {
            }
        }
    }

    private boolean castSpell(Player player, String spellId) {
        return switch (spellId) {
            case "poison_needle" -> launchItemProjectile(player, spellId, 2.2, 0.0, org.bukkit.Material.ARROW);
            case "wind_slash" -> launchItemProjectile(player, spellId, 2.0, 0.0, org.bukkit.Material.FEATHER);
            case "bone_shard" -> launchShardBurst(player, spellId);
            case "ice_lance" -> launchItemProjectile(player, spellId, 2.1, 0.0, org.bukkit.Material.PACKED_ICE);
            case "fireball" -> launchFireball(player, 1.05);
            case "lightning_bolt" -> castLightningBolt(player);
            case "void_orb" -> launchItemProjectile(player, spellId, 1.2, 0.02, org.bukkit.Material.ENDER_PEARL);
            case "fire_breath_wave" -> castCone(player, 5.5, 40, 3.0, true);
            case "light_spear" -> castBeam(player, 18.0, 5.0);
            case "time_rift" -> launchItemProjectile(player, spellId, 1.4, 0.01, org.bukkit.Material.CLOCK);
            case "soul_wave" -> castCone(player, 6.5, 55, 4.0, false);
            case "elemental_barrage" -> castElementalBarrage(player);
            default -> false;
        };
    }

    private boolean launchItemProjectile(Player player, String spellId, double speed, double spread,
            org.bukkit.Material displayMaterial) {
        Snowball snowball = player.launchProjectile(Snowball.class);
        snowball.getPersistentDataContainer().set(projectileSpellKey, PersistentDataType.STRING, spellId);
        if (displayMaterial != null && displayMaterial != org.bukkit.Material.AIR) {
            try {
                ItemStack displayItem = createProjectileDisplayItem(spellId, displayMaterial);
                if (displayItem != null) {
                    snowball.setItem(displayItem);
                }
            } catch (Throwable ignored) {
            }
        }
        Vector velocity = player.getLocation().getDirection().normalize().multiply(speed);
        if (spread > 0) {
            velocity.add(new Vector(
                    (Math.random() - 0.5) * spread,
                    (Math.random() - 0.5) * spread,
                    (Math.random() - 0.5) * spread));
        }
        snowball.setVelocity(velocity);
        if ("void_orb".equals(spellId)) {
            startVoidOrbParticles(snowball);
        }
        return true;
    }

    private void startVoidOrbParticles(Snowball projectile) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!projectile.isValid() || projectile.isDead() || projectile.getTicksLived() > 100) {
                    cancel();
                    return;
                }
                Location at = projectile.getLocation();
                at.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, at, 10, 0.08, 0.08, 0.08, 0.08);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private ItemStack createProjectileDisplayItem(String spellId, org.bukkit.Material displayMaterial) {
        if (displayMaterial == null || displayMaterial == org.bukkit.Material.AIR) {
            return null;
        }
        ItemStack stack = new ItemStack(displayMaterial);
        if ("void_orb".equals(spellId) && displayMaterial == org.bukkit.Material.ENDER_PEARL) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(VOID_ORB_PROJECTILE_CUSTOM_MODEL_DATA);
                stack.setItemMeta(meta);
            }
        }
        return stack;
    }

    private boolean launchShardBurst(Player player, String spellId) {
        for (int i = 0; i < 3; i++) {
            launchItemProjectile(player, spellId, 2.0, 0.25, org.bukkit.Material.BONE);
        }
        return true;
    }

    private boolean launchFireball(Player player, double speed) {
        Fireball fireball = player.launchProjectile(Fireball.class);
        fireball.setIsIncendiary(false);
        fireball.setYield(0);
        fireball.getPersistentDataContainer().set(projectileSpellKey, PersistentDataType.STRING, "fireball");
        fireball.setVelocity(player.getLocation().getDirection().normalize().multiply(speed));
        return true;
    }

    private boolean castLightningBolt(Player player) {
        Location origin = player.getEyeLocation();
        RayTraceResult trace = player.getWorld().rayTraceEntities(origin, origin.getDirection(), 20.0,
                entity -> entity instanceof LivingEntity && entity != player);
        if (trace == null || trace.getHitEntity() == null) {
            player.sendActionBar(Component.text("No target", NamedTextColor.GRAY));
            return false;
        }
        Entity hit = trace.getHitEntity();
        if (!(hit instanceof LivingEntity living)) {
            return false;
        }
        World world = player.getWorld();
        world.strikeLightningEffect(hit.getLocation());
        living.damage(6.0, player);
        return true;
    }

    private boolean castBeam(Player player, double range, double damage) {
        Location origin = player.getEyeLocation();
        RayTraceResult trace = player.getWorld().rayTraceEntities(origin, origin.getDirection(), range,
                entity -> entity instanceof LivingEntity && entity != player);
        if (trace == null || trace.getHitEntity() == null) {
            player.sendActionBar(Component.text("No target", NamedTextColor.GRAY));
            return false;
        }
        Entity hit = trace.getHitEntity();
        if (!(hit instanceof LivingEntity living)) {
            return false;
        }
        living.damage(damage, player);
        player.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, living.getLocation().add(0, 1.0, 0), 18, 0.2, 0.3, 0.2, 0.01);
        return true;
    }

    private boolean castCone(Player player, double range, double degrees, double damage, boolean ignite) {
        Location origin = player.getEyeLocation();
        Vector forward = origin.getDirection().normalize();
        double cos = Math.cos(Math.toRadians(degrees));
        for (Entity entity : player.getNearbyEntities(range, range, range)) {
            if (!(entity instanceof LivingEntity living) || entity == player) {
                continue;
            }
            Vector to = living.getLocation().add(0, 1.0, 0).toVector().subtract(origin.toVector()).normalize();
            if (forward.dot(to) < cos) {
                continue;
            }
            living.damage(damage, player);
            if (ignite) {
                living.setFireTicks(Math.max(living.getFireTicks(), 60));
            }
        }
        player.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK, player.getLocation().add(0, 1.0, 0), 6, 0.2, 0.2, 0.2, 0.0);
        return true;
    }

    private boolean castElementalBarrage(Player player) {
        int roll = (int) (Math.random() * 3);
        if (roll == 0) {
            return launchFireball(player, 1.05);
        }
        if (roll == 1) {
            return launchItemProjectile(player, "ice_lance", 2.2, 0.0, org.bukkit.Material.PACKED_ICE);
        }
        return castLightningBolt(player);
    }

    private boolean isWeapon(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        String type = meta.getPersistentDataContainer().get(itemKeys.itemType(), PersistentDataType.STRING);
        return CustomItemType.WEAPON.name().equals(type);
    }

    private boolean isLikelyInteractable(Block block) {
        if (block.getState() instanceof org.bukkit.inventory.InventoryHolder) {
            return true;
        }
        var type = block.getType();
        return Tag.BUTTONS.isTagged(type)
                || Tag.DOORS.isTagged(type)
                || Tag.TRAPDOORS.isTagged(type)
                || Tag.FENCE_GATES.isTagged(type)
                || type == org.bukkit.Material.LEVER;
    }

    private boolean isDebug(Player player) {
        Byte value = player.getPersistentDataContainer().get(debugSpellsKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    private void debug(Player player, String message) {
        if (!isDebug(player)) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = lastDebugMs.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < debugThrottleMs) {
            return;
        }
        lastDebugMs.put(player.getUniqueId(), now);
        player.sendActionBar(Component.text("[SpellDebug] " + message, NamedTextColor.DARK_GRAY));
        plugin.getLogger().info("[SpellDebug] " + player.getName() + " " + message);
    }

    private String readString(ItemStack stack, org.bukkit.NamespacedKey key) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    private boolean isSpellUnlocked(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte unlocked = meta.getPersistentDataContainer().get(itemKeys.spellUnlocked(), PersistentDataType.BYTE);
        return unlocked != null && unlocked == (byte) 1;
    }

    private long nextAvailable(UUID playerId, String spellId) {
        return Optional.ofNullable(cooldowns.get(playerId))
                .map(map -> map.get(spellId))
                .orElse(0L);
    }

    private void setNextAvailable(UUID playerId, String spellId, long next) {
        cooldowns.computeIfAbsent(playerId, ignored -> new HashMap<>()).put(spellId, next);
    }

    private void decayDurability(ItemStack stack, int damage) {
        if (damage <= 0) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }
        int nextDamage = Math.max(0, damageable.getDamage() + damage);
        damageable.setDamage(nextDamage);
        stack.setItemMeta((ItemMeta) damageable);
    }
}
