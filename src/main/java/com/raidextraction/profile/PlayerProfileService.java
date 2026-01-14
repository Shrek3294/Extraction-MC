package com.raidextraction.profile;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerProfileService {
    private final PlayerProfileRepository repository;
    private final Map<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();

    public PlayerProfileService(PlayerProfileRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public Optional<PlayerProfile> cached(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return Optional.ofNullable(cache.get(playerId));
    }

    public PlayerProfile loadOrCreate(UUID playerId) {
        PlayerProfile profile = repository.loadOrCreate(playerId);
        cache.put(playerId, profile);
        return profile;
    }

    public PlayerProfile addCredits(UUID playerId, long delta) {
        PlayerProfile profile = repository.addCredits(playerId, delta);
        cache.put(playerId, profile);
        return profile;
    }

    public PlayerProfileRepository.SpendResult trySpendCredits(UUID playerId, long amount) {
        PlayerProfileRepository.SpendResult result = repository.trySpendCredits(playerId, amount);
        cache.put(playerId, result.profile());
        return result;
    }

    public PlayerProfile setHudEnabled(UUID playerId, boolean enabled) {
        PlayerProfile profile = repository.setHudEnabled(playerId, enabled);
        cache.put(playerId, profile);
        return profile;
    }

    public PlayerProfileRepository.AwardXpResult awardExtractionXp(String raidId, UUID playerId, long xpDelta, int xpPerLevel) {
        PlayerProfileRepository.AwardXpResult result = repository.awardExtractionXp(raidId, playerId, xpDelta, xpPerLevel);
        cache.put(playerId, result.profile());
        return result;
    }
}

