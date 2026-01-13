package com.raidextraction.integration;

import com.raidextraction.config.model.LobbySpawnConfig;
import com.raidextraction.config.model.RaidDefinition;

import java.util.UUID;

/**
 * Adapter for teleportation requests so Paper API usage stays in one place.
 */
public interface TeleportService {

    /**
     * Teleport the player into the active raid instance (spawn selection handled by
     * the adapter).
     */
    boolean sendToRaid(UUID playerId, RaidDefinition raidDefinition);

    /**
     * Teleport the player back to the configured lobby spawn location.
     */
    boolean sendToLobby(UUID playerId, LobbySpawnConfig lobbySpawnConfig);
}
