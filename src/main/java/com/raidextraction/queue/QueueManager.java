package com.raidextraction.queue;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class QueueManager {
    private final Deque<QueuedPlayer> queue = new ArrayDeque<>();
    private final Clock clock;

    public QueueManager(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public boolean enqueue(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (contains(playerId)) {
            return false;
        }
        return queue.add(new QueuedPlayer(playerId, Instant.now(clock)));
    }

    public boolean dequeue(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return queue.removeIf(player -> player.playerId().equals(playerId));
    }

    public boolean contains(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return queue.stream().anyMatch(player -> player.playerId().equals(playerId));
    }

    public List<QueuedPlayer> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(queue));
    }

    public List<UUID> popNextGroup(int maxPlayers) {
        if (maxPlayers <= 0) {
            return List.of();
        }
        List<UUID> group = new ArrayList<>(maxPlayers);
        while (!queue.isEmpty() && group.size() < maxPlayers) {
            group.add(queue.removeFirst().playerId());
        }
        return group;
    }

    public record QueuedPlayer(UUID playerId, Instant queuedAt) {
        public QueuedPlayer {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(queuedAt, "queuedAt");
        }
    }
}
