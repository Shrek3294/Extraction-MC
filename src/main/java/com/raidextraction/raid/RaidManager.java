package com.raidextraction.raid;

import com.raidextraction.config.model.RaidDefinition;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class RaidManager {
    private final Map<String, RaidDefinition> definitions;
    private final Map<String, RaidInstance> activeRaids = new HashMap<>();
    private final QueueManager queueManager;
    private final Clock clock;

    public RaidManager(Map<String, RaidDefinition> definitions, QueueManager queueManager, Clock clock) {
        this.definitions = Collections.unmodifiableMap(new HashMap<>(Objects.requireNonNull(definitions, "definitions")));
        this.queueManager = Objects.requireNonNull(queueManager, "queueManager");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Optional<RaidInstance> tryCreateFromQueue(String raidDefinitionId) {
        Objects.requireNonNull(raidDefinitionId, "raidDefinitionId");
        RaidDefinition definition = definitions.get(raidDefinitionId);
        if (definition == null) {
            return Optional.empty();
        }
        int queued = queueManager.size(raidDefinitionId);
        if (queued < definition.minPlayers()) {
            return Optional.empty();
        }
        int toDrain = Math.min(definition.maxPlayers(), queued);
        List<UUID> players = queueManager.drain(raidDefinitionId, toDrain);
        if (players.size() < definition.minPlayers()) {
            for (UUID playerId : players) {
                queueManager.enqueue(raidDefinitionId, playerId);
            }
            return Optional.empty();
        }
        return createRaid(definition, players);
    }

    public Optional<RaidInstance> createRaid(RaidDefinition definition, List<UUID> players) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(players, "players");
        if (!definitions.containsKey(definition.id())) {
            return Optional.empty();
        }
        List<UUID> uniquePlayers = new ArrayList<>(new java.util.LinkedHashSet<>(players));
        int count = uniquePlayers.size();
        if (count < definition.minPlayers() || count > definition.maxPlayers()) {
            return Optional.empty();
        }
        String raidId = UUID.randomUUID().toString();
        RaidInstance instance = new RaidInstance(raidId, definition, clock);
        uniquePlayers.forEach(instance::addPlayer);
        activeRaids.put(raidId, instance);
        return Optional.of(instance);
    }

    public Optional<RaidInstance> getRaid(String raidId) {
        Objects.requireNonNull(raidId, "raidId");
        return Optional.ofNullable(activeRaids.get(raidId));
    }

    public List<RaidInstance> activeRaids() {
        return List.copyOf(activeRaids.values());
    }

    public boolean endRaid(String raidId) {
        Objects.requireNonNull(raidId, "raidId");
        return activeRaids.remove(raidId) != null;
    }

    public Map<String, RaidDefinition> definitions() {
        return definitions;
    }
}
