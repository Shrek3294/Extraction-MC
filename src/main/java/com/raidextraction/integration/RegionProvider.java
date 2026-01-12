package com.raidextraction.integration;

import com.raidextraction.config.model.EvacZoneDefinition;
import com.raidextraction.config.model.RaidDefinition;

import java.util.UUID;

/**
 * Adapter for region/position checks so Paper location lookups stay outside core services.
 */
public interface RegionProvider {

    /**
     * Determine whether the player is currently within the raid's playable world/region.
     */
    boolean isInRaidWorld(UUID playerId, RaidDefinition raidDefinition);

    /**
     * Determine whether the player is inside the configured evac zone.
     */
    boolean isInEvacZone(UUID playerId, EvacZoneDefinition evacZoneDefinition);
}
