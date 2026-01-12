package com.raidextraction.stash;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SQLiteStashRepositoryTest {

    @Test
    void stashPersistsAcrossReloads(@TempDir Path tempDir) {
        Path database = tempDir.resolve("stash.db");
        StashService stashService = new StashService(new SQLiteStashRepository(database));
        UUID playerId = UUID.randomUUID();

        List<ItemData> items = List.of(
                new ItemData("DIAMOND", 3),
                new ItemData("IRON_INGOT", 12));

        stashService.replace(playerId, items);

        StashService reloadedService = new StashService(new SQLiteStashRepository(database));
        assertEquals(items, reloadedService.load(playerId));
    }
}
