package com.raidextraction.loot;

import com.raidextraction.config.model.LootEntry;
import com.raidextraction.config.model.LootTableDefinition;
import com.raidextraction.item.ItemKeys;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class LootPricingService {
    private final Map<String, LootValue> byItemId = new HashMap<>();
    private final Map<String, LootValue> byMaterial = new HashMap<>();

    public LootPricingService(Map<String, LootTableDefinition> definitions) {
        if (definitions == null) {
            return;
        }
        for (LootTableDefinition table : definitions.values()) {
            if (table == null || table.entries() == null) {
                continue;
            }
            for (LootEntry entry : table.entries()) {
                if (entry == null) {
                    continue;
                }
                long credits = Math.max(0L, entry.credits());
                String category = entry.category();
                if (credits <= 0L || category == null || category.isBlank()) {
                    continue;
                }
                LootValue value = new LootValue(category.trim(), credits);
                if (entry.id() != null && !entry.id().isBlank()) {
                    byItemId.put(entry.id(), value);
                }
                if (entry.material() != null && !entry.material().isBlank()) {
                    byMaterial.putIfAbsent(entry.material(), value);
                }
            }
        }
    }

    public Optional<LootValue> lookup(ItemStack stack, ItemKeys itemKeys) {
        Objects.requireNonNull(itemKeys, "itemKeys");
        if (stack == null || stack.getType().isAir()) {
            return Optional.empty();
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            String itemId = meta.getPersistentDataContainer().get(itemKeys.itemId(), PersistentDataType.STRING);
            if (itemId != null && !itemId.isBlank()) {
                LootValue byId = byItemId.get(itemId);
                if (byId != null) {
                    return Optional.of(byId);
                }
            }
        }

        LootValue byMat = byMaterial.get(stack.getType().name());
        return Optional.ofNullable(byMat);
    }

    public record LootValue(String category, long credits) {
        public LootValue {
            Objects.requireNonNull(category, "category");
            if (credits < 0) {
                throw new IllegalArgumentException("credits cannot be negative");
            }
        }
    }
}

