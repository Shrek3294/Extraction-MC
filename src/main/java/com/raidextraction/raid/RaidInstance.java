package com.raidextraction.raid;

import com.raidextraction.config.model.RaidDefinition;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import com.raidextraction.stash.ItemData;

public final class RaidInstance {
    private final String id;
    private final RaidDefinition definition;
    private final Clock clock;
    private final Set<UUID> players;
    private final Instant createdAt;
    private RaidState state;
    private Instant stateChangedAt;

    public RaidInstance(String id, RaidDefinition definition, Clock clock) {
        Instant now = Instant.now(Objects.requireNonNull(clock, "clock"));
        this.id = Objects.requireNonNull(id, "id");
        this.definition = Objects.requireNonNull(definition, "definition");
        this.clock = clock;
        this.players = new LinkedHashSet<>();
        this.createdAt = now;
        this.state = RaidState.LOBBY;
        this.stateChangedAt = now;
    }

    public RaidInstance(String id,
            RaidDefinition definition,
            Clock clock,
            Instant createdAt,
            RaidState state,
            Instant stateChangedAt,
            Set<UUID> players) {
        this.id = Objects.requireNonNull(id, "id");
        this.definition = Objects.requireNonNull(definition, "definition");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.players = new LinkedHashSet<>(Objects.requireNonNullElse(players, Set.of()));
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.state = Objects.requireNonNullElse(state, RaidState.LOBBY);
        this.stateChangedAt = Objects.requireNonNullElse(stateChangedAt, this.createdAt);
    }

    public String id() {
        return id;
    }

    public RaidDefinition definition() {
        return definition;
    }

    public RaidState state() {
        return state;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant stateChangedAt() {
        return stateChangedAt;
    }

    public Set<UUID> players() {
        return Collections.unmodifiableSet(players);
    }

    public boolean addPlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (state != RaidState.LOBBY && state != RaidState.DEPLOYING) {
            return false;
        }
        if (players.size() >= definition.maxPlayers()) {
            return false;
        }
        return players.add(playerId);
    }

    public boolean removePlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return players.remove(playerId);
    }

    public boolean isReadyToDeploy() {
        int size = players.size();
        return size >= definition.minPlayers() && size <= definition.maxPlayers();
    }

    public void markDeploying() {
        transitionTo(RaidState.DEPLOYING);
    }

    public void markInRaid() {
        transitionTo(RaidState.IN_RAID);
    }

    public void markExtracting() {
        transitionTo(RaidState.EXTRACTING);
    }

    public void markEnded() {
        transitionTo(RaidState.ENDED);
    }

    public Instant raidEndsAt() {
        if (state != RaidState.IN_RAID) {
            return null;
        }
        return stateChangedAt.plusSeconds(definition.durationSeconds());
    }

    private final Map<String, List<ItemData>> pendingLoot = new HashMap<>();

    public void addPendingLoot(int x, int y, int z, List<ItemData> loot) {
        String key = formatLocationKey(x, y, z);
        pendingLoot.put(key, new ArrayList<>(loot));
    }

    public List<ItemData> getPendingLoot(int x, int y, int z) {
        String key = formatLocationKey(x, y, z);
        return pendingLoot.getOrDefault(key, Collections.emptyList());
    }

    public List<ItemData> takePendingLoot(int x, int y, int z) {
        String key = formatLocationKey(x, y, z);
        return pendingLoot.remove(key);
    }

    public boolean hasPendingLoot(int x, int y, int z) {
        return pendingLoot.containsKey(formatLocationKey(x, y, z));
    }

    public int getPendingLootMapSize() {
        return pendingLoot.size();
    }

    private String formatLocationKey(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    public void transitionTo(RaidState nextState) {
        Objects.requireNonNull(nextState, "nextState");
        if (state == nextState) {
            return;
        }
        if (!state.canTransitionTo(nextState)) {
            throw new IllegalStateException("Invalid raid state transition: " + state + " -> " + nextState);
        }
        state = nextState;
        stateChangedAt = Instant.now(clock);
    }
}
