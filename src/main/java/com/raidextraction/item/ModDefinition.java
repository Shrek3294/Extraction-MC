package com.raidextraction.item;

import org.bukkit.Material;

import java.util.Objects;

public record ModDefinition(
        String id,
        Material material,
        Rarity rarity,
        String nameMiniMessage,
        int customModelData,
        ModCategory category,
        String unlockSpell,
        String enchantId,
        int enchantLevel
) {
    public ModDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(rarity, "rarity");
        Objects.requireNonNull(nameMiniMessage, "nameMiniMessage");
        Objects.requireNonNull(category, "category");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id cannot be blank");
        }
    }
}
