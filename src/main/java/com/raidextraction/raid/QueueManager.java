package com.raidextraction.raid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class QueueManager {
    private final Map<String, Deque<UUID>> queueByRaid = new HashMap<>();
    private final Map<UUID, String> playerQueue = new HashMap<>();

    public boolean enqueue(String raidId, UUID playerId) {
        Objects.requireNonNull(raidId, "raidId");
        Objects.requireNonNull(playerId, "playerId");
        if (playerQueue.containsKey(playerId)) {
            return false;
        }
        queueByRaid.computeIfAbsent(raidId, key -> new ArrayDeque<>()).addLast(playerId);
        playerQueue.put(playerId, raidId);
        return true;
    }

    public boolean remove(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        String raidId = playerQueue.remove(playerId);
        if (raidId == null) {
            return false;
        }
        Deque<UUID> queue = queueByRaid.get(raidId);
        if (queue == null) {
            return false;
        }
        boolean removed = queue.remove(playerId);
        if (queue.isEmpty()) {
            queueByRaid.remove(raidId);
        }
        return removed;
    }

    public Optional<String> queuedRaid(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return Optional.ofNullable(playerQueue.get(playerId));
    }

    public int size(String raidId) {
        Objects.requireNonNull(raidId, "raidId");
        Deque<UUID> queue = queueByRaid.get(raidId);
        return queue == null ? 0 : queue.size();
    }

    public List<UUID> snapshot(String raidId) {
        Objects.requireNonNull(raidId, "raidId");
        Deque<UUID> queue = queueByRaid.get(raidId);
        if (queue == null || queue.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(queue));
    }

    public List<UUID> drain(String raidId, int maxCount) {
        Objects.requireNonNull(raidId, "raidId");
        if (maxCount <= 0) {
            return List.of();
        }
        Deque<UUID> queue = queueByRaid.get(raidId);
        if (queue == null || queue.isEmpty()) {
            return List.of();
        }
        List<UUID> drained = new ArrayList<>(Math.min(queue.size(), maxCount));
        while (!queue.isEmpty() && drained.size() < maxCount) {
            UUID playerId = queue.removeFirst();
            drained.add(playerId);
            playerQueue.remove(playerId);
        }
        if (queue.isEmpty()) {
            queueByRaid.remove(raidId);
        }
        return drained;
    }
}
