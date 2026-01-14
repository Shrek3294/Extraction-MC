package com.raidextraction.stash;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class StashService {
    private final StashRepository repository;
    private final StashCapacityProvider capacityProvider;

    public record AppendResult(List<ItemData> stash, List<ItemData> added, List<ItemData> overflow, int capacity) {
        public AppendResult {
            Objects.requireNonNull(stash, "stash");
            Objects.requireNonNull(added, "added");
            Objects.requireNonNull(overflow, "overflow");
            if (capacity < 0) {
                throw new IllegalArgumentException("capacity must be non-negative");
            }
        }
    }

    public StashService(StashRepository repository, StashCapacityProvider capacityProvider) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.capacityProvider = Objects.requireNonNull(capacityProvider, "capacityProvider");
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

    public int capacity(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        return Math.max(0, capacityProvider.capacity(ownerId));
    }

    public AppendResult addItems(UUID ownerId, List<ItemData> itemsToAdd) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(itemsToAdd, "itemsToAdd");
        if (itemsToAdd.isEmpty()) {
            List<ItemData> stash = repository.loadStash(ownerId);
            return new AppendResult(stash, List.of(), List.of(), capacity(ownerId));
        }

        int capacity = capacity(ownerId);
        List<ItemData> current = new ArrayList<>(repository.loadStash(ownerId));
        int available = Math.max(0, capacity - current.size());

        List<ItemData> added = new ArrayList<>();
        List<ItemData> overflow = new ArrayList<>();
        for (ItemData itemData : itemsToAdd) {
            if (available > 0) {
                current.add(itemData);
                added.add(itemData);
                available--;
            } else {
                overflow.add(itemData);
            }
        }

        if (!added.isEmpty()) {
            repository.saveStash(ownerId, current);
        }
        return new AppendResult(List.copyOf(current), List.copyOf(added), List.copyOf(overflow), capacity);
    }

    public void forceAddItems(UUID ownerId, List<ItemData> itemsToAdd) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(itemsToAdd, "itemsToAdd");
        if (itemsToAdd.isEmpty()) {
            return;
        }
        List<ItemData> current = new ArrayList<>(repository.loadStash(ownerId));
        current.addAll(itemsToAdd);
        repository.saveStash(ownerId, current);
    }

    public void addItem(UUID ownerId, ItemData itemData) {
        Objects.requireNonNull(itemData, "itemData");
        addItems(ownerId, List.of(itemData));
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
