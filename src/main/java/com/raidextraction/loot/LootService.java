package com.raidextraction.loot;

import com.raidextraction.config.model.LootEntry;
import com.raidextraction.config.model.LootTableDefinition;
import com.raidextraction.stash.ItemData;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

public final class LootService {
    private final LootTableRegistry registry;
    private final Random random;
    private final LootItemFactory itemFactory;

    public LootService(LootTableRegistry registry, Random random, LootItemFactory itemFactory) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.random = Objects.requireNonNull(random, "random");
        this.itemFactory = Objects.requireNonNull(itemFactory, "itemFactory");
    }

    public List<ItemData> roll(String tableId, int rolls) {
        if (rolls <= 0) {
            return List.of();
        }
        Optional<LootTableDefinition> definition = registry.get(tableId);
        if (definition.isEmpty()) {
            return List.of();
        }
        LootTableDefinition table = definition.get();
        if (!table.isValid()) {
            return List.of();
        }
        List<LootEntry> entries = table.entries();
        int totalWeight = entries.stream().mapToInt(LootEntry::weight).sum();
        if (totalWeight <= 0) {
            return List.of();
        }
        List<ItemData> results = new ArrayList<>(rolls);
        for (int i = 0; i < rolls; i++) {
            LootEntry entry = pickEntry(entries, totalWeight);
            int amount = random.nextInt(entry.maxAmount() - entry.minAmount() + 1) + entry.minAmount();
            results.add(itemFactory.create(entry, amount));
        }
        return results;
    }

    private LootEntry pickEntry(List<LootEntry> entries, int totalWeight) {
        int roll = random.nextInt(totalWeight);
        int cursor = 0;
        for (LootEntry entry : entries) {
            cursor += entry.weight();
            if (roll < cursor) {
                return entry;
            }
        }
        return entries.get(entries.size() - 1);
    }
}
