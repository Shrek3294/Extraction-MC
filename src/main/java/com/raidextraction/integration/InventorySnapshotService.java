package com.raidextraction.integration;

import com.raidextraction.stash.ItemData;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter for capturing and restoring player inventories without leaking Paper types into core logic.
 */
public interface InventorySnapshotService {

    /**
     * Capture the player's current inventory for later restore or stash commit.
     */
    void snapshot(UUID playerId);

    /**
     * Restore the previously captured inventory if available.
     *
     * @return true if a snapshot existed and was restored
     */
    boolean restore(UUID playerId);

    /**
     * Peek at the captured contents using the neutral ItemData model.
     */
    Optional<List<ItemData>> peek(UUID playerId);

    /**
     * Check whether a snapshot is available for the player.
     */
    boolean hasSnapshot(UUID playerId);

    /**
     * Clear any stored snapshot for the player.
     */
    void clear(UUID playerId);
}
