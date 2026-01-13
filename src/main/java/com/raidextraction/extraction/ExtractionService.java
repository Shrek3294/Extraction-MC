package com.raidextraction.extraction;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ExtractionService {
    private final EvacTracker evacTracker;
    private final Clock clock;
    private final Map<String, Set<UUID>> extractedPlayers = new HashMap<>();
    private final Map<String, Set<UUID>> extractionHandled = new HashMap<>();
    private final Map<UUID, Instant> extractionAttemptCooldowns = new HashMap<>();
    private final Map<UUID, Instant> commandCooldowns = new HashMap<>();
    private final Duration extractionAttemptCooldown;
    private final Duration commandCooldown;

    public ExtractionService(EvacTracker evacTracker) {
        this(evacTracker, Clock.systemUTC());
    }

    public ExtractionService(EvacTracker evacTracker, Clock clock) {
        this.evacTracker = Objects.requireNonNull(evacTracker, "evacTracker");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.extractionAttemptCooldown = Duration.ofSeconds(1);
        this.commandCooldown = Duration.ofSeconds(2);
    }

    public ExtractionResult beginExtraction(String raidId, UUID playerId, String zoneName, Duration duration) {
        Objects.requireNonNull(raidId, "raidId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(zoneName, "zoneName");
        Objects.requireNonNull(duration, "duration");
        if (isExtracted(raidId, playerId)) {
            return ExtractionResult.ALREADY_EXTRACTED;
        }
        if (evacTracker.entry(raidId, playerId).isPresent()) {
            return ExtractionResult.ALREADY_TRACKING;
        }
        if (!checkCooldown(extractionAttemptCooldowns, playerId, extractionAttemptCooldown).allowed()) {
            return ExtractionResult.COOLDOWN;
        }
        boolean started = evacTracker.startCountdown(raidId, playerId, zoneName, duration);
        return started ? ExtractionResult.STARTED : ExtractionResult.ALREADY_TRACKING;
    }

    public ExtractionResult completeIfReady(String raidId, UUID playerId) {
        Objects.requireNonNull(raidId, "raidId");
        Objects.requireNonNull(playerId, "playerId");
        if (isExtracted(raidId, playerId)) {
            return ExtractionResult.ALREADY_EXTRACTED;
        }
        if (!evacTracker.isComplete(raidId, playerId)) {
            return ExtractionResult.NOT_READY;
        }
        evacTracker.cancelCountdown(raidId, playerId);
        extractedPlayers.computeIfAbsent(raidId, key -> new HashSet<>()).add(playerId);
        return ExtractionResult.EXTRACTED;
    }

    public void forceComplete(String raidId, UUID playerId) {
        Objects.requireNonNull(raidId, "raidId");
        Objects.requireNonNull(playerId, "playerId");
        evacTracker.cancelCountdown(raidId, playerId);
        extractedPlayers.computeIfAbsent(raidId, key -> new HashSet<>()).add(playerId);
    }

    public void cancelExtraction(String raidId, UUID playerId) {
        Objects.requireNonNull(raidId, "raidId");
        Objects.requireNonNull(playerId, "playerId");
        evacTracker.cancelCountdown(raidId, playerId);
    }

    public boolean isExtracted(String raidId, UUID playerId) {
        Objects.requireNonNull(raidId, "raidId");
        Objects.requireNonNull(playerId, "playerId");
        Set<UUID> extracted = extractedPlayers.get(raidId);
        return extracted != null && extracted.contains(playerId);
    }

    public boolean markExtractionHandled(String raidId, UUID playerId) {
        Objects.requireNonNull(raidId, "raidId");
        Objects.requireNonNull(playerId, "playerId");
        return extractionHandled.computeIfAbsent(raidId, key -> new HashSet<>()).add(playerId);
    }

    public CooldownResult checkCommandCooldown(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return checkCooldown(commandCooldowns, playerId, commandCooldown);
    }

    public Duration remaining(String raidId, UUID playerId) {
        Objects.requireNonNull(raidId, "raidId");
        Objects.requireNonNull(playerId, "playerId");
        return evacTracker.remaining(raidId, playerId);
    }

    public Set<UUID> extractedPlayers(String raidId) {
        Objects.requireNonNull(raidId, "raidId");
        Set<UUID> extracted = extractedPlayers.get(raidId);
        if (extracted == null || extracted.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(extracted);
    }

    public void clearRaid(String raidId) {
        Objects.requireNonNull(raidId, "raidId");
        evacTracker.clearRaid(raidId);
        extractedPlayers.remove(raidId);
        extractionHandled.remove(raidId);
    }

    public List<EvacTracker.EvacSnapshot> evacSnapshots() {
        return evacTracker.snapshots();
    }

    public void restoreEvacSnapshots(List<EvacTracker.EvacSnapshot> snapshots) {
        evacTracker.restoreSnapshots(snapshots);
    }

    public enum ExtractionResult {
        STARTED,
        ALREADY_TRACKING,
        NOT_READY,
        EXTRACTED,
        ALREADY_EXTRACTED,
        COOLDOWN
    }

    public record CooldownResult(boolean allowed, Duration remaining) {
    }

    private CooldownResult checkCooldown(Map<UUID, Instant> cooldowns, UUID playerId, Duration cooldown) {
        Instant now = Instant.now(clock);
        Instant last = cooldowns.get(playerId);
        if (last != null) {
            Duration elapsed = Duration.between(last, now);
            if (elapsed.compareTo(cooldown) < 0) {
                return new CooldownResult(false, cooldown.minus(elapsed));
            }
        }
        cooldowns.put(playerId, now);
        return new CooldownResult(true, Duration.ZERO);
    }
}
