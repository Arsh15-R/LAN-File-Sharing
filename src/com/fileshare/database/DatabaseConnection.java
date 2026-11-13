package com.fileshare.database;

import com.fileshare.utils.Constants;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;

/**
 * Utility class to manage database connections via JDBC.
 * Reads configuration from db.properties and supplies connections.
 */
public final class DatabaseConnection {

    private static String dbDriver = "com.mysql.cj.jdbc.Driver";
    private static String dbUrl = "jdbc:mysql://localhost:3306/fileshare_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&createDatabaseIfNotExist=true";
    private static String dbUser = "root";
    private static String dbPassword = "";
    private static boolean driverLoaded = false;
    private static final Object LOCK = new Object();

    static {
        loadConfiguration();
        initializeDriver();
    }

    private DatabaseConnection() {
        // Prevent instantiation
    }

    /**
     * Loads database credentials from configuration files or environment variables.
     * Ensures passwords are not hardcoded in source code.
     */
    private static void loadConfiguration() {
        Properties props = new Properties();
        boolean loaded = false;

        // 1. Try loading from resources/db.properties
        File file = new File(Constants.DB_CONFIG_FILE);
        if (!file.exists()) {
            file = new File(Constants.DB_CONFIG_FILE_ALT);
        }

        if (file.exists()) {
            try (InputStream in = new FileInputStream(file)) {
                props.load(in);
                loaded = true;
            } catch (Exception e) {
                System.err.println("[DatabaseConnection] Warning: Could not read config file: " + e.getMessage());
            }
        }

        if (!loaded) {
            // Try loading from classpath
            try (InputStream in = DatabaseConnection.class.getClassLoader().getResourceAsStream("db.properties")) {
                if (in != null) {
                    props.load(in);
                    loaded = true;
                }
            } catch (Exception ignored) {
            }
        }

        // Apply configuration values or environment variable overrides
        if (loaded) {
            dbDriver = props.getProperty("db.driver", dbDriver);
            dbUrl = props.getProperty("db.url", dbUrl);
            dbUser = props.getProperty("db.user", dbUser);
            dbPassword = props.getProperty("db.password", dbPassword);
        }

        // Environment variables override properties (useful for deployment)
        String envUrl = System.getenv("DB_URL");
        if (envUrl != null && !envUrl.trim().isEmpty()) {
            dbUrl = envUrl;
        }
        String envUser = System.getenv("DB_USER");
        if (envUser != null && !envUser.trim().isEmpty()) {
            dbUser = envUser;
        }
        String envPass = System.getenv("DB_PASSWORD");
        if (envPass != null && !envPass.trim().isEmpty()) {
            dbPassword = envPass;
        } else if ((dbPassword == null || dbPassword.trim().isEmpty()) && System.getProperty("os.name", "").toLowerCase().contains("win")) {
            try {
                ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-Command", "[Environment]::GetEnvironmentVariable('DB_PASSWORD', 'User')");
                Process p = pb.start();
                try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                    String line = r.readLine();
                    if (line != null && !line.trim().isEmpty()) {
                        dbPassword = line.trim();
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void initializeDriver() {
        try {
            Class.forName(dbDriver);
            driverLoaded = true;
        } catch (ClassNotFoundException e) {
            System.err.println("[DatabaseConnection] ERROR: MySQL JDBC Driver not found on classpath!");
            System.err.println("Please make sure mysql-connector-j-*.jar is included in the classpath.");
            driverLoaded = false;
        }
    }

    /**
     * Establishes and returns a new database connection.
     *
     * @return active java.sql.Connection
     * @throws SQLException if a database access error occurs
     */
    public static Connection getConnection() throws SQLException {
        if (!driverLoaded) {
            initializeDriver();
            if (!driverLoaded) {
                throw new SQLException("MySQL JDBC Driver (" + dbDriver + ") is missing from classpath.");
            }
        }
        try {
            return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
        } catch (SQLException e) {
            if (e.getErrorCode() == 1045) {
                synchronized (LOCK) {
                    try {
                        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
                    } catch (SQLException ignored) {
                    }

                    if (!GraphicsEnvironment.isHeadless()) {
                        JPasswordField pwdField = new JPasswordField(20);
                        int action = JOptionPane.showConfirmDialog(
                                null,
                                new Object[]{"MySQL authentication required for user '" + dbUser + "':", pwdField},
                                "MySQL Password Required",
                                JOptionPane.OK_CANCEL_OPTION,
                                JOptionPane.QUESTION_MESSAGE
                        );
                        if (action == JOptionPane.OK_OPTION) {
                            dbPassword = new String(pwdField.getPassword());
                            return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
                        }
                    }
                }
            }
            throw new SQLException(
                    "Failed to connect to MySQL database at '" + dbUrl + "' as user '" + dbUser + "'. " +
                    "Reason: " + e.getMessage() + ". " +
                    "Ensure MySQL server is running and credentials in resources/db.properties are correct.", e);
        }
    }

    private static volatile Boolean databaseAvailable = null;

    /**
     * Checks if MySQL database is reachable.
     */
    public static boolean isDatabaseAvailable() {
        if (databaseAvailable == null) {
            testConnection();
        }
        return Boolean.TRUE.equals(databaseAvailable);
    }

    /**
     * Tests whether the database server is reachable and credentials are valid.
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            boolean ok = conn != null && !conn.isClosed();
            databaseAvailable = ok;
            return ok;
        } catch (SQLException e) {
            System.err.println("[DatabaseConnection] MySQL connection notice: " + e.getMessage());
            databaseAvailable = false;
            return false;
        }
    }

    /**
     * Initializes the database schema automatically if tables do not exist.
     */
    public static void initializeDatabase() {
        String createUsersTable = "CREATE TABLE IF NOT EXISTS users (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "username VARCHAR(50) NOT NULL UNIQUE, " +
                "password_hash VARCHAR(255) NOT NULL, " +
                "full_name VARCHAR(100) NOT NULL, " +
                "email VARCHAR(100) NOT NULL, " +
                "status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE', " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        String createTransfersTable = "CREATE TABLE IF NOT EXISTS file_transfers (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "transfer_uuid VARCHAR(64) NOT NULL UNIQUE, " +
                "sender_username VARCHAR(50) NOT NULL, " +
                "receiver_username VARCHAR(50) NOT NULL, " +
                "file_name VARCHAR(255) NOT NULL, " +
                "file_size BIGINT NOT NULL, " +
                "status VARCHAR(30) NOT NULL DEFAULT 'Pending', " +
                "transferred_bytes BIGINT NOT NULL DEFAULT 0, " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "completed_at TIMESTAMP NULL DEFAULT NULL, " +
                "CONSTRAINT fk_sender_user FOREIGN KEY (sender_username) REFERENCES users(username) ON UPDATE CASCADE ON DELETE CASCADE, " +
                "CONSTRAINT fk_receiver_user FOREIGN KEY (receiver_username) REFERENCES users(username) ON UPDATE CASCADE ON DELETE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createUsersTable);
            stmt.execute(createTransfersTable);
            System.out.println("[DatabaseConnection] Database schema verified successfully.");
        } catch (SQLException e) {
            System.err.println("[DatabaseConnection] Note: Schema verification encountered: " + e.getMessage());
        }
    }

    public static String getDbUrl() {
        return dbUrl;
    }

    public static String getDbUser() {
        return dbUser;
    }

    public static void main(String[] args) {
        System.out.println("Testing MySQL connection to: " + dbUrl);
        if (testConnection()) {
            System.out.println("SUCCESS: Connected to MySQL database successfully as user '" + dbUser + "'!");
        } else {
            System.err.println("FAILED: Could not connect to MySQL database.");
        }
    }
}
