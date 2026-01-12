package com.raidextraction.integration.paper;

import com.raidextraction.integration.InventorySnapshotService;
import com.raidextraction.integration.ItemDataMapper;
import com.raidextraction.stash.ItemData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Paper-backed inventory snapshot adapter that keeps neutral ItemData exposed for stash flows.
 */
public final class PaperInventorySnapshotService implements InventorySnapshotService {
    private final JavaPlugin plugin;
    private final ItemDataMapper itemDataMapper;
    private final Map<UUID, ItemStack[]> snapshots = new HashMap<>();

    public PaperInventorySnapshotService(JavaPlugin plugin, ItemDataMapper itemDataMapper) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.itemDataMapper = Objects.requireNonNull(itemDataMapper, "itemDataMapper");
    }

    @Override
    public void snapshot(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        snapshots.put(playerId, copyItems(player.getInventory().getContents()));
    }

    @Override
    public boolean restore(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        ItemStack[] saved = snapshots.get(playerId);
        if (saved == null) {
            return false;
        }
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return false;
        }
        player.getInventory().setContents(copyItems(saved));
        player.updateInventory();
        return true;
    }

    @Override
    public Optional<List<ItemData>> peek(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        ItemStack[] saved = snapshots.get(playerId);
        if (saved == null) {
            return Optional.empty();
        }
        List<ItemData> items = new ArrayList<>();
        for (ItemStack item : saved) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                continue;
            }
            items.add(itemDataMapper.toItemData(item));
        }
        return Optional.of(items);
    }

    @Override
    public boolean hasSnapshot(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return snapshots.containsKey(playerId);
    }

    @Override
    public void clear(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        snapshots.remove(playerId);
    }

    private ItemStack[] copyItems(ItemStack[] items) {
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            copy[i] = item == null ? null : item.clone();
        }
        return copy;
    }
}
