package com.raidextraction.quest;

import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * SQLite implementation of QuestRepository.
 */
public class SQLiteQuestRepository implements QuestRepository {
    private final String jdbcUrl;
    private final Logger logger;

    public SQLiteQuestRepository(Path databasePath, Logger logger) {
        this.jdbcUrl = "jdbc:sqlite:" + databasePath.toAbsolutePath();
        this.logger = Objects.requireNonNull(logger, "logger");
        ensureSchema();
    }

    @Override
    public List<PlayerQuest> loadPlayerQuests(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        List<PlayerQuest> quests = new ArrayList<>();
        
        String sql = """
            SELECT quest_id, completed, claimed, created_at_ms, updated_at_ms 
            FROM player_quests 
            WHERE player_uuid = ?
            """;
            
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, playerId.toString());
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    PlayerQuest quest = new PlayerQuest(
                            playerId,
                            rs.getString("quest_id"),
                            rs.getInt("completed") == 1,
                            rs.getInt("claimed") == 1,
                            rs.getLong("created_at_ms"),
                            rs.getLong("updated_at_ms")
                    );
                    quests.add(quest);
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to load player quests for " + playerId + ": " + e.getMessage());
        }
        
        return quests;
    }

    @Override
    public List<PlayerQuestTask> loadPlayerQuestTasks(UUID playerId, String questId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(questId, "questId");
        List<PlayerQuestTask> tasks = new ArrayList<>();
        
        String sql = """
            SELECT task_id, progress, completed, created_at_ms, updated_at_ms 
            FROM player_quest_tasks 
            WHERE player_uuid = ? AND quest_id = ?
            """;
            
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, playerId.toString());
            stmt.setString(2, questId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    PlayerQuestTask task = new PlayerQuestTask(
                            playerId,
                            questId,
                            rs.getString("task_id"),
                            rs.getInt("progress"),
                            rs.getInt("completed") == 1,
                            rs.getLong("created_at_ms"),
                            rs.getLong("updated_at_ms")
                    );
                    tasks.add(task);
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to load player quest tasks for " + playerId + ", " + questId + ": " + e.getMessage());
        }
        
        return tasks;
    }

    @Override
    public void savePlayerQuest(PlayerQuest playerQuest) {
        Objects.requireNonNull(playerQuest, "playerQuest");
        
        String sql = """
            INSERT OR REPLACE INTO player_quests 
            (player_uuid, quest_id, completed, claimed, created_at_ms, updated_at_ms) 
            VALUES (?, ?, ?, ?, ?, ?)
            """;
            
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, playerQuest.playerId().toString());
            stmt.setString(2, playerQuest.questId());
            stmt.setInt(3, playerQuest.completed() ? 1 : 0);
            stmt.setInt(4, playerQuest.claimed() ? 1 : 0);
            stmt.setLong(5, playerQuest.createdAtMs());
            stmt.setLong(6, playerQuest.updatedAtMs());
            
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.severe("Failed to save player quest " + playerQuest.questId() + " for " + playerQuest.playerId() + ": " + e.getMessage());
        }
    }

    @Override
    public void savePlayerQuestTask(PlayerQuestTask playerQuestTask) {
        Objects.requireNonNull(playerQuestTask, "playerQuestTask");
        
        String sql = """
            INSERT OR REPLACE INTO player_quest_tasks 
            (player_uuid, quest_id, task_id, progress, completed, created_at_ms, updated_at_ms) 
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
            
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, playerQuestTask.playerId().toString());
            stmt.setString(2, playerQuestTask.questId());
            stmt.setString(3, playerQuestTask.taskId());
            stmt.setInt(4, playerQuestTask.progress());
            stmt.setInt(5, playerQuestTask.completed() ? 1 : 0);
            stmt.setLong(6, playerQuestTask.createdAtMs());
            stmt.setLong(7, playerQuestTask.updatedAtMs());
            
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.severe("Failed to save player quest task " + playerQuestTask.taskId() + " for " + playerQuestTask.playerId() + ": " + e.getMessage());
        }
    }

    @Override
    public void markQuestClaimed(UUID playerId, String questId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(questId, "questId");
        
        String sql = "UPDATE player_quests SET claimed = 1, updated_at_ms = ? WHERE player_uuid = ? AND quest_id = ?";
        
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, System.currentTimeMillis());
            stmt.setString(2, playerId.toString());
            stmt.setString(3, questId);
            
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.severe("Failed to mark quest claimed " + questId + " for " + playerId + ": " + e.getMessage());
        }
    }

    @Override
    public void resetPlayerQuest(UUID playerId, String questId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(questId, "questId");
        
        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            conn.setAutoCommit(false);
            
            // Delete quest tasks
            try (PreparedStatement deleteTasks = conn.prepareStatement(
                    "DELETE FROM player_quest_tasks WHERE player_uuid = ? AND quest_id = ?")) {
                deleteTasks.setString(1, playerId.toString());
                deleteTasks.setString(2, questId);
                deleteTasks.executeUpdate();
            }
            
            // Reset quest status
            try (PreparedStatement resetQuest = conn.prepareStatement(
                    "UPDATE player_quests SET completed = 0, claimed = 0, updated_at_ms = ? WHERE player_uuid = ? AND quest_id = ?")) {
                resetQuest.setLong(1, System.currentTimeMillis());
                resetQuest.setString(2, playerId.toString());
                resetQuest.setString(3, questId);
                resetQuest.executeUpdate();
            }
            
            conn.commit();
        } catch (SQLException e) {
            logger.severe("Failed to reset player quest " + questId + " for " + playerId + ": " + e.getMessage());
        }
    }

    private void ensureSchema() {
        String questsTable = """
            CREATE TABLE IF NOT EXISTS player_quests (
                player_uuid TEXT NOT NULL,
                quest_id TEXT NOT NULL,
                completed INTEGER NOT NULL DEFAULT 0,
                claimed INTEGER NOT NULL DEFAULT 0,
                created_at_ms INTEGER NOT NULL,
                updated_at_ms INTEGER NOT NULL,
                PRIMARY KEY (player_uuid, quest_id)
            )
            """;
            
        String tasksTable = """
            CREATE TABLE IF NOT EXISTS player_quest_tasks (
                player_uuid TEXT NOT NULL,
                quest_id TEXT NOT NULL,
                task_id TEXT NOT NULL,
                progress INTEGER NOT NULL DEFAULT 0,
                completed INTEGER NOT NULL DEFAULT 0,
                created_at_ms INTEGER NOT NULL,
                updated_at_ms INTEGER NOT NULL,
                PRIMARY KEY (player_uuid, quest_id, task_id)
            )
            """;
            
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement()) {
            
            stmt.execute(questsTable);
            stmt.execute(tasksTable);
            logger.info("Quest database schema ensured");
        } catch (SQLException e) {
            logger.severe("Failed to ensure quest database schema: " + e.getMessage());
        }
    }
}