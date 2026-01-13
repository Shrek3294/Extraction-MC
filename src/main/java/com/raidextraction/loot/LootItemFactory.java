package com.raidextraction.loot;

import com.raidextraction.config.model.LootEntry;
import com.raidextraction.stash.ItemData;

public interface LootItemFactory {
    ItemData create(LootEntry entry, int amount);
}

