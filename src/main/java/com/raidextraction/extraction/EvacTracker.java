package com.raidextraction.extraction;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class EvacTracker {
    private final Clock clock;
    private final Map<EvacKey, EvacEntry> activeEntries = new HashMap<>();

    public EvacTracker(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public boolean startCountdown(String raidId, UUID playerId, String zoneName, Duration duration) {
        Objects.requireNonNull(raidId, "raidId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(zoneName, "zoneName");
        Objects.requireNonNull(duration, "duration");
        if (duration.isZero() || duration.isNegative()) {
            return false;
        }
        EvacKey key = new EvacKey(raidId, playerId);
        if (activeEntries.containsKey(key)) {
            return false;
        }
        activeEntries.put(key, new EvacEntry(zoneName, Instant.now(clock), duration));
        return true;
    }

    public void cancelCountdown(String raidId, UUID playerId) {
        Objects.requireNonNull(raidId, "raidId");
        Objects.requireNonNull(playerId, "playerId");
        activeEntries.remove(new EvacKey(raidId, playerId));
    }

    public Optional<EvacEntry> entry(String raidId, UUID playerId) {
        Objects.requireNonNull(raidId, "raidId");
        Objects.requireNonNull(playerId, "playerId");
        return Optional.ofNullable(activeEntries.get(new EvacKey(raidId, playerId)));
    }

    public boolean isComplete(String raidId, UUID playerId) {
        return entry(raidId, playerId).map(entry -> !entry.remaining(clock).isPositive()).orElse(false);
    }

    public Duration remaining(String raidId, UUID playerId) {
        return entry(raidId, playerId)
                .map(entry -> entry.remaining(clock))
                .orElse(Duration.ZERO);
    }

    public void clearRaid(String raidId) {
        Objects.requireNonNull(raidId, "raidId");
        activeEntries.keySet().removeIf(key -> key.raidId.equals(raidId));
    }

    public record EvacEntry(String zoneName, Instant startedAt, Duration duration) {
        public EvacEntry {
            Objects.requireNonNull(zoneName, "zoneName");
            Objects.requireNonNull(startedAt, "startedAt");
            Objects.requireNonNull(duration, "duration");
        }

        public Duration remaining(Clock clock) {
            Duration elapsed = Duration.between(startedAt, Instant.now(clock));
            Duration remaining = duration.minus(elapsed);
            return remaining.isNegative() ? Duration.ZERO : remaining;
        }
    }

    private record EvacKey(String raidId, UUID playerId) {
        private EvacKey {
            Objects.requireNonNull(raidId, "raidId");
            Objects.requireNonNull(playerId, "playerId");
        }
    }
}
