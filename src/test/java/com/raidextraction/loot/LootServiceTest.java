package com.raidextraction.loot;

import com.raidextraction.config.model.LootEntry;
import com.raidextraction.config.model.LootTableDefinition;
import com.raidextraction.stash.ItemData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootServiceTest {

    @Test
    void weightedLootRollsFavorHigherWeightEntries() {
        LootEntry common = new LootEntry("common", "STONE", 3, 1, 1);
        LootEntry rare = new LootEntry("rare", "DIAMOND", 1, 1, 1);
        LootTableDefinition table = new LootTableDefinition("table", List.of(common, rare));
        LootTableRegistry registry = new LootTableRegistry(Map.of(table.id(), table));
        LootService service = new LootService(registry, new Random(42),
                (entry, amount) -> new ItemData(entry.material(), amount));

        List<ItemData> rolls = service.roll(table.id(), 10_000);
        long commonCount = rolls.stream().filter(item -> item.material().equals("STONE")).count();
        long rareCount = rolls.stream().filter(item -> item.material().equals("DIAMOND")).count();

        assertEquals(10_000, commonCount + rareCount);
        assertTrue(commonCount > rareCount);
        assertTrue(commonCount >= rareCount * 2);
    }
}
