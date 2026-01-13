package com.raidextraction.integration;

import com.raidextraction.config.model.LootEntry;
import com.raidextraction.item.CustomItemFactory;
import com.raidextraction.item.CustomItemRegistry;
import com.raidextraction.loot.LootItemFactory;
import com.raidextraction.stash.ItemData;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

public final class PaperLootItemFactory implements LootItemFactory {
    private final CustomItemRegistry customItemRegistry;
    private final CustomItemFactory customItemFactory;
    private final ItemDataMapper itemDataMapper;

    public PaperLootItemFactory(CustomItemRegistry customItemRegistry, CustomItemFactory customItemFactory,
            ItemDataMapper itemDataMapper) {
        this.customItemRegistry = Objects.requireNonNull(customItemRegistry, "customItemRegistry");
        this.customItemFactory = Objects.requireNonNull(customItemFactory, "customItemFactory");
        this.itemDataMapper = Objects.requireNonNull(itemDataMapper, "itemDataMapper");
    }

    @Override
    public ItemData create(LootEntry entry, int amount) {
        if (entry == null) {
            return new ItemData("AIR", 1);
        }

        String id = entry.id();
        if (id != null && !id.isBlank()) {
            var weapon = customItemRegistry.getWeapon(id);
            if (weapon.isPresent()) {
                ItemStack stack = customItemFactory.createWeapon(weapon.get());
                return itemDataMapper.toItemData(stack);
            }
            var mod = customItemRegistry.getMod(id);
            if (mod.isPresent()) {
                ItemStack stack = customItemFactory.createMod(mod.get(), Math.max(1, amount));
                return itemDataMapper.toItemData(stack);
            }
        }

        return new ItemData(entry.material(), Math.max(1, amount));
    }
}
