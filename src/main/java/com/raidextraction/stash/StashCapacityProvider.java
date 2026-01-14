package com.raidextraction.stash;

import java.util.UUID;

@FunctionalInterface
public interface StashCapacityProvider {
    int capacity(UUID ownerId);
}

