package com.raidextraction.integration;

import com.raidextraction.stash.ItemData;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Converts between Bukkit ItemStacks and the neutral ItemData model so stash persistence
 * can keep metadata without leaking Paper types into storage.
 */
public final class ItemDataMapper {
    private static final String BUKKIT_TAG_KEY = "bukkit";
    private final Logger logger;

    public ItemDataMapper(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public ItemData toItemData(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        Map<String, String> tags = new HashMap<>();
        try {
            tags.put(BUKKIT_TAG_KEY, Base64.getEncoder().encodeToString(stack.serializeAsBytes()));
        } catch (Exception error) {
            logger.warning("Failed to serialize item stack for stash: " + error.getMessage());
        }
        return new ItemData(stack.getType().name(), stack.getAmount(), tags);
    }

    public ItemStack toItemStack(ItemData itemData) {
        Objects.requireNonNull(itemData, "itemData");
        String encoded = itemData.tags().get(BUKKIT_TAG_KEY);
        if (encoded != null && !encoded.isBlank()) {
            try {
                return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
            } catch (Exception error) {
                logger.warning("Failed to deserialize stash item " + itemData.material() + ": " + error.getMessage());
            }
        }
        Material material = Material.matchMaterial(itemData.material());
        if (material == null) {
            logger.warning("Unknown material in stash entry: " + itemData.material());
            return new ItemStack(Material.AIR);
        }
        return new ItemStack(material, Math.max(1, itemData.amount()));
    }
}
