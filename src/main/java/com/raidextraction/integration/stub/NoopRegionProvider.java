package com.raidextraction.integration.stub;

import com.raidextraction.config.model.EvacZoneDefinition;
import com.raidextraction.config.model.RaidBoundsDefinition;
import com.raidextraction.config.model.RaidDefinition;
import com.raidextraction.integration.RegionProvider;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

public final class NoopRegionProvider implements RegionProvider {
    private final Logger logger;

    public NoopRegionProvider(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public boolean isInRaidWorld(UUID playerId, RaidDefinition raidDefinition) {
        logStub("raid region check", playerId, raidDefinition.id());
        return false;
    }

    @Override
    public boolean isInEvacZone(UUID playerId, EvacZoneDefinition evacZoneDefinition) {
        logStub("evac zone check", playerId, evacZoneDefinition.name());
        return false;
    }

    @Override
    public boolean isInRaidBounds(UUID playerId, RaidBoundsDefinition raidBoundsDefinition) {
        if (raidBoundsDefinition == null) {
            return true;
        }
        logStub("raid bounds check", playerId, raidBoundsDefinition.world());
        return true; // Stub always returns true
    }

    private void logStub(String action, UUID playerId, String target) {
        logger.fine(() -> "RegionProvider stub ignoring " + action + " for player " + playerId + " on " + target);
    }
}
