package com.raidextraction.loot;

import com.raidextraction.config.model.LootTableDefinition;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class LootTableRegistry {
    private final Map<String, LootTableDefinition> tables = new HashMap<>();

    public LootTableRegistry(Map<String, LootTableDefinition> definitions) {
        if (definitions != null) {
            tables.putAll(definitions);
        }
    }

    public Optional<LootTableDefinition> get(String tableId) {
        Objects.requireNonNull(tableId, "tableId");
        return Optional.ofNullable(tables.get(tableId));
    }

    public void register(LootTableDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        tables.put(definition.id(), definition);
    }

    public Map<String, LootTableDefinition> definitions() {
        return Collections.unmodifiableMap(tables);
    }
}
