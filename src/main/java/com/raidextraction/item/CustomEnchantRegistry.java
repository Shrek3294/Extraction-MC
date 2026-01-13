package com.raidextraction.item;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class CustomEnchantRegistry {
    private final Map<String, EnchantDefinition> enchants;

    public CustomEnchantRegistry(ItemsConfig config) {
        Objects.requireNonNull(config, "config");
        this.enchants = config.enchants();
    }

    public Optional<EnchantDefinition> get(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(enchants.get(id));
    }
}

