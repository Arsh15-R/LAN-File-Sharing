package com.fileshare.utils;

import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;

/**
 * Utility for formatting file sizes and timestamps for UI display.
 */
public final class FormatUtils {

    private static final String[] UNITS = new String[]{"B", "KB", "MB", "GB", "TB"};
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private FormatUtils() {
        // Prevent instantiation
    }

    /**
     * Formats raw byte size into human-readable string (e.g., "14.25 MB").
     */
    public static String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        if (digitGroups >= UNITS.length) digitGroups = UNITS.length - 1;
        return new DecimalFormat("#,##0.##").format(bytes / Math.pow(1024, digitGroups)) + " " + UNITS[digitGroups];
    }

    /**
     * Formats a SQL Timestamp into a readable string.
     */
    public static synchronized String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) return "N/A";
        return DATE_FORMAT.format(timestamp);
    }
}
