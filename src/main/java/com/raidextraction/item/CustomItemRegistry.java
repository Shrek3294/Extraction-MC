package com.raidextraction.item;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class CustomItemRegistry {
    private final Map<String, WeaponDefinition> weapons;
    private final Map<String, ModDefinition> mods;

    public CustomItemRegistry(ItemsConfig config) {
        Objects.requireNonNull(config, "config");
        this.weapons = config.weapons();
        this.mods = config.mods();
    }

    public Optional<WeaponDefinition> getWeapon(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(weapons.get(id));
    }

    public Optional<ModDefinition> getMod(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mods.get(id));
    }

    public boolean hasAny(String id) {
        return weapons.containsKey(id) || mods.containsKey(id);
    }
}

