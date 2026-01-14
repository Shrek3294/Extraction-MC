package com.raidextraction.stash;

import java.util.Objects;
import java.util.UUID;

public final class FixedStashCapacityProvider implements StashCapacityProvider {
    private final int capacity;

    public FixedStashCapacityProvider(int capacity) {
        this.capacity = Math.max(0, capacity);
    }

    @Override
    public int capacity(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        return capacity;
    }
}

