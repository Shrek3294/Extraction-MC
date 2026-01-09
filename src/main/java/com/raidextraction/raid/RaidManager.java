package com.raidextraction.raid;

import com.raidextraction.config.model.RaidDefinition;

import java.time.Clock;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class RaidManager {
    private final Map<String, RaidInstance> instances = new LinkedHashMap<>();
    private final Clock clock;

    public RaidManager(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public RaidInstance createRaid(String raidId, RaidDefinition definition) {
        Objects.requireNonNull(raidId, "raidId");
        Objects.requireNonNull(definition, "definition");
        if (instances.containsKey(raidId)) {
            throw new IllegalArgumentException("Raid already exists: " + raidId);
        }
        RaidInstance instance = new RaidInstance(raidId, definition, clock);
        instances.put(raidId, instance);
        return instance;
    }

    public Optional<RaidInstance> findRaid(String raidId) {
        Objects.requireNonNull(raidId, "raidId");
        return Optional.ofNullable(instances.get(raidId));
    }

    public Collection<RaidInstance> allRaids() {
        return Collections.unmodifiableCollection(instances.values());
    }

    public void endRaid(String raidId) {
        Objects.requireNonNull(raidId, "raidId");
        RaidInstance instance = instances.get(raidId);
        if (instance == null) {
            return;
        }
        if (instance.state() != RaidState.ENDED) {
            instance.markEnded();
        }
        instances.remove(raidId);
    }
}
