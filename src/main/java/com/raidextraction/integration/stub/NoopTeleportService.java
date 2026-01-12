package com.raidextraction.integration.stub;

import com.raidextraction.config.model.RaidDefinition;
import com.raidextraction.integration.TeleportService;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

public final class NoopTeleportService implements TeleportService {
    private final Logger logger;

    public NoopTeleportService(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public boolean sendToRaid(UUID playerId, RaidDefinition raidDefinition) {
        logStub("raid", playerId, raidDefinition.id());
        return false;
    }

    @Override
    public boolean sendToLobby(UUID playerId, String lobbyWorld) {
        logStub("lobby", playerId, lobbyWorld);
        return false;
    }

    private void logStub(String destination, UUID playerId, String target) {
        logger.fine(() -> "TeleportService stub ignoring " + destination + " teleport for player " + playerId + " (" + target + ")");
    }
}
