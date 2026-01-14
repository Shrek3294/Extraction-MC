package com.raidextraction.profile;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class SQLitePlayerProfileRepository implements PlayerProfileRepository {
    private static final String EVENT_EXTRACTION_XP = "extract_xp";

    private final String jdbcUrl;

    public SQLitePlayerProfileRepository(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        this.jdbcUrl = "jdbc:sqlite:" + databasePath.toAbsolutePath();
        ensureSchema();
    }

    @Override
    public PlayerProfile loadOrCreate(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        try (Connection connection = openConnection()) {
            ensureProfileRow(connection, playerId);
            return loadProfile(connection, playerId);
        } catch (SQLException error) {
            throw new IllegalStateException("Failed to load profile for " + playerId, error);
        }
    }

    @Override
    public PlayerProfile addCredits(UUID playerId, long delta) {
        Objects.requireNonNull(playerId, "playerId");
        if (delta == 0) {
            return loadOrCreate(playerId);
        }
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            ensureProfileRow(connection, playerId);
            long updatedAt = Instant.now().toEpochMilli();
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE player_profiles SET credits = MAX(0, credits + ?), updated_at_ms = ? WHERE player_uuid = ?")) {
                update.setLong(1, delta);
                update.setLong(2, updatedAt);
                update.setString(3, playerId.toString());
                update.executeUpdate();
            }
            PlayerProfile profile = loadProfile(connection, playerId);
            connection.commit();
            return profile;
        } catch (SQLException error) {
            throw new IllegalStateException("Failed to update credits for " + playerId, error);
        }
    }

    @Override
    public SpendResult trySpendCredits(UUID playerId, long amount) {
        Objects.requireNonNull(playerId, "playerId");
        if (amount <= 0) {
            return new SpendResult(true, loadOrCreate(playerId));
        }
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            ensureProfileRow(connection, playerId);
            long updatedAt = Instant.now().toEpochMilli();
            int updated;
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE player_profiles SET credits = credits - ?, updated_at_ms = ? WHERE player_uuid = ? AND credits >= ?")) {
                update.setLong(1, amount);
                update.setLong(2, updatedAt);
                update.setString(3, playerId.toString());
                update.setLong(4, amount);
                updated = update.executeUpdate();
            }
            PlayerProfile profile = loadProfile(connection, playerId);
            connection.commit();
            return new SpendResult(updated > 0, profile);
        } catch (SQLException error) {
            throw new IllegalStateException("Failed to spend credits for " + playerId, error);
        }
    }

    @Override
    public PlayerProfile setHudEnabled(UUID playerId, boolean enabled) {
        Objects.requireNonNull(playerId, "playerId");
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            ensureProfileRow(connection, playerId);
            long updatedAt = Instant.now().toEpochMilli();
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE player_profiles SET hud_enabled = ?, updated_at_ms = ? WHERE player_uuid = ?")) {
                update.setInt(1, enabled ? 1 : 0);
                update.setLong(2, updatedAt);
                update.setString(3, playerId.toString());
                update.executeUpdate();
            }
            PlayerProfile profile = loadProfile(connection, playerId);
            connection.commit();
            return profile;
        } catch (SQLException error) {
            throw new IllegalStateException("Failed to update HUD enabled for " + playerId, error);
        }
    }

    @Override
    public AwardXpResult awardExtractionXp(String raidId, UUID playerId, long xpDelta, int xpPerLevel) {
        Objects.requireNonNull(raidId, "raidId");
        Objects.requireNonNull(playerId, "playerId");
        if (xpDelta <= 0) {
            return new AwardXpResult(false, loadOrCreate(playerId));
        }
        int safeXpPerLevel = Math.max(1, xpPerLevel);
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            ensureProfileRow(connection, playerId);

            boolean awarded;
            long now = Instant.now().toEpochMilli();
            try (PreparedStatement insertEvent = connection.prepareStatement(
                    "INSERT OR IGNORE INTO profile_events (raid_id, player_uuid, kind, created_at_ms) VALUES (?, ?, ?, ?)")) {
                insertEvent.setString(1, raidId);
                insertEvent.setString(2, playerId.toString());
                insertEvent.setString(3, EVENT_EXTRACTION_XP);
                insertEvent.setLong(4, now);
                awarded = insertEvent.executeUpdate() > 0;
            }

            if (awarded) {
                long currentXp = loadXp(connection, playerId);
                long nextXp = Math.max(0, currentXp + xpDelta);
                int nextLevel = computeLevel(nextXp, safeXpPerLevel);
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE player_profiles SET xp = ?, level = ?, updated_at_ms = ? WHERE player_uuid = ?")) {
                    update.setLong(1, nextXp);
                    update.setInt(2, nextLevel);
                    update.setLong(3, now);
                    update.setString(4, playerId.toString());
                    update.executeUpdate();
                }
            }

            PlayerProfile profile = loadProfile(connection, playerId);
            connection.commit();
            return new AwardXpResult(awarded, profile);
        } catch (SQLException error) {
            throw new IllegalStateException("Failed to award extraction XP for " + raidId + ":" + playerId, error);
        }
    }

    private void ensureSchema() {
        String profilesSql = """
                CREATE TABLE IF NOT EXISTS player_profiles (
                    player_uuid TEXT PRIMARY KEY,
                    credits INTEGER NOT NULL DEFAULT 0,
                    xp INTEGER NOT NULL DEFAULT 0,
                    level INTEGER NOT NULL DEFAULT 1,
                    hud_enabled INTEGER NOT NULL DEFAULT 1,
                    updated_at_ms INTEGER NOT NULL DEFAULT 0
                )
                """;
        String eventsSql = """
                CREATE TABLE IF NOT EXISTS profile_events (
                    raid_id TEXT NOT NULL,
                    player_uuid TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    created_at_ms INTEGER NOT NULL,
                    PRIMARY KEY (raid_id, player_uuid, kind)
                )
                """;
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(profilesSql);
            statement.execute(eventsSql);
        } catch (SQLException error) {
            throw new IllegalStateException("Failed to ensure profile schema", error);
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private void ensureProfileRow(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT OR IGNORE INTO player_profiles (player_uuid) VALUES (?)")) {
            insert.setString(1, playerId.toString());
            insert.executeUpdate();
        }
    }

    private PlayerProfile loadProfile(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT credits, xp, level, hud_enabled FROM player_profiles WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return new PlayerProfile(playerId, 0, 0, 1, true);
                }
                long credits = Math.max(0, rs.getLong("credits"));
                long xp = Math.max(0, rs.getLong("xp"));
                int level = Math.max(1, rs.getInt("level"));
                boolean hudEnabled = rs.getInt("hud_enabled") != 0;
                return new PlayerProfile(playerId, credits, xp, level, hudEnabled);
            }
        }
    }

    private long loadXp(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT xp FROM player_profiles WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return 0L;
                }
                return Math.max(0, rs.getLong("xp"));
            }
        }
    }

    private int computeLevel(long xp, int xpPerLevel) {
        long safeXp = Math.max(0, xp);
        int safe = Math.max(1, xpPerLevel);
        long level = (safeXp / safe) + 1;
        if (level > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) level;
    }
}

