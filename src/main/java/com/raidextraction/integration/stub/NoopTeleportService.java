package com.raidextraction.integration.stub;

import com.raidextraction.config.model.LobbySpawnConfig;
import com.raidextraction.config.model.RaidDefinition;
import com.raidextraction.integration.TeleportService;

import java.util.Objects;
import java.util.UUID;

public final class NoopTeleportService implements TeleportService {

    @Override
    public boolean sendToRaid(UUID playerId, RaidDefinition raidDefinition) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(raidDefinition, "raidDefinition");
        return false;
    }

    @Override
    public boolean sendToLobby(UUID playerId, LobbySpawnConfig lobbySpawnConfig) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(lobbySpawnConfig, "lobbySpawnConfig");
        return false;
    }
}
