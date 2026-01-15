package com.raidextraction.trader;

import com.raidextraction.config.ConfigManager;
import com.raidextraction.item.CustomItemFactory;
import com.raidextraction.item.CustomItemRegistry;
import com.raidextraction.item.ItemKeys;
import com.raidextraction.loot.LootPricingService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class TraderService {
    private static final String ROOT = "trader";
    private static final String NPC_UUID_PATH = ROOT + ".npc.uuid";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final LootPricingService pricingService;
    private final ItemKeys itemKeys;
    private final CustomItemRegistry customItemRegistry;
    private final CustomItemFactory customItemFactory;
    private final Logger logger;
    private final NamespacedKey traderNpcKey;

    private final boolean enabled;
    private final boolean spawnAtLobby;
    private final String npcName;
    private final Set<String> sellCategories;
    private final Map<String, KitDefinition> kits;
    private final String defaultKitId;

    public TraderService(JavaPlugin plugin,
            ConfigManager configManager,
            LootPricingService pricingService,
            ItemKeys itemKeys,
            CustomItemRegistry customItemRegistry,
            CustomItemFactory customItemFactory) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.pricingService = Objects.requireNonNull(pricingService, "pricingService");
        this.itemKeys = Objects.requireNonNull(itemKeys, "itemKeys");
        this.customItemRegistry = Objects.requireNonNull(customItemRegistry, "customItemRegistry");
        this.customItemFactory = Objects.requireNonNull(customItemFactory, "customItemFactory");
        this.logger = plugin.getLogger();
        this.traderNpcKey = new NamespacedKey(plugin, "trader_npc");

        FileConfiguration traderConfig = configManager.getTraderConfig();
        this.enabled = traderConfig.getBoolean(ROOT + ".enabled", false);
        this.spawnAtLobby = traderConfig.getBoolean(ROOT + ".npc.spawn_at_lobby", true);
        this.npcName = traderConfig.getString(ROOT + ".npc.name", "Trader");
        this.sellCategories = parseCategories(traderConfig.getStringList(ROOT + ".sell_categories"));
        this.kits = parseKits(traderConfig.getConfigurationSection(ROOT + ".kits"));
        this.defaultKitId = kits.containsKey("starter") ? "starter" : kits.keySet().stream().findFirst().orElse(null);
    }

    public boolean enabled() {
        return enabled;
    }

    public String npcName() {
        return npcName;
    }

    public Optional<KitDefinition> defaultKit() {
        if (defaultKitId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(kits.get(defaultKitId));
    }

    public Optional<String> defaultKitId() {
        return Optional.ofNullable(defaultKitId);
    }

    public boolean isTraderNpc(Entity entity) {
        if (entity == null || entity.getType() != EntityType.VILLAGER) {
            return false;
        }
        return entity.getPersistentDataContainer().has(traderNpcKey, PersistentDataType.BYTE);
    }

    public void ensureNpcPresent() {
        if (!enabled || !spawnAtLobby) {
            return;
        }
        UUID configured = readConfiguredNpcUuid().orElse(null);
        Entity existing = configured != null ? plugin.getServer().getEntity(configured) : null;
        if (existing instanceof Villager villager) {
            markNpc(villager);
            return;
        }

        Location spawn = lobbySpawnLocation();
        if (spawn == null || spawn.getWorld() == null) {
            logger.warning("Trader NPC is enabled, but lobby world is not loaded; skipping spawn.");
            return;
        }

        Villager found = findNearbyNpc(spawn);
        if (found != null) {
            markNpc(found);
            persistNpcUuid(found.getUniqueId());
            return;
        }

        Villager villager = (Villager) spawn.getWorld().spawnEntity(spawn, EntityType.VILLAGER);
        villager.customName(Component.text(npcName));
        villager.setCustomNameVisible(true);
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setSilent(true);
        villager.setCollidable(false);
        villager.setRemoveWhenFarAway(false);
        markNpc(villager);
        persistNpcUuid(villager.getUniqueId());
        logger.info("Spawned Trader NPC at lobby spawn (uuid=" + villager.getUniqueId() + ").");
    }

    public SellPreview previewSellAll(Player player) {
        Objects.requireNonNull(player, "player");
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        long credits = 0L;
        int stacks = 0;
        for (ItemStack stack : contents) {
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                continue;
            }
            LootPricingService.LootValue value = pricingService.lookup(stack, itemKeys).orElse(null);
            if (value == null || !sellCategories.contains(normalize(value.category()))) {
                continue;
            }
            long perItem = Math.max(0L, value.credits());
            if (perItem <= 0) {
                continue;
            }
            credits += safeMultiply(perItem, stack.getAmount());
            stacks++;
        }
        return new SellPreview(Math.max(0L, credits), stacks);
    }

    public SellResult sellAll(Player player) {
        Objects.requireNonNull(player, "player");
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        List<ItemStack> removed = new ArrayList<>();
        long credits = 0L;
        int stacks = 0;

        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                continue;
            }
            LootPricingService.LootValue value = pricingService.lookup(stack, itemKeys).orElse(null);
            if (value == null || !sellCategories.contains(normalize(value.category()))) {
                continue;
            }
            long perItem = Math.max(0L, value.credits());
            if (perItem <= 0) {
                continue;
            }
            credits += safeMultiply(perItem, stack.getAmount());
            stacks++;
            removed.add(stack.clone());
            inventory.setItem(slot, null);
        }

        if (!removed.isEmpty()) {
            player.updateInventory();
        }
        return new SellResult(Math.max(0L, credits), stacks, removed);
    }

    public void restoreItems(Player player, List<ItemStack> items) {
        if (player == null || items == null || items.isEmpty()) {
            return;
        }
        for (ItemStack stack : items) {
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                continue;
            }
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
            leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
        player.updateInventory();
    }

    public Optional<KitDefinition> kit(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(kits.get(id));
    }

    public List<ItemStack> createKitStacks(String kitId) {
        KitDefinition kit = kits.get(kitId);
        if (kit == null) {
            return List.of();
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (KitItem item : kit.items()) {
            ItemStack stack = toItemStack(item);
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                continue;
            }
            stacks.add(stack);
        }
        return List.copyOf(stacks);
    }

    private ItemStack toItemStack(KitItem item) {
        if (item.itemId() != null && !item.itemId().isBlank()) {
            String id = item.itemId();
            var weapon = customItemRegistry.getWeapon(id);
            if (weapon.isPresent()) {
                return customItemFactory.createWeapon(weapon.get());
            }
            var mod = customItemRegistry.getMod(id);
            if (mod.isPresent()) {
                return customItemFactory.createMod(mod.get(), Math.max(1, item.amount()));
            }
        }

        Material material = Material.matchMaterial(item.material());
        if (material == null) {
            logger.warning("Unknown kit material: " + item.material());
            return new ItemStack(Material.AIR);
        }
        return new ItemStack(material, Math.max(1, item.amount()));
    }

    private void markNpc(Villager villager) {
        villager.getPersistentDataContainer().set(traderNpcKey, PersistentDataType.BYTE, (byte) 1);
    }

    private Villager findNearbyNpc(Location spawn) {
        World world = spawn.getWorld();
        if (world == null) {
            return null;
        }
        for (Entity entity : world.getNearbyEntities(spawn, 6, 6, 6)) {
            if (entity instanceof Villager villager && isTraderNpc(villager)) {
                return villager;
            }
        }
        for (Entity entity : world.getNearbyEntities(spawn, 6, 6, 6)) {
            if (entity instanceof Villager villager && isNamedLikeTrader(villager)) {
                return villager;
            }
        }
        return null;
    }

    private boolean isNamedLikeTrader(Villager villager) {
        if (villager == null) {
            return false;
        }
        Component customName = villager.customName();
        if (customName == null) {
            return false;
        }
        String plain = PlainTextComponentSerializer.plainText().serialize(customName).trim();
        return !plain.isEmpty() && plain.equalsIgnoreCase(npcName);
    }

    private Location lobbySpawnLocation() {
        var cfg = configManager.getLobbySpawnConfig();
        World world = plugin.getServer().getWorld(cfg.world());
        if (world == null) {
            return null;
        }
        return new Location(world, cfg.x(), cfg.y(), cfg.z(), cfg.yaw(), cfg.pitch());
    }

    private Optional<UUID> readConfiguredNpcUuid() {
        String raw = configManager.getTraderConfig().getString(NPC_UUID_PATH, "");
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private void persistNpcUuid(UUID uuid) {
        FileConfiguration traderConfig = configManager.getTraderConfig();
        traderConfig.set(NPC_UUID_PATH, uuid != null ? uuid.toString() : "");
        File file = new File(plugin.getDataFolder(), "trader.yml");
        try {
            traderConfig.save(file);
        } catch (Exception error) {
            logger.log(Level.WARNING, "Failed to persist trader.yml NPC uuid", error);
        }
    }

    private Set<String> parseCategories(List<String> raw) {
        Set<String> categories = new HashSet<>();
        if (raw == null) {
            return categories;
        }
        for (String value : raw) {
            String normalized = normalize(value);
            if (!normalized.isEmpty()) {
                categories.add(normalized);
            }
        }
        return Set.copyOf(categories);
    }

    private Map<String, KitDefinition> parseKits(ConfigurationSection kitsSection) {
        if (kitsSection == null) {
            return Map.of();
        }
        Map<String, KitDefinition> parsed = new HashMap<>();
        for (String kitId : kitsSection.getKeys(false)) {
            ConfigurationSection kitSection = kitsSection.getConfigurationSection(kitId);
            if (kitSection == null) {
                continue;
            }
            long price = Math.max(0L, kitSection.getLong("price", 0L));
            List<Map<?, ?>> itemMaps = kitSection.getMapList("items");
            List<KitItem> items = new ArrayList<>();
            for (Map<?, ?> map : itemMaps) {
                String itemId = map.get("item_id") != null ? map.get("item_id").toString() : "";
                String material = map.get("material") != null ? map.get("material").toString() : "AIR";
                int amount = 1;
                Object rawAmount = map.get("amount");
                if (rawAmount instanceof Number number) {
                    amount = number.intValue();
                }
                items.add(new KitItem(itemId, material, Math.max(1, amount)));
            }
            parsed.put(kitId, new KitDefinition(price, List.copyOf(items)));
        }
        return Map.copyOf(parsed);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase();
    }

    private long safeMultiply(long value, long multiplier) {
        if (value == 0 || multiplier == 0) {
            return 0L;
        }
        if (value > 0 && multiplier > 0 && value > Long.MAX_VALUE / multiplier) {
            return Long.MAX_VALUE;
        }
        return value * multiplier;
    }

    public record SellPreview(long credits, int stacks) {
    }

    public record SellResult(long credits, int stacks, List<ItemStack> removedItems) {
    }

    public record KitDefinition(long price, List<KitItem> items) {
    }

    public record KitItem(String itemId, String material, int amount) {
    }
}
