package com.fileshare.dao;

import com.fileshare.beans.FileTransferBean;
import com.fileshare.beans.TransferHistoryBean;
import com.fileshare.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object (DAO) for File Transfer history and state persistence.
 */
public class FileTransferDAO {

    // In-memory fallback history store when MySQL is offline
    private static final List<TransferHistoryBean> inMemoryHistory = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final java.util.concurrent.atomic.AtomicInteger transferIdSeq = new java.util.concurrent.atomic.AtomicInteger(1);
    private static final java.io.File DATA_DIR = new java.io.File("data");
    private static final java.io.File TRANSFERS_FILE = new java.io.File(DATA_DIR, "transfers_store.ser");

    static {
        loadFallbackFromDisk();
    }

    private static synchronized void loadFallbackFromDisk() {
        if (!DATA_DIR.exists()) DATA_DIR.mkdirs();
        if (TRANSFERS_FILE.exists()) {
            try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(new java.io.FileInputStream(TRANSFERS_FILE))) {
                @SuppressWarnings("unchecked")
                List<TransferHistoryBean> loaded = (List<TransferHistoryBean>) ois.readObject();
                inMemoryHistory.addAll(loaded);
            } catch (Exception ignored) {}
        }
    }

    private static synchronized void saveFallbackToDisk() {
        try {
            if (!DATA_DIR.exists()) DATA_DIR.mkdirs();
            try (java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(new java.io.FileOutputStream(TRANSFERS_FILE))) {
                oos.writeObject(new ArrayList<>(inMemoryHistory));
            }
        } catch (Exception ignored) {}
    }

    /**
     * Inserts a new file transfer record into the database.
     *
     * @param transfer FileTransferBean object
     * @return generated primary key ID, or -1 on failure
     */
    public int saveTransfer(FileTransferBean transfer) {
        if (transfer == null) return -1;

        if (!DatabaseConnection.isDatabaseAvailable()) {
            int id = transferIdSeq.incrementAndGet();
            transfer.setId(id);
            TransferHistoryBean th = new TransferHistoryBean(
                    id, transfer.getTransferUuid(), transfer.getSenderUsername(),
                    transfer.getReceiverUsername(), transfer.getFileName(),
                    transfer.getFileSize(), transfer.getStatus(),
                    transfer.getCreatedAt() != null ? transfer.getCreatedAt() : new Timestamp(System.currentTimeMillis())
            );
            inMemoryHistory.add(0, th);
            saveFallbackToDisk();
            return id;
        }

        String sql = "INSERT INTO file_transfers " +
                "(transfer_uuid, sender_username, receiver_username, file_name, file_size, status, transferred_bytes, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, transfer.getTransferUuid());
            ps.setString(2, transfer.getSenderUsername());
            ps.setString(3, transfer.getReceiverUsername());
            ps.setString(4, transfer.getFileName());
            ps.setLong(5, transfer.getFileSize());
            ps.setString(6, transfer.getStatus());
            ps.setLong(7, transfer.getTransferredBytes());
            ps.setTimestamp(8, transfer.getCreatedAt() != null ? transfer.getCreatedAt() : new Timestamp(System.currentTimeMillis()));

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        int genId = rs.getInt(1);
                        transfer.setId(genId);
                        return genId;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[FileTransferDAO] Failed to record transfer: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Updates transfer status and bytes transferred by transfer UUID.
     *
     * @param transferUuid unique UUID of the transfer
     * @param status new status ('Pending', 'In Progress', 'Completed', 'Failed', 'Cancelled')
     * @param transferredBytes total bytes transmitted so far
     * @return true if updated, false otherwise
     */
    public boolean updateTransferStatus(String transferUuid, String status, long transferredBytes) {
        if (transferUuid == null || status == null) return false;

        if (!DatabaseConnection.isDatabaseAvailable()) {
            for (TransferHistoryBean th : inMemoryHistory) {
                if (transferUuid.equals(th.getTransferUuid())) {
                    th.setStatus(status);
                    saveFallbackToDisk();
                    return true;
                }
            }
            return false;
        }

        String sql = "UPDATE file_transfers SET status = ?, transferred_bytes = ?, " +
                "completed_at = CASE WHEN ? = 'Completed' THEN CURRENT_TIMESTAMP ELSE completed_at END " +
                "WHERE transfer_uuid = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setLong(2, transferredBytes);
            ps.setString(3, status);
            ps.setString(4, transferUuid);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("[FileTransferDAO] Error updating transfer " + transferUuid + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Retrieves transfer history for a specific user (as either sender or receiver).
     *
     * @param username user whose history to query
     * @return list of TransferHistoryBean records
     */
    public List<TransferHistoryBean> getHistoryForUser(String username) {
        List<TransferHistoryBean> list = new ArrayList<>();
        if (username == null) return list;

        if (!DatabaseConnection.isDatabaseAvailable()) {
            for (TransferHistoryBean th : inMemoryHistory) {
                if (username.equalsIgnoreCase(th.getSenderUsername()) || username.equalsIgnoreCase(th.getReceiverUsername())) {
                    list.add(th);
                }
            }
            return list;
        }

        String sql = "SELECT id, transfer_uuid, sender_username, receiver_username, file_name, file_size, status, created_at " +
                "FROM file_transfers " +
                "WHERE sender_username = ? OR receiver_username = ? " +
                "ORDER BY created_at DESC LIMIT 100";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, username);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TransferHistoryBean bean = new TransferHistoryBean(
                            rs.getInt("id"),
                            rs.getString("transfer_uuid"),
                            rs.getString("sender_username"),
                            rs.getString("receiver_username"),
                            rs.getString("file_name"),
                            rs.getLong("file_size"),
                            rs.getString("status"),
                            rs.getTimestamp("created_at")
                    );
                    list.add(bean);
                }
            }
        } catch (SQLException e) {
            System.err.println("[FileTransferDAO] Failed to fetch history for " + username + ": " + e.getMessage());
        }
        return list;
    }

    /**
     * Marks any unfinished transfers as 'Failed' when a participating client abruptly disconnects.
     */
    public void markIncompleteTransfersFailedForUser(String username) {
        if (username == null) return;

        if (!DatabaseConnection.isDatabaseAvailable()) {
            for (TransferHistoryBean th : inMemoryHistory) {
                if ((username.equalsIgnoreCase(th.getSenderUsername()) || username.equalsIgnoreCase(th.getReceiverUsername()))
                        && ("Pending".equalsIgnoreCase(th.getStatus()) || "In Progress".equalsIgnoreCase(th.getStatus()))) {
                    th.setStatus("Failed");
                }
            }
            return;
        }

        String sql = "UPDATE file_transfers SET status = 'Failed' " +
                "WHERE (sender_username = ? OR receiver_username = ?) AND status IN ('Pending', 'In Progress')";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, username);
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("[FileTransferDAO] Failed to mark incomplete transfers failed: " + e.getMessage());
        }
    }
}
