package com.fileshare.dao;

import com.fileshare.beans.UserBean;
import com.fileshare.database.DatabaseConnection;
import com.fileshare.utils.Constants;
import com.fileshare.utils.PasswordHasher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * Data Access Object (DAO) for User database operations.
 * Implements CRUD and authentication using PreparedStatements and try-with-resources.
 */
public class UserDAO {

    // In-memory fallback repository when MySQL service is offline
    private static final java.util.Map<String, UserBean> inMemoryUsers = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, String> inMemoryPasswords = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicInteger idCounter = new java.util.concurrent.atomic.AtomicInteger(100);

    private static final java.io.File DATA_DIR = new java.io.File("data");
    private static final java.io.File USERS_FILE = new java.io.File(DATA_DIR, "users_store.ser");
    private static final java.io.File PASSWORDS_FILE = new java.io.File(DATA_DIR, "passwords_store.ser");

    static {
        // Primary sample accounts: Arsh and Daksh (password: 123456789)
        String sampleHash = PasswordHasher.hash("123456789");
        UserBean arsh = new UserBean(1, "Arsh", "Arsh Rana", "arsh@lanfileshare.com", "OFFLINE", new Timestamp(System.currentTimeMillis()));
        UserBean daksh = new UserBean(2, "Daksh", "Daksh Walia", "daksh@lanfileshare.com", "OFFLINE", new Timestamp(System.currentTimeMillis()));
        inMemoryUsers.put("arsh", arsh);
        inMemoryPasswords.put("arsh", sampleHash);
        inMemoryUsers.put("daksh", daksh);
        inMemoryPasswords.put("daksh", sampleHash);

        // Load any previously registered users saved to disk
        loadFallbackFromDisk();
    }

    private static synchronized void loadFallbackFromDisk() {
        if (!DATA_DIR.exists()) DATA_DIR.mkdirs();
        if (USERS_FILE.exists()) {
            try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(new java.io.FileInputStream(USERS_FILE))) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, UserBean> loaded = (java.util.Map<String, UserBean>) ois.readObject();
                inMemoryUsers.putAll(loaded);
            } catch (Exception ignored) {}
        }
        if (PASSWORDS_FILE.exists()) {
            try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(new java.io.FileInputStream(PASSWORDS_FILE))) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, String> loaded = (java.util.Map<String, String>) ois.readObject();
                inMemoryPasswords.putAll(loaded);
            } catch (Exception ignored) {}
        }
    }

    private static synchronized void saveFallbackToDisk() {
        try {
            if (!DATA_DIR.exists()) DATA_DIR.mkdirs();
            try (java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(new java.io.FileOutputStream(USERS_FILE))) {
                oos.writeObject(new java.util.HashMap<>(inMemoryUsers));
            }
            try (java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(new java.io.FileOutputStream(PASSWORDS_FILE))) {
                oos.writeObject(new java.util.HashMap<>(inMemoryPasswords));
            }
        } catch (Exception ignored) {}
    }

    /**
     * Checks if a username is already registered in the system.
     *
     * @param username username to inspect
     * @return true if taken, false otherwise
     */
    public boolean isUsernameTaken(String username) {
        if (!DatabaseConnection.isDatabaseAvailable()) {
            return inMemoryUsers.containsKey(username.trim().toLowerCase());
        }
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] Error checking username existence: " + e.getMessage());
        }
        return false;
    }

    /**
     * Registers a new user with hashed password storage.
     *
     * @param user UserBean containing registration details
     * @return true if registration succeeded, false otherwise
     */
    public boolean registerUser(UserBean user) {
        if (user == null || user.getUsername() == null || user.getPassword() == null) {
            return false;
        }

        if (!DatabaseConnection.isDatabaseAvailable()) {
            String uName = user.getUsername().trim().toLowerCase();
            if (inMemoryUsers.containsKey(uName)) return false;
            String hashedPassword = PasswordHasher.hash(user.getPassword());
            int newId = idCounter.incrementAndGet();
            UserBean stored = new UserBean(newId, user.getUsername().trim(), user.getFullName(), user.getEmail(), Constants.STATUS_OFFLINE, new Timestamp(System.currentTimeMillis()));
            inMemoryUsers.put(uName, stored);
            inMemoryPasswords.put(uName, hashedPassword);
            saveFallbackToDisk();
            return true;
        }

        String sql = "INSERT INTO users (username, password_hash, full_name, email, status) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            String hashedPassword = PasswordHasher.hash(user.getPassword());

            ps.setString(1, user.getUsername().trim());
            ps.setString(2, hashedPassword);
            ps.setString(3, user.getFullName() != null ? user.getFullName().trim() : "");
            ps.setString(4, user.getEmail() != null ? user.getEmail().trim() : "");
            ps.setString(5, Constants.STATUS_OFFLINE);

            int affected = ps.executeUpdate();
            return affected > 0;

        } catch (SQLException e) {
            System.err.println("[UserDAO] Registration failed for " + user.getUsername() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Authenticates a user with username and password.
     * Updates status to ONLINE upon successful verification.
     *
     * @param username user identifier
     * @param plainPassword plain password input
     * @return populated UserBean if valid, null if credentials fail
     */
    public UserBean authenticateUser(String username, String plainPassword) {
        if (username == null || plainPassword == null) {
            return null;
        }

        if (!DatabaseConnection.isDatabaseAvailable()) {
            String uName = username.trim().toLowerCase();
            UserBean user = inMemoryUsers.get(uName);
            if (user != null) {
                String storedHash = inMemoryPasswords.get(uName);
                if (PasswordHasher.verify(plainPassword, storedHash)) {
                    user.setStatus(Constants.STATUS_ONLINE);
                    return user;
                }
            }
            return null;
        }

        String sql = "SELECT id, username, password_hash, full_name, email, status, created_at FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    if (PasswordHasher.verify(plainPassword, storedHash)) {
                        int id = rs.getInt("id");
                        String uName = rs.getString("username");
                        String fName = rs.getString("full_name");
                        String email = rs.getString("email");
                        Timestamp createdAt = rs.getTimestamp("created_at");

                        // Update status to ONLINE
                        updateUserStatus(uName, Constants.STATUS_ONLINE);

                        UserBean user = new UserBean(id, uName, fName, email, Constants.STATUS_ONLINE, createdAt);
                        return user;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] Authentication error for " + username + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Updates the online/offline status of a user.
     *
     * @param username username to update
     * @param status status string ("ONLINE" or "OFFLINE")
     * @return true if updated, false otherwise
     */
    public boolean updateUserStatus(String username, String status) {
        if (username == null) return false;

        if (!DatabaseConnection.isDatabaseAvailable()) {
            UserBean user = inMemoryUsers.get(username.trim().toLowerCase());
            if (user != null) {
                user.setStatus(status);
                return true;
            }
            return false;
        }

        String sql = "UPDATE users SET status = ? WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setString(2, username);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("[UserDAO] Failed to update status for " + username + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Retrieves user details by username.
     */
    public UserBean getUserByUsername(String username) {
        if (username == null) return null;

        if (!DatabaseConnection.isDatabaseAvailable()) {
            return inMemoryUsers.get(username.trim().toLowerCase());
        }

        String sql = "SELECT id, username, full_name, email, status, created_at FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new UserBean(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("full_name"),
                            rs.getString("email"),
                            rs.getString("status"),
                            rs.getTimestamp("created_at")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] Error querying user " + username + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Resets all users to OFFLINE on server startup.
     */
    public void resetAllUserStatuses() {
        if (!DatabaseConnection.isDatabaseAvailable()) {
            for (UserBean u : inMemoryUsers.values()) {
                u.setStatus("OFFLINE");
            }
            return;
        }

        String sql = "UPDATE users SET status = 'OFFLINE'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[UserDAO] Note: Could not reset user statuses: " + e.getMessage());
        }
    }
}
