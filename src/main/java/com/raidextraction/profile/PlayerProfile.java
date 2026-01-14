package com.raidextraction.profile;

import java.util.Objects;
import java.util.UUID;

public record PlayerProfile(
        UUID playerId,
        long credits,
        long xp,
        int level,
        boolean hudEnabled
) {
    public PlayerProfile {
        Objects.requireNonNull(playerId, "playerId");
        if (credits < 0) {
            throw new IllegalArgumentException("credits cannot be negative");
        }
        if (xp < 0) {
            throw new IllegalArgumentException("xp cannot be negative");
        }
        if (level < 1) {
            throw new IllegalArgumentException("level must be >= 1");
        }
    }
}

