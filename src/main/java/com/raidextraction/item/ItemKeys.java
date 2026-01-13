package com.raidextraction.item;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class ItemKeys {
    private final NamespacedKey itemId;
    private final NamespacedKey itemType;
    private final NamespacedKey rarity;
    private final NamespacedKey weaponType;
    private final NamespacedKey modSlots;
    private final NamespacedKey mods;
    private final NamespacedKey enchants;
    private final NamespacedKey spellId;
    private final NamespacedKey spellUnlocked;

    public ItemKeys(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        this.itemId = new NamespacedKey(plugin, "item_id");
        this.itemType = new NamespacedKey(plugin, "item_type");
        this.rarity = new NamespacedKey(plugin, "rarity");
        this.weaponType = new NamespacedKey(plugin, "weapon_type");
        this.modSlots = new NamespacedKey(plugin, "mod_slots");
        this.mods = new NamespacedKey(plugin, "mods");
        this.enchants = new NamespacedKey(plugin, "enchants");
        this.spellId = new NamespacedKey(plugin, "spell_id");
        this.spellUnlocked = new NamespacedKey(plugin, "spell_unlocked");
    }

    public NamespacedKey itemId() {
        return itemId;
    }

    public NamespacedKey itemType() {
        return itemType;
    }

    public NamespacedKey rarity() {
        return rarity;
    }

    public NamespacedKey weaponType() {
        return weaponType;
    }

    public NamespacedKey modSlots() {
        return modSlots;
    }

    public NamespacedKey mods() {
        return mods;
    }

    public NamespacedKey enchants() {
        return enchants;
    }

    public NamespacedKey spellId() {
        return spellId;
    }

    public NamespacedKey spellUnlocked() {
        return spellUnlocked;
    }
}
