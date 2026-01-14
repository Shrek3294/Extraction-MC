package com.raidextraction.stash;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StashServiceCapacityTest {

    @Test
    void addItemsHonorsCapacityAndReturnsOverflow() {
        UUID playerId = UUID.randomUUID();
        InMemoryStashRepository repository = new InMemoryStashRepository();
        StashService service = new StashService(repository, new FixedStashCapacityProvider(2));

        StashService.AppendResult result = service.addItems(playerId, List.of(
                new ItemData("DIAMOND", 1),
                new ItemData("IRON_INGOT", 1),
                new ItemData("GOLD_INGOT", 1)));

        assertEquals(2, result.added().size());
        assertEquals(1, result.overflow().size());
        assertEquals(2, result.stash().size());
        assertEquals(2, service.load(playerId).size());
    }

    @Test
    void addItemsDoesNotShrinkLegacyOverCapacityStashes() {
        UUID playerId = UUID.randomUUID();
        InMemoryStashRepository repository = new InMemoryStashRepository();
        repository.saveStash(playerId, List.of(
                new ItemData("DIAMOND", 1),
                new ItemData("IRON_INGOT", 1),
                new ItemData("GOLD_INGOT", 1)));

        StashService service = new StashService(repository, new FixedStashCapacityProvider(2));
        StashService.AppendResult result = service.addItems(playerId, List.of(new ItemData("EMERALD", 1)));

        assertEquals(0, result.added().size());
        assertEquals(1, result.overflow().size());
        assertEquals(3, service.load(playerId).size());
    }

    @Test
    void forceAddItemsIgnoresCapacity() {
        UUID playerId = UUID.randomUUID();
        InMemoryStashRepository repository = new InMemoryStashRepository();
        StashService service = new StashService(repository, new FixedStashCapacityProvider(1));

        service.forceAddItems(playerId, List.of(
                new ItemData("DIAMOND", 1),
                new ItemData("IRON_INGOT", 1)));

        assertEquals(2, service.load(playerId).size());
    }

    private static final class InMemoryStashRepository implements StashRepository {
        private final Map<UUID, List<ItemData>> byOwner = new HashMap<>();

        @Override
        public List<ItemData> loadStash(UUID ownerId) {
            return List.copyOf(byOwner.getOrDefault(ownerId, List.of()));
        }

        @Override
        public void saveStash(UUID ownerId, List<ItemData> items) {
            byOwner.put(ownerId, new ArrayList<>(items));
        }

        @Override
        public void clearStash(UUID ownerId) {
            byOwner.remove(ownerId);
        }
    }
}

