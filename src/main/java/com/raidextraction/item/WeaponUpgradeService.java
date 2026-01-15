package com.raidextraction.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class WeaponUpgradeService {
    private final ItemKeys keys;
    private final CustomItemRegistry registry;
    private final CustomEnchantRegistry enchantRegistry;

    public WeaponUpgradeService(ItemKeys keys, CustomItemRegistry registry, CustomEnchantRegistry enchantRegistry) {
        this.keys = Objects.requireNonNull(keys, "keys");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.enchantRegistry = Objects.requireNonNull(enchantRegistry, "enchantRegistry");
    }

    public ApplyOutcome applyMod(Player player, ItemStack weaponStack, ItemStack modStack) {
        if (player == null || weaponStack == null || modStack == null) {
            return new ApplyOutcome(ApplyResult.failure("Missing items"), weaponStack, modStack);
        }
        if (!isWeapon(weaponStack)) {
            return new ApplyOutcome(ApplyResult.failure("Main item is not a weapon"), weaponStack, modStack);
        }
        if (!isMod(modStack)) {
            return new ApplyOutcome(ApplyResult.failure("Mod item is not a mod"), weaponStack, modStack);
        }

        String modId = CustomItemFactory.readItemId(keys, modStack);
        if (modId == null || modId.isBlank()) {
            return new ApplyOutcome(ApplyResult.failure("Unknown mod"), weaponStack, modStack);
        }
        ModDefinition mod = registry.getMod(modId).orElse(null);
        if (mod == null) {
            return new ApplyOutcome(ApplyResult.failure("Unregistered mod: " + modId), weaponStack, modStack);
        }

        ItemMeta weaponMeta = weaponStack.getItemMeta();
        if (weaponMeta == null) {
            return new ApplyOutcome(ApplyResult.failure("Weapon has no meta"), weaponStack, modStack);
        }
        PersistentDataContainer pdc = weaponMeta.getPersistentDataContainer();
        if (mod.category() == ModCategory.ENCHANT) {
            ApplyResult enchantResult = applyEnchant(player, weaponStack, pdc, mod);
            if (!enchantResult.ok()) {
                return new ApplyOutcome(enchantResult, weaponStack, modStack);
            }
        } else {
            Integer modSlotsRaw = pdc.get(keys.modSlots(), PersistentDataType.INTEGER);
            int modSlots = Math.max(0, modSlotsRaw != null ? modSlotsRaw : 0);
            List<String> currentMods = readMods(pdc);
            if (modSlots <= 0) {
                return new ApplyOutcome(ApplyResult.failure("This weapon has no mod slots"), weaponStack, modStack);
            }
            if (currentMods.size() >= modSlots) {
                return new ApplyOutcome(ApplyResult.failure("No mod slots available"), weaponStack, modStack);
            }

            if (currentMods.contains(mod.id())) {
                return new ApplyOutcome(ApplyResult.failure("This mod is already installed"), weaponStack, modStack);
            }
            currentMods.add(mod.id());
            writeMods(pdc, currentMods);
        }

        String weaponSpell = pdc.get(keys.spellId(), PersistentDataType.STRING);
        boolean unlocked = isSpellUnlocked(pdc);
        if (!unlocked && mod.unlockSpell() != null && !mod.unlockSpell().isBlank()
                && mod.unlockSpell().equalsIgnoreCase(weaponSpell)) {
            pdc.set(keys.spellUnlocked(), PersistentDataType.BYTE, (byte) 1);
            unlocked = true;
        }

        weaponMeta.lore(buildLore(weaponMeta.displayName(), pdc, unlocked));
        weaponStack.setItemMeta(weaponMeta);

        ItemStack updatedModStack = consumeOne(modStack);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.2f);
        player.sendActionBar(Component.text((mod.category() == ModCategory.ENCHANT ? "Enchanted: " : "Mod installed: ") + mod.id(),
                NamedTextColor.GREEN));
        return new ApplyOutcome(ApplyResult.success(), weaponStack, updatedModStack);
    }

    private boolean isWeapon(ItemStack stack) {
        return CustomItemType.WEAPON.equals(CustomItemFactory.readItemType(keys, stack));
    }

    private boolean isMod(ItemStack stack) {
        return CustomItemType.MOD.equals(CustomItemFactory.readItemType(keys, stack));
    }

    private ItemStack consumeOne(ItemStack stack) {
        int next = stack.getAmount() - 1;
        if (next <= 0) {
            return ItemStack.empty();
        }
        stack.setAmount(next);
        return stack;
    }

    private List<String> readMods(PersistentDataContainer pdc) {
        String raw = pdc.get(keys.mods(), PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList());
    }

    private void writeMods(PersistentDataContainer pdc, List<String> mods) {
        if (mods == null || mods.isEmpty()) {
            pdc.remove(keys.mods());
            return;
        }
        pdc.set(keys.mods(), PersistentDataType.STRING, String.join(",", mods));
    }

    private boolean isSpellUnlocked(PersistentDataContainer pdc) {
        Byte unlocked = pdc.get(keys.spellUnlocked(), PersistentDataType.BYTE);
        return unlocked != null && unlocked == (byte) 1;
    }

    private ApplyResult applyEnchant(Player player, ItemStack weaponStack, PersistentDataContainer pdc, ModDefinition mod) {
        String enchantId = mod.enchantId();
        if (enchantId == null || enchantId.isBlank()) {
            return ApplyResult.failure("Enchant scroll missing enchant_id");
        }
        EnchantDefinition enchant = enchantRegistry.get(enchantId).orElse(null);
        if (enchant == null) {
            return ApplyResult.failure("Unknown enchant: " + enchantId);
        }
        String weaponTypeRaw = pdc.get(keys.weaponType(), PersistentDataType.STRING);
        WeaponType weaponType = WeaponType.parse(weaponTypeRaw, WeaponType.SWORD);
        if (!enchant.allowedWeaponTypes().isEmpty() && !enchant.allowedWeaponTypes().contains(weaponType)) {
            return ApplyResult.failure("That enchant can't be applied to " + weaponType);
        }
        var enchants = readEnchants(pdc);
        int current = enchants.getOrDefault(enchantId, 0);
        int next = Math.min(enchant.maxLevel(), Math.max(0, current + Math.max(1, mod.enchantLevel())));
        if (next <= current) {
            return ApplyResult.failure("Enchant already at max level");
        }
        enchants.put(enchantId, next);
        writeEnchants(pdc, enchants);
        return ApplyResult.success();
    }

    private java.util.Map<String, Integer> readEnchants(PersistentDataContainer pdc) {
        String raw = pdc.get(keys.enchants(), PersistentDataType.STRING);
        java.util.Map<String, Integer> enchants = new java.util.HashMap<>();
        if (raw == null || raw.isBlank()) {
            return enchants;
        }
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            if (colon <= 0 || colon == trimmed.length() - 1) {
                continue;
            }
            String id = trimmed.substring(0, colon).trim();
            String levelRaw = trimmed.substring(colon + 1).trim();
            if (id.isBlank() || levelRaw.isBlank()) {
                continue;
            }
            try {
                int level = Integer.parseInt(levelRaw);
                if (level > 0) {
                    enchants.put(id, level);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return enchants;
    }

    private void writeEnchants(PersistentDataContainer pdc, java.util.Map<String, Integer> enchants) {
        if (enchants == null || enchants.isEmpty()) {
            pdc.remove(keys.enchants());
            return;
        }
        String raw = enchants.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null && entry.getValue() > 0)
                .map(entry -> entry.getKey().trim() + ":" + entry.getValue())
                .sorted()
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        if (raw.isBlank()) {
            pdc.remove(keys.enchants());
            return;
        }
        pdc.set(keys.enchants(), PersistentDataType.STRING, raw);
    }

    private List<Component> buildLore(Component displayName, PersistentDataContainer pdc, boolean spellUnlocked) {
        String rarity = pdc.get(keys.rarity(), PersistentDataType.STRING);
        if (rarity == null || rarity.isBlank()) {
            rarity = "COMMON";
        }
        Rarity rarityEnum = Rarity.parse(rarity, Rarity.COMMON);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(rarityEnum.name(), rarityEnum.color()));

        String weaponType = pdc.get(keys.weaponType(), PersistentDataType.STRING);
        if (weaponType == null || weaponType.isBlank()) {
            weaponType = "SWORD";
        }
        lore.add(Component.text(weaponType, NamedTextColor.DARK_GRAY));

        String spellId = pdc.get(keys.spellId(), PersistentDataType.STRING);
        if (spellId != null && !spellId.isBlank()) {
            lore.add(Component.text("Spell: " + spellId + (spellUnlocked ? " (unlocked)" : " (locked)"),
                    spellUnlocked ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY));
        }

        List<String> mods = readMods(pdc);
        if (!mods.isEmpty()) {
            lore.add(Component.text("Mods: " + String.join(", ", mods), NamedTextColor.GRAY));
        }
        String enchantsRaw = pdc.get(keys.enchants(), PersistentDataType.STRING);
        if (enchantsRaw != null && !enchantsRaw.isBlank()) {
            lore.add(Component.text("Enchants: " + enchantsRaw, NamedTextColor.GRAY));
        }
        return lore;
    }

    public record ApplyResult(boolean ok, String message) {
        public static ApplyResult success() {
            return new ApplyResult(true, "");
        }

        public static ApplyResult failure(String message) {
            return new ApplyResult(false, message);
        }
    }

    public record ApplyOutcome(ApplyResult result, ItemStack weaponStack, ItemStack modStack) {
    }
}
