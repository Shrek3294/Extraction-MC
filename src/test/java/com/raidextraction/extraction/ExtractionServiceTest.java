package com.raidextraction.extraction;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExtractionServiceTest {

    @Test
    void extractionIsIdempotentAndBlocksSpam() {
        MutableClock clock = new MutableClock(Instant.EPOCH, ZoneOffset.UTC);
        EvacTracker evacTracker = new EvacTracker(clock);
        ExtractionService service = new ExtractionService(evacTracker);

        UUID playerId = UUID.randomUUID();
        String raidId = "raid-1";

        assertEquals(ExtractionService.ExtractionResult.STARTED,
                service.beginExtraction(raidId, playerId, "zone-a", Duration.ofSeconds(5)));
        assertEquals(ExtractionService.ExtractionResult.ALREADY_TRACKING,
                service.beginExtraction(raidId, playerId, "zone-a", Duration.ofSeconds(5)));
        assertEquals(ExtractionService.ExtractionResult.NOT_READY,
                service.completeIfReady(raidId, playerId));

        clock.advance(Duration.ofSeconds(5));
        assertEquals(ExtractionService.ExtractionResult.EXTRACTED,
                service.completeIfReady(raidId, playerId));
        assertEquals(ExtractionService.ExtractionResult.ALREADY_EXTRACTED,
                service.completeIfReady(raidId, playerId));
        assertEquals(ExtractionService.ExtractionResult.ALREADY_EXTRACTED,
                service.beginExtraction(raidId, playerId, "zone-a", Duration.ofSeconds(5)));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
