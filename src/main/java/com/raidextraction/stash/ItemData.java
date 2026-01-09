package com.raidextraction.stash;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public record ItemData(String material, int amount, Map<String, String> tags) {
    public ItemData {
        Objects.requireNonNull(material, "material");
        if (material.isBlank()) {
            throw new IllegalArgumentException("material cannot be blank");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (tags == null) {
            tags = Map.of();
        }
        tags = Collections.unmodifiableMap(new HashMap<>(tags));
    }

    public ItemData(String material, int amount) {
        this(material, amount, Map.of());
    }
}
