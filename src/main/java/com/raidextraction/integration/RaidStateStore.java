package com.raidextraction.integration;

import com.raidextraction.extraction.EvacTracker;
import com.raidextraction.raid.RaidState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class RaidStateStore {
    private static final int SCHEMA_VERSION = 1;

    private final Path filePath;
    private final Logger logger;

    public RaidStateStore(Path filePath, Logger logger) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public Optional<RaidStateSnapshot> load() {
        if (!Files.exists(filePath)) {
            return Optional.empty();
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(filePath.toFile());
        int schemaVersion = config.getInt("schemaVersion", 0);
        if (schemaVersion != SCHEMA_VERSION) {
            logger.warning("Raid state schema mismatch. Expected " + SCHEMA_VERSION + " but found " + schemaVersion + ".");
        }
        Instant savedAt = parseInstant(config.getString("savedAt"), Instant.now());
        List<RaidSnapshot> raids = readRaids(config);
        List<EvacTracker.EvacSnapshot> evacSnapshots = readEvacSnapshots(config);
        return Optional.of(new RaidStateSnapshot(savedAt, raids, evacSnapshots));
    }

    public void save(RaidStateSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        YamlConfiguration config = new YamlConfiguration();
        config.set("schemaVersion", SCHEMA_VERSION);
        config.set("savedAt", snapshot.savedAt().toString());
        writeRaids(config, snapshot.raids());
        writeEvacSnapshots(config, snapshot.evacSnapshots());
        try {
            config.save(filePath.toFile());
        } catch (IOException error) {
            logger.log(Level.WARNING, "Failed to write raid state to " + filePath, error);
        }
    }

    public void clear() {
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException error) {
            logger.log(Level.WARNING, "Failed to delete raid state file at " + filePath, error);
        }
    }

    private List<RaidSnapshot> readRaids(YamlConfiguration config) {
        ConfigurationSection raidsSection = config.getConfigurationSection("raids");
        if (raidsSection == null) {
            return List.of();
        }
        List<RaidSnapshot> raids = new ArrayList<>();
        for (String raidId : raidsSection.getKeys(false)) {
            ConfigurationSection raidSection = raidsSection.getConfigurationSection(raidId);
            if (raidSection == null) {
                continue;
            }
            String definitionId = raidSection.getString("definitionId");
            RaidState state = parseState(raidSection.getString("state"));
            if (definitionId == null || definitionId.isBlank() || state == null) {
                logger.warning("Skipping raid state entry " + raidId + " due to missing definition or state.");
                continue;
            }
            Instant createdAt = parseInstant(raidSection.getString("createdAt"), Instant.now());
            Instant stateChangedAt = parseInstant(raidSection.getString("stateChangedAt"), createdAt);
            Instant deadline = parseInstant(raidSection.getString("raidDeadline"), null);
            Set<UUID> players = parseUuidSet(raidSection.getStringList("players"));
            raids.add(new RaidSnapshot(raidId, definitionId, state, createdAt, stateChangedAt, deadline, players));
        }
        return raids;
    }

    private void writeRaids(YamlConfiguration config, List<RaidSnapshot> raids) {
        if (raids == null || raids.isEmpty()) {
            return;
        }
        ConfigurationSection raidsSection = config.createSection("raids");
        for (RaidSnapshot raid : raids) {
            ConfigurationSection raidSection = raidsSection.createSection(raid.raidId());
            raidSection.set("definitionId", raid.definitionId());
            raidSection.set("state", raid.state().name());
            raidSection.set("createdAt", raid.createdAt().toString());
            raidSection.set("stateChangedAt", raid.stateChangedAt().toString());
            if (raid.raidDeadline() != null) {
                raidSection.set("raidDeadline", raid.raidDeadline().toString());
            }
            raidSection.set("players", raid.players().stream().map(UUID::toString).toList());
        }
    }

    private List<EvacTracker.EvacSnapshot> readEvacSnapshots(YamlConfiguration config) {
        List<Map<?, ?>> rawList = config.getMapList("evac");
        if (rawList == null || rawList.isEmpty()) {
            return List.of();
        }
        List<EvacTracker.EvacSnapshot> evacSnapshots = new ArrayList<>();
        for (Map<?, ?> entry : rawList) {
            if (entry == null) {
                continue;
            }
            String raidId = asString(entry.get("raidId"));
            String playerIdRaw = asString(entry.get("playerId"));
            String zoneName = asString(entry.get("zone"));
            String startedAtRaw = asString(entry.get("startedAt"));
            Long durationSeconds = asLong(entry.get("durationSeconds"));
            if (raidId == null || playerIdRaw == null || zoneName == null || startedAtRaw == null || durationSeconds == null) {
                continue;
            }
            UUID playerId = parseUuid(playerIdRaw);
            if (playerId == null) {
                continue;
            }
            Instant startedAt = parseInstant(startedAtRaw, null);
            if (startedAt == null) {
                continue;
            }
            evacSnapshots.add(new EvacTracker.EvacSnapshot(
                    raidId,
                    playerId,
                    zoneName,
                    startedAt,
                    Duration.ofSeconds(durationSeconds)));
        }
        return evacSnapshots;
    }

    private void writeEvacSnapshots(YamlConfiguration config, List<EvacTracker.EvacSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }
        List<Map<String, Object>> entries = new ArrayList<>(snapshots.size());
        for (EvacTracker.EvacSnapshot snapshot : snapshots) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("raidId", snapshot.raidId());
            entry.put("playerId", snapshot.playerId().toString());
            entry.put("zone", snapshot.zoneName());
            entry.put("startedAt", snapshot.startedAt().toString());
            entry.put("durationSeconds", snapshot.duration().getSeconds());
            entries.add(entry);
        }
        config.set("evac", entries);
    }

    private RaidState parseState(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return RaidState.valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            logger.warning("Unknown raid state in persistence: " + value);
            return null;
        }
    }

    private Instant parseInstant(String value, Instant fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            logger.warning("Invalid instant in persistence: " + value);
            return fallback;
        }
    }

    private Set<UUID> parseUuidSet(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<UUID> result = new LinkedHashSet<>();
        for (String value : values) {
            UUID uuid = parseUuid(value);
            if (uuid != null) {
                result.add(uuid);
            }
        }
        return result;
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String result = String.valueOf(value).trim();
        return result.isEmpty() ? null : result;
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public record RaidStateSnapshot(Instant savedAt,
                                    List<RaidSnapshot> raids,
                                    List<EvacTracker.EvacSnapshot> evacSnapshots) {
        public RaidStateSnapshot {
            Objects.requireNonNull(savedAt, "savedAt");
            raids = raids == null ? List.of() : List.copyOf(raids);
            evacSnapshots = evacSnapshots == null ? List.of() : List.copyOf(evacSnapshots);
        }
    }

    public record RaidSnapshot(String raidId,
                               String definitionId,
                               RaidState state,
                               Instant createdAt,
                               Instant stateChangedAt,
                               Instant raidDeadline,
                               Set<UUID> players) {
        public RaidSnapshot {
            Objects.requireNonNull(raidId, "raidId");
            Objects.requireNonNull(definitionId, "definitionId");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(stateChangedAt, "stateChangedAt");
            players = players == null ? Set.of() : Set.copyOf(players);
        }
    }
}
