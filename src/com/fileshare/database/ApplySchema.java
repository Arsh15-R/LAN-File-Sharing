package com.fileshare.database;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.Scanner;
import java.util.regex.Matcher;

/**
 * Utility tool to apply MySQL schema and seed data directly from sql/schema.sql.
 * Can be run via CLI or batch script.
 */
public class ApplySchema {

    public static void main(String[] args) {
        System.out.println("========================================================");
        System.out.println("  LAN File Sharing - MySQL Schema Application Tool     ");
        System.out.println("========================================================");

        // 1. Locate schema.sql
        File schemaFile = new File("sql/schema.sql");
        String explicitPassword = null;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("-p") && i + 1 < args.length) {
                explicitPassword = args[i + 1];
                i++;
            } else if (!args[i].startsWith("-")) {
                File custom = new File(args[i]);
                if (custom.exists()) {
                    schemaFile = custom;
                }
            }
        }

        if (!schemaFile.exists()) {
            schemaFile = new File("schema.sql");
        }

        if (!schemaFile.exists()) {
            System.err.println("[ERROR] Schema file not found: " + schemaFile.getAbsolutePath());
            System.exit(1);
        }

        System.out.println("[INFO] Reading schema from: " + schemaFile.getAbsolutePath());

        // 2. Load db.properties
        Properties props = new Properties();
        File propFile = new File("resources/db.properties");
        if (!propFile.exists()) {
            propFile = new File("db.properties");
        }

        String dbUrl = "jdbc:mysql://localhost:3306/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        String dbUser = "root";
        String dbPassword = "root";

        if (propFile.exists()) {
            try (InputStream in = new FileInputStream(propFile)) {
                props.load(in);
                dbUser = props.getProperty("db.user", dbUser);
                dbPassword = props.getProperty("db.password", dbPassword);
                String configuredUrl = props.getProperty("db.url", "");
                if (!configuredUrl.isEmpty()) {
                    if (configuredUrl.contains("/fileshare_db")) {
                        dbUrl = configuredUrl.replace("/fileshare_db", "/");
                    } else {
                        dbUrl = configuredUrl;
                    }
                }
                System.out.println("[INFO] Loaded configuration from: " + propFile.getPath());
            } catch (Exception e) {
                System.err.println("[WARN] Could not read db.properties: " + e.getMessage());
            }
        }

        if (explicitPassword != null) {
            dbPassword = explicitPassword;
        } else if (System.getenv("DB_PASSWORD") != null) {
            dbPassword = System.getenv("DB_PASSWORD");
        }
        if (System.getenv("DB_USER") != null) dbUser = System.getenv("DB_USER");
        if (System.getenv("DB_URL") != null) dbUrl = System.getenv("DB_URL");

        System.out.println("[INFO] Connecting to MySQL URL: " + dbUrl);
        System.out.println("[INFO] Authenticating as user: " + dbUser);

        // 3. Register JDBC Driver
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("[ERROR] MySQL JDBC driver not found on classpath.");
            System.exit(1);
        }

        Connection conn = null;
        try {
            conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
        } catch (SQLException e) {
            if (e.getErrorCode() == 1045 || (e.getMessage() != null && e.getMessage().toLowerCase().contains("access denied"))) {
                System.out.println("\n[AUTH NOTICE] Password '" + dbPassword + "' was rejected for user '" + dbUser + "'.");
                System.out.print("Please enter your MySQL root password: ");
                Scanner scanner = new Scanner(System.in);
                if (scanner.hasNextLine()) {
                    dbPassword = scanner.nextLine().trim();
                    try {
                        conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
                        System.out.println("[SUCCESS] Successfully authenticated with entered password!");
                    } catch (Exception ex) {
                        System.err.println("\n[ERROR] Connection failed: " + ex.getMessage());
                        System.exit(1);
                    }
                } else {
                    System.err.println("[ERROR] No password input provided.");
                    System.exit(1);
                }
            } else {
                System.err.println("\n[ERROR] Could not connect to MySQL server: " + e.getMessage());
                System.exit(1);
            }
        }

        try (Connection activeConn = conn;
             Statement stmt = activeConn.createStatement()) {

            System.out.println("[SUCCESS] Connected to MySQL Server successfully!");

            // Read SQL statements
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(schemaFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("--") || trimmed.startsWith("/*") || trimmed.isEmpty()) {
                        continue;
                    }
                    sb.append(line).append("\n");
                }
            }

            // Split statements by semicolon
            String[] statements = sb.toString().split(";");
            int executedCount = 0;

            for (String sql : statements) {
                String singleStmt = sql.trim();
                if (singleStmt.isEmpty()) continue;

                System.out.println("\n[EXECUTING] " + getFirstLine(singleStmt) + " ...");
                try {
                    stmt.execute(singleStmt);
                    executedCount++;
                    System.out.println("[OK] Executed successfully.");
                } catch (Exception ex) {
                    System.err.println("[WARN] Notice on statement: " + ex.getMessage());
                }
            }

            System.out.println("\n========================================================");
            System.out.println("[SUCCESS] Finished applying schema! (" + executedCount + " statements executed)");

            // Verification
            try (ResultSet rs = stmt.executeQuery("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'fileshare_db'")) {
                System.out.println("\n[VERIFICATION] Tables in 'fileshare_db':");
                while (rs.next()) {
                    System.out.println("  - " + rs.getString(1));
                }
            }

            try (ResultSet rs = stmt.executeQuery("SELECT username, full_name, email, status FROM fileshare_db.users")) {
                System.out.println("\n[VERIFICATION] Seed users in 'users' table:");
                while (rs.next()) {
                    System.out.printf("  - %s (%s, %s) [%s]%n",
                            rs.getString("username"),
                            rs.getString("full_name"),
                            rs.getString("email"),
                            rs.getString("status"));
                }
            }
            System.out.println("========================================================");

        } catch (Exception e) {
            System.err.println("\n[ERROR] Execution error: " + e.getMessage());
            System.exit(1);
        }
    }



    private static String getFirstLine(String text) {
        int idx = text.indexOf('\n');
        if (idx > 0) {
            return text.substring(0, idx).trim();
        }
        return text.trim();
    }
}
