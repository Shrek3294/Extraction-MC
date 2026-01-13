package com.raidextraction.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CustomItemFactory {
    private final ItemKeys keys;

    public CustomItemFactory(ItemKeys keys) {
        this.keys = Objects.requireNonNull(keys, "keys");
    }

    public ItemStack createWeapon(WeaponDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        ItemStack stack = new ItemStack(definition.material(), 1);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(MiniMessageText.parse(definition.nameMiniMessage()));
        if (definition.customModelData() > 0) {
            meta.setCustomModelData(definition.customModelData());
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(definition.rarity().name(), definition.rarity().color()));
        lore.add(Component.text(definition.weaponType().name(), NamedTextColor.DARK_GRAY));
        if (definition.spell() != null && definition.spell().id() != null && !definition.spell().id().isBlank()) {
            boolean unlocked = !definition.spell().locked();
            lore.add(Component.text("Spell: " + definition.spell().id() + (unlocked ? " (unlocked)" : " (locked)"),
                    unlocked ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY));
        }
        meta.lore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.itemId(), PersistentDataType.STRING, definition.id());
        pdc.set(keys.itemType(), PersistentDataType.STRING, CustomItemType.WEAPON.name());
        pdc.set(keys.rarity(), PersistentDataType.STRING, definition.rarity().name());
        pdc.set(keys.weaponType(), PersistentDataType.STRING, definition.weaponType().name());
        pdc.set(keys.modSlots(), PersistentDataType.INTEGER, definition.modSlots());
        if (definition.spell() != null && definition.spell().id() != null && !definition.spell().id().isBlank()) {
            pdc.set(keys.spellId(), PersistentDataType.STRING, definition.spell().id());
            pdc.set(keys.spellUnlocked(), PersistentDataType.BYTE, (byte) (definition.spell().locked() ? 0 : 1));
        }

        stack.setItemMeta(meta);
        return stack;
    }

    public ItemStack createMod(ModDefinition definition, int amount) {
        Objects.requireNonNull(definition, "definition");
        ItemStack stack = new ItemStack(definition.material(), Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(MiniMessageText.parse(definition.nameMiniMessage()));
        if (definition.customModelData() > 0) {
            meta.setCustomModelData(definition.customModelData());
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.lore(List.of(
                Component.text(definition.rarity().name(), definition.rarity().color()),
                Component.text("Mod: " + definition.category().name(), NamedTextColor.DARK_GRAY)));

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.itemId(), PersistentDataType.STRING, definition.id());
        pdc.set(keys.itemType(), PersistentDataType.STRING, CustomItemType.MOD.name());
        pdc.set(keys.rarity(), PersistentDataType.STRING, definition.rarity().name());

        stack.setItemMeta(meta);
        return stack;
    }

    public static String readItemId(ItemKeys keys, ItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(keys.itemId(), PersistentDataType.STRING);
    }

    public static CustomItemType readItemType(ItemKeys keys, ItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        String raw = meta.getPersistentDataContainer().get(keys.itemType(), PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return CustomItemType.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
