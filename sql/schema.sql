-- ============================================================================
-- LAN File Sharing Application Database Schema
-- Database: fileshare_db
-- ============================================================================

-- 1. Create and switch to the database
CREATE DATABASE IF NOT EXISTS fileshare_db;
USE fileshare_db;

-- 2. Create users table
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_users_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Create file transfers table
CREATE TABLE IF NOT EXISTS file_transfers (
    id INT AUTO_INCREMENT PRIMARY KEY,
    transfer_uuid VARCHAR(64) NOT NULL UNIQUE,
    sender_username VARCHAR(50) NOT NULL,
    receiver_username VARCHAR(50) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'Pending',
    transferred_bytes BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL DEFAULT NULL,
    INDEX idx_transfers_status (status),
    INDEX idx_transfers_sender (sender_username),
    INDEX idx_transfers_receiver (receiver_username),
    CONSTRAINT fk_sender_user FOREIGN KEY (sender_username) REFERENCES users(username) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_receiver_user FOREIGN KEY (receiver_username) REFERENCES users(username) ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. Add initial sample users
-- Accounts: 'Arsh' & 'Daksh' (Password: '123456789')
-- SHA-256 for '123456789': 15e2b0d3c33891ebb0f1ef609ec419420c20e320ce94c65fbc8c3312448eb225
INSERT INTO users (username, password_hash, full_name, email, status)
VALUES 
('Arsh', '15e2b0d3c33891ebb0f1ef609ec419420c20e320ce94c65fbc8c3312448eb225', 'Arsh Rana', 'arsh@lanfileshare.com', 'OFFLINE'),
('Daksh', '15e2b0d3c33891ebb0f1ef609ec419420c20e320ce94c65fbc8c3312448eb225', 'Daksh Walia', 'daksh@lanfileshare.com', 'OFFLINE')
ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), full_name = VALUES(full_name);
