package com.raidextraction.item;

import org.bukkit.Material;

import java.util.Objects;

public record WeaponDefinition(
        String id,
        Material material,
        Rarity rarity,
        String nameMiniMessage,
        int customModelData,
        WeaponType weaponType,
        int modSlots,
        SpellSpec spell
) {
    public WeaponDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(rarity, "rarity");
        Objects.requireNonNull(nameMiniMessage, "nameMiniMessage");
        Objects.requireNonNull(weaponType, "weaponType");
        Objects.requireNonNull(spell, "spell");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id cannot be blank");
        }
        if (modSlots < 0) {
            throw new IllegalArgumentException("modSlots cannot be negative");
        }
    }

    public record SpellSpec(String id, boolean locked) {
        public SpellSpec {
            Objects.requireNonNull(id, "id");
        }
    }
}

