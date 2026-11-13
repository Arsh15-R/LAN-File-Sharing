package com.fileshare.utils;

import java.io.File;

/**
 * Application-wide constants.
 */
public final class Constants {

    private Constants() {
        // Prevent instantiation
    }

    public static final String APP_NAME = "LAN File Sharing System";
    public static final int DEFAULT_PORT = 8888;
    public static final String DEFAULT_HOST = "localhost";

    // Transfer settings: 256 KB chunks optimized for high-speed Wi-Fi & large files
    public static final int BUFFER_SIZE = 256 * 1024;
    public static final int SOCKET_BUFFER_SIZE = 512 * 1024; // 512 KB socket buffer
    public static final String DEFAULT_DOWNLOADS_DIR = "downloads" + File.separator;

    // Database configuration
    public static final String DB_CONFIG_FILE = "resources/db.properties";
    public static final String DB_CONFIG_FILE_ALT = "db.properties";

    // User status
    public static final String STATUS_ONLINE = "ONLINE";
    public static final String STATUS_OFFLINE = "OFFLINE";
}
