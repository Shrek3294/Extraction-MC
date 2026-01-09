package com.raidextraction.stash;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public final class SQLiteStashRepository implements StashRepository {
    private final String jdbcUrl;

    public SQLiteStashRepository(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        this.jdbcUrl = "jdbc:sqlite:" + databasePath.toAbsolutePath();
        ensureSchema();
    }

    @Override
    public List<ItemData> loadStash(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        String sql = "SELECT slot, material, amount, tags FROM stash_items WHERE owner_uuid = ? ORDER BY slot ASC";
        List<ItemData> items = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ownerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String material = resultSet.getString("material");
                    int amount = resultSet.getInt("amount");
                    String tagsValue = resultSet.getString("tags");
                    items.add(new ItemData(material, amount, deserializeTags(tagsValue)));
                }
            }
        } catch (SQLException error) {
            throw new IllegalStateException("Failed to load stash for " + ownerId, error);
        }
        return Collections.unmodifiableList(items);
    }

    @Override
    public void saveStash(UUID ownerId, List<ItemData> items) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(items, "items");
        String deleteSql = "DELETE FROM stash_items WHERE owner_uuid = ?";
        String insertSql = "INSERT INTO stash_items (owner_uuid, slot, material, amount, tags) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement deleteStatement = connection.prepareStatement(deleteSql)) {
                deleteStatement.setString(1, ownerId.toString());
                deleteStatement.executeUpdate();
            }
            try (PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {
                int slot = 0;
                for (ItemData item : items) {
                    insertStatement.setString(1, ownerId.toString());
                    insertStatement.setInt(2, slot++);
                    insertStatement.setString(3, item.material());
                    insertStatement.setInt(4, item.amount());
                    insertStatement.setString(5, serializeTags(item.tags()));
                    insertStatement.addBatch();
                }
                insertStatement.executeBatch();
            }
            connection.commit();
        } catch (SQLException error) {
            throw new IllegalStateException("Failed to save stash for " + ownerId, error);
        }
    }

    @Override
    public void clearStash(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        String deleteSql = "DELETE FROM stash_items WHERE owner_uuid = ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(deleteSql)) {
            statement.setString(1, ownerId.toString());
            statement.executeUpdate();
        } catch (SQLException error) {
            throw new IllegalStateException("Failed to clear stash for " + ownerId, error);
        }
    }

    private void ensureSchema() {
        String sql = """
                CREATE TABLE IF NOT EXISTS stash_items (
                    owner_uuid TEXT NOT NULL,
                    slot INTEGER NOT NULL,
                    material TEXT NOT NULL,
                    amount INTEGER NOT NULL,
                    tags TEXT,
                    PRIMARY KEY (owner_uuid, slot)
                )
                """;
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException error) {
            throw new IllegalStateException("Failed to ensure stash schema", error);
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private String serializeTags(Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        return tags.entrySet().stream()
                .map(entry -> escape(entry.getKey()) + "=" + escape(entry.getValue()))
                .collect(Collectors.joining(";"));
    }

    private Map<String, String> deserializeTags(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        Map<String, String> tags = new HashMap<>();
        StringBuilder current = new StringBuilder();
        boolean escaping = false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (escaping) {
                current.append(character);
                escaping = false;
                continue;
            }
            if (character == '\\') {
                escaping = true;
                continue;
            }
            if (character == ';') {
                addTagPair(tags, current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        if (escaping) {
            current.append('\\');
        }
        addTagPair(tags, current.toString());
        return tags;
    }

    private void addTagPair(Map<String, String> tags, String rawPair) {
        if (rawPair == null || rawPair.isEmpty()) {
            return;
        }
        int index = rawPair.indexOf('=');
        if (index < 0) {
            tags.put(unescape(rawPair), "");
            return;
        }
        String key = unescape(rawPair.substring(0, index));
        String val = unescape(rawPair.substring(index + 1));
        tags.put(key, val);
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace("=", "\\=");
    }

    private String unescape(String value) {
        StringBuilder builder = new StringBuilder();
        boolean escaping = false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (escaping) {
                builder.append(character);
                escaping = false;
            } else if (character == '\\') {
                escaping = true;
            } else {
                builder.append(character);
            }
        }
        if (escaping) {
            builder.append('\\');
        }
        return builder.toString();
    }
}
