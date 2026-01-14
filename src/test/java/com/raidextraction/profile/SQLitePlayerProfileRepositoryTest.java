package com.raidextraction.profile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLitePlayerProfileRepositoryTest {

    @Test
    void creditsPersistAcrossReloads(@TempDir Path tempDir) {
        Path database = tempDir.resolve("profiles.db");
        PlayerProfileService service = new PlayerProfileService(new SQLitePlayerProfileRepository(database));
        UUID playerId = UUID.randomUUID();

        service.addCredits(playerId, 125);

        PlayerProfileService reloaded = new PlayerProfileService(new SQLitePlayerProfileRepository(database));
        assertEquals(125, reloaded.loadOrCreate(playerId).credits());
    }

    @Test
    void extractionXpIsIdempotentPerRaid(@TempDir Path tempDir) {
        Path database = tempDir.resolve("profiles.db");
        PlayerProfileService service = new PlayerProfileService(new SQLitePlayerProfileRepository(database));
        UUID playerId = UUID.randomUUID();

        PlayerProfileRepository.AwardXpResult first = service.awardExtractionXp("raid-1", playerId, 50, 100);
        assertTrue(first.awarded());
        assertEquals(50, first.profile().xp());
        assertEquals(1, first.profile().level());

        PlayerProfileRepository.AwardXpResult duplicate = service.awardExtractionXp("raid-1", playerId, 50, 100);
        assertFalse(duplicate.awarded());
        assertEquals(50, duplicate.profile().xp());
        assertEquals(1, duplicate.profile().level());

        PlayerProfileRepository.AwardXpResult secondRaid = service.awardExtractionXp("raid-2", playerId, 75, 100);
        assertTrue(secondRaid.awarded());
        assertEquals(125, secondRaid.profile().xp());
        assertEquals(2, secondRaid.profile().level());
    }
}

