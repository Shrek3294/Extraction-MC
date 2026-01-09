package com.raidextraction.stash;

import java.util.List;
import java.util.UUID;

public interface StashRepository {
    List<ItemData> loadStash(UUID ownerId);

    void saveStash(UUID ownerId, List<ItemData> items);

    void clearStash(UUID ownerId);
}
