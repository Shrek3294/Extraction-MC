package com.raidextraction.stash;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class StashService {
    private final StashRepository repository;

    public StashService(StashRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public List<ItemData> load(UUID ownerId) {
        return repository.loadStash(ownerId);
    }

    public void replace(UUID ownerId, List<ItemData> items) {
        repository.saveStash(ownerId, items);
    }

    public void clear(UUID ownerId) {
        repository.clearStash(ownerId);
    }

    public void addItem(UUID ownerId, ItemData itemData) {
        Objects.requireNonNull(itemData, "itemData");
        List<ItemData> items = new ArrayList<>(repository.loadStash(ownerId));
        items.add(itemData);
        repository.saveStash(ownerId, items);
    }

    public boolean removeItem(UUID ownerId, ItemData itemData) {
        Objects.requireNonNull(itemData, "itemData");
        List<ItemData> items = new ArrayList<>(repository.loadStash(ownerId));
        boolean removed = items.remove(itemData);
        if (removed) {
            repository.saveStash(ownerId, items);
        }
        return removed;
    }
}
