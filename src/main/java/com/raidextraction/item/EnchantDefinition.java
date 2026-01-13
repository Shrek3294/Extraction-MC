package com.raidextraction.item;

import java.util.List;
import java.util.Objects;

public record EnchantDefinition(
        String id,
        String nameMiniMessage,
        int maxLevel,
        List<WeaponType> allowedWeaponTypes
) {
    public EnchantDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nameMiniMessage, "nameMiniMessage");
        Objects.requireNonNull(allowedWeaponTypes, "allowedWeaponTypes");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id cannot be blank");
        }
        if (maxLevel <= 0) {
            throw new IllegalArgumentException("maxLevel must be positive");
        }
    }
}

