package com.raidextraction.extraction;

import java.time.Duration;
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
    private final Map<String, Set<UUID>> extractedPlayers = new HashMap<>();

    public ExtractionService(EvacTracker evacTracker) {
        this.evacTracker = Objects.requireNonNull(evacTracker, "evacTracker");
    }

    public ExtractionResult beginExtraction(String raidId, UUID playerId, String zoneName, Duration duration) {
        Objects.requireNonNull(raidId, "raidId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(zoneName, "zoneName");
        Objects.requireNonNull(duration, "duration");
        if (isExtracted(raidId, playerId)) {
            return ExtractionResult.ALREADY_EXTRACTED;
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
        ALREADY_EXTRACTED
    }
}
