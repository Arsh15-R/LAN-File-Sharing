package com.fileshare.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility for hashing and verifying passwords using SHA-256.
 */
public final class PasswordHasher {

    private PasswordHasher() {
        // Prevent instantiation
    }

    /**
     * Hashes a plaintext password using SHA-256.
     *
     * @param plainPassword plaintext string
     * @return hex-encoded SHA-256 hash
     */
    public static String hash(String plainPassword) {
        if (plainPassword == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(plainPassword.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Verifies that a plaintext password matches a stored hash.
     *
     * @param plainPassword input plaintext password
     * @param storedHash    expected hash
     * @return true if matches, false otherwise
     */
    public static boolean verify(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null) {
            return false;
        }
        String calculated = hash(plainPassword);
        return calculated.equalsIgnoreCase(storedHash);
    }
}
