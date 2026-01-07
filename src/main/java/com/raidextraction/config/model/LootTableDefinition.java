package com.raidextraction.config.model;

import java.util.List;

public record LootTableDefinition(
        String id,
        List<LootEntry> entries
) {
    public boolean isValid() {
        return id != null && !id.isEmpty() && entries != null && !entries.isEmpty() && entries.stream().allMatch(LootEntry::isValid);
    }
}
