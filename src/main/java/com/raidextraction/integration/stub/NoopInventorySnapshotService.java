package com.raidextraction.integration.stub;

import com.raidextraction.integration.InventorySnapshotService;
import com.raidextraction.stash.ItemData;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public final class NoopInventorySnapshotService implements InventorySnapshotService {
    private final Logger logger;

    public NoopInventorySnapshotService(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void snapshot(UUID playerId) {
        logStub("snapshot", playerId);
    }

    @Override
    public boolean restore(UUID playerId) {
        logStub("restore", playerId);
        return false;
    }

    @Override
    public Optional<List<ItemData>> peek(UUID playerId) {
        logStub("peek", playerId);
        return Optional.empty();
    }

    @Override
    public boolean hasSnapshot(UUID playerId) {
        return false;
    }

    @Override
    public void clear(UUID playerId) {
        logStub("clear", playerId);
    }

    private void logStub(String action, UUID playerId) {
        logger.fine(() -> "InventorySnapshotService stub ignoring " + action + " for player " + playerId);
    }
}
