-- ============================================================================
-- LAN File Sharing Application - Useful SQL Queries & Sample Operations
-- Database: fileshare_db
-- ============================================================================

USE fileshare_db;

-- ============================================================================
-- 1. SEED / RESET SAMPLE USERS
-- Accounts: 'Arsh' & 'Daksh' (Password: '123456789')
-- SHA-256 hash for '123456789': 15e2b0d3c33891ebb0f1ef609ec419420c20e320ce94c65fbc8c3312448eb225
-- ============================================================================
INSERT INTO users (username, password_hash, full_name, email, status)
VALUES 
('Arsh', '15e2b0d3c33891ebb0f1ef609ec419420c20e320ce94c65fbc8c3312448eb225', 'Arsh Rana', 'arsh@lanfileshare.com', 'OFFLINE'),
('Daksh', '15e2b0d3c33891ebb0f1ef609ec419420c20e320ce94c65fbc8c3312448eb225', 'Daksh Walia', 'daksh@lanfileshare.com', 'OFFLINE')
ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), full_name = VALUES(full_name);

-- ============================================================================
-- 2. ADD SAMPLE FILE TRANSFER HISTORY
-- ============================================================================
INSERT INTO file_transfers (transfer_uuid, sender_username, receiver_username, file_name, file_size, status, transferred_bytes, created_at, completed_at)
VALUES
('uuid-tx-001', 'Arsh', 'Daksh', 'Project_Specification.pdf', 2450000, 'Completed', 2450000, NOW() - INTERVAL 120 MINUTE, NOW() - INTERVAL 118 MINUTE),
('uuid-tx-002', 'Daksh', 'Arsh', 'Architecture_Diagram.png', 1120000, 'Completed', 1120000, NOW() - INTERVAL 60 MINUTE, NOW() - INTERVAL 59 MINUTE)
ON DUPLICATE KEY UPDATE status = VALUES(status);

-- ============================================================================
-- 3. SELECT QUERIES - USER MANAGEMENT
-- ============================================================================

-- Query 3.1: List all registered users
SELECT id, username, full_name, email, status, created_at 
FROM users 
ORDER BY id ASC;

-- Query 3.2: View online users only
SELECT id, username, full_name, email 
FROM users 
WHERE status = 'ONLINE';

-- Query 3.3: Total registered users count
SELECT COUNT(*) AS total_users 
FROM users;

-- ============================================================================
-- 4. SELECT QUERIES - FILE TRANSFERS & AUDIT LOGS
-- ============================================================================

-- Query 4.1: View all file transfer history (most recent first)
SELECT 
    id,
    transfer_uuid,
    sender_username AS sender,
    receiver_username AS receiver,
    file_name,
    ROUND(file_size / (1024 * 1024), 2) AS file_size_mb,
    status,
    ROUND(transferred_bytes / (1024 * 1024), 2) AS transferred_mb,
    created_at,
    completed_at
FROM file_transfers
ORDER BY created_at DESC;

-- Query 4.2: Transfers involving a specific user (e.g., 'Arsh') as sender or receiver
SELECT 
    transfer_uuid,
    sender_username,
    receiver_username,
    file_name,
    status,
    created_at
FROM file_transfers
WHERE sender_username = 'Arsh' OR receiver_username = 'Arsh'
ORDER BY created_at DESC;

-- Query 4.3: Summary count by transfer status (Completed, Failed, Pending)
SELECT 
    status, 
    COUNT(*) AS total_transfers,
    ROUND(SUM(file_size) / (1024 * 1024), 2) AS total_volume_mb
FROM file_transfers
GROUP BY status;

-- Query 4.4: Top senders by volume transferred
SELECT 
    sender_username AS user,
    COUNT(*) AS files_sent,
    ROUND(SUM(transferred_bytes) / (1024 * 1024), 2) AS total_sent_mb
FROM file_transfers
WHERE status = 'Completed'
GROUP BY sender_username
ORDER BY total_sent_mb DESC;

-- Query 4.5: Top receivers by volume received
SELECT 
    receiver_username AS user,
    COUNT(*) AS files_received,
    ROUND(SUM(transferred_bytes) / (1024 * 1024), 2) AS total_received_mb
FROM file_transfers
WHERE status = 'Completed'
GROUP BY receiver_username
ORDER BY total_received_mb DESC;

