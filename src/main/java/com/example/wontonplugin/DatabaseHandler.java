package com.example.wontonplugin;

import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

public class DatabaseHandler {

    private Connection connection;
    private final String dbPath;
    private final Logger logger;

    public DatabaseHandler(String dbPath, Logger logger) {
        this.dbPath = dbPath;
        this.logger = logger;
    }

    public void connect() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return;
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        connection.createStatement().execute("PRAGMA journal_mode=WAL");
        createTables();
    }

    public void disconnect() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                logger.warning("Could not close database connection: " + e.getMessage());
            }
        }
    }

    private void createTables() throws SQLException {
        String friendsTable = "CREATE TABLE IF NOT EXISTS friends (" +
                "player_uuid TEXT NOT NULL, " +
                "friend_name TEXT NOT NULL, " +
                "PRIMARY KEY (player_uuid, friend_name))";

        String pendingTable = "CREATE TABLE IF NOT EXISTS pending_requests (" +
                "target_uuid TEXT NOT NULL, " +
                "requester_uuid TEXT NOT NULL, " +
                "PRIMARY KEY (target_uuid, requester_uuid))";

        String settingsTable = "CREATE TABLE IF NOT EXISTS player_settings (" +
                "player_uuid TEXT PRIMARY KEY, " +
                "receive_requests INTEGER NOT NULL DEFAULT 1)";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(friendsTable);
            stmt.execute(pendingTable);
            stmt.execute(settingsTable);
        }
    }

    public Map<UUID, List<String>> loadAllFriends() {
        Map<UUID, List<String>> data = new HashMap<>();
        String sql = "SELECT player_uuid, friend_name FROM friends ORDER BY player_uuid";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                String friendName = rs.getString("friend_name");
                data.computeIfAbsent(uuid, k -> new ArrayList<>()).add(friendName);
            }
        } catch (SQLException e) {
            logger.warning("Could not load friends: " + e.getMessage());
        }
        return data;
    }

    public Map<UUID, Set<UUID>> loadAllPendingRequests() {
        Map<UUID, Set<UUID>> data = new HashMap<>();
        String sql = "SELECT target_uuid, requester_uuid FROM pending_requests ORDER BY target_uuid";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                UUID target = UUID.fromString(rs.getString("target_uuid"));
                UUID requester = UUID.fromString(rs.getString("requester_uuid"));
                data.computeIfAbsent(target, k -> new HashSet<>()).add(requester);
            }
        } catch (SQLException e) {
            logger.warning("Could not load pending requests: " + e.getMessage());
        }
        return data;
    }

    public void addFriend(UUID playerUuid, String friendName) {
        String sql = "INSERT OR IGNORE INTO friends (player_uuid, friend_name) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, friendName);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Could not add friend: " + e.getMessage());
        }
    }

    public void removeFriend(UUID playerUuid, String friendName) {
        String sql = "DELETE FROM friends WHERE player_uuid = ? AND LOWER(friend_name) = LOWER(?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, friendName);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Could not remove friend: " + e.getMessage());
        }
    }

    public void removeFriendFromTarget(UUID targetUuid, String playerName) {
        String sql = "DELETE FROM friends WHERE player_uuid = ? AND LOWER(friend_name) = LOWER(?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, targetUuid.toString());
            stmt.setString(2, playerName);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Could not remove friend from target: " + e.getMessage());
        }
    }

    public void addPendingRequest(UUID targetUuid, UUID requesterUuid) {
        String sql = "INSERT OR IGNORE INTO pending_requests (target_uuid, requester_uuid) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, targetUuid.toString());
            stmt.setString(2, requesterUuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Could not add pending request: " + e.getMessage());
        }
    }

    public void removePendingRequest(UUID targetUuid, UUID requesterUuid) {
        String sql = "DELETE FROM pending_requests WHERE target_uuid = ? AND requester_uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, targetUuid.toString());
            stmt.setString(2, requesterUuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Could not remove pending request: " + e.getMessage());
        }
    }

    public void removePendingRequestsForPlayer(UUID playerUuid) {
        String sql = "DELETE FROM pending_requests WHERE target_uuid = ? OR requester_uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, playerUuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Could not clear pending requests: " + e.getMessage());
        }
    }

    public void clearAllPendingRequests() {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DELETE FROM pending_requests");
        } catch (SQLException e) {
            logger.warning("Could not clear all pending requests: " + e.getMessage());
        }
    }

    public boolean getReceiveRequests(UUID playerUuid) {
        String sql = "SELECT receive_requests FROM player_settings WHERE player_uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("receive_requests") == 1;
            }
        } catch (SQLException e) {
            logger.warning("Could not get player settings: " + e.getMessage());
        }
        return true;
    }

    public void setReceiveRequests(UUID playerUuid, boolean enabled) {
        String sql = "INSERT OR REPLACE INTO player_settings (player_uuid, receive_requests) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setInt(2, enabled ? 1 : 0);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Could not set player settings: " + e.getMessage());
        }
    }
}
