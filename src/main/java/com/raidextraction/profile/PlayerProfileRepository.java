package com.raidextraction.profile;

import java.util.UUID;

public interface PlayerProfileRepository {
    PlayerProfile loadOrCreate(UUID playerId);

    PlayerProfile addCredits(UUID playerId, long delta);

    SpendResult trySpendCredits(UUID playerId, long amount);

    PlayerProfile setHudEnabled(UUID playerId, boolean enabled);

    AwardXpResult awardExtractionXp(String raidId, UUID playerId, long xpDelta, int xpPerLevel);

    record SpendResult(boolean spent, PlayerProfile profile) {
    }

    record AwardXpResult(boolean awarded, PlayerProfile profile) {
    }
}

