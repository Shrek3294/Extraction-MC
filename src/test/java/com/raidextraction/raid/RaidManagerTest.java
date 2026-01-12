package com.raidextraction.raid;

import com.raidextraction.config.model.RaidDefinition;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaidManagerTest {

    @Test
    void enforcesMinMaxAcrossMultipleDefinitions() {
        RaidDefinition alpha = new RaidDefinition("alpha", "world", 2, 4, 300, "loot", List.of());
        RaidDefinition beta = new RaidDefinition("beta", "world", 1, 2, 300, "loot", List.of());
        QueueManager queueManager = new QueueManager();
        RaidManager raidManager = new RaidManager(
                Map.of(alpha.id(), alpha, beta.id(), beta),
                queueManager,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        UUID alphaOne = UUID.randomUUID();
        UUID alphaTwo = UUID.randomUUID();
        queueManager.enqueue(alpha.id(), alphaOne);
        assertTrue(raidManager.tryCreateFromQueue(alpha.id()).isEmpty());
        queueManager.enqueue(alpha.id(), alphaTwo);
        Optional<RaidInstance> alphaRaid = raidManager.tryCreateFromQueue(alpha.id());
        assertTrue(alphaRaid.isPresent());
        assertEquals(2, alphaRaid.get().players().size());
        assertEquals(0, queueManager.size(alpha.id()));

        UUID betaOne = UUID.randomUUID();
        UUID betaTwo = UUID.randomUUID();
        UUID betaThree = UUID.randomUUID();
        queueManager.enqueue(beta.id(), betaOne);
        queueManager.enqueue(beta.id(), betaTwo);
        queueManager.enqueue(beta.id(), betaThree);
        Optional<RaidInstance> betaRaid = raidManager.tryCreateFromQueue(beta.id());
        assertTrue(betaRaid.isPresent());
        assertEquals(2, betaRaid.get().players().size());
        assertEquals(1, queueManager.size(beta.id()));
    }
}
