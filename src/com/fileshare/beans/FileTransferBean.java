package com.fileshare.beans;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.UUID;

/**
 * JavaBean representing a File Transfer session.
 * Tracks transfer metadata, parties, size, and real-time status.
 */
public class FileTransferBean implements Serializable {

    private static final long serialVersionUID = 1L;

    // Transfer status constants
    public static final String STATUS_PENDING = "Pending";
    public static final String STATUS_IN_PROGRESS = "In Progress";
    public static final String STATUS_COMPLETED = "Completed";
    public static final String STATUS_FAILED = "Failed";
    public static final String STATUS_CANCELLED = "Cancelled";

    private int id;
    private String transferUuid;
    private String senderUsername;
    private String receiverUsername;
    private String fileName;
    private long fileSize;
    private String status;
    private long transferredBytes;
    private Timestamp createdAt;
    private Timestamp completedAt;

    /**
     * Default no-argument constructor (required for JavaBeans).
     */
    public FileTransferBean() {
        this.transferUuid = UUID.randomUUID().toString();
        this.status = STATUS_PENDING;
        this.transferredBytes = 0;
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }

    /**
     * Parameterized constructor for initiating a new file transfer.
     */
    public FileTransferBean(String senderUsername, String receiverUsername, String fileName, long fileSize) {
        this();
        this.senderUsername = senderUsername;
        this.receiverUsername = receiverUsername;
        this.fileName = fileName;
        this.fileSize = fileSize;
    }

    /**
     * Full parameterized constructor for database retrieval.
     */
    public FileTransferBean(int id, String transferUuid, String senderUsername, String receiverUsername,
                            String fileName, long fileSize, String status, long transferredBytes,
                            Timestamp createdAt, Timestamp completedAt) {
        this.id = id;
        this.transferUuid = transferUuid;
        this.senderUsername = senderUsername;
        this.receiverUsername = receiverUsername;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.status = status;
        this.transferredBytes = transferredBytes;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    // --- Getters and Setters ---

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTransferUuid() {
        return transferUuid;
    }

    public void setTransferUuid(String transferUuid) {
        this.transferUuid = transferUuid;
    }

    public String getSenderUsername() {
        return senderUsername;
    }

    public void setSenderUsername(String senderUsername) {
        this.senderUsername = senderUsername;
    }

    public String getReceiverUsername() {
        return receiverUsername;
    }

    public void setReceiverUsername(String receiverUsername) {
        this.receiverUsername = receiverUsername;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getTransferredBytes() {
        return transferredBytes;
    }

    public void setTransferredBytes(long transferredBytes) {
        this.transferredBytes = transferredBytes;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Timestamp completedAt) {
        this.completedAt = completedAt;
    }

    /**
     * Calculates transfer progress percentage (0 - 100).
     */
    public int getProgressPercentage() {
        if (fileSize <= 0) return 0;
        return (int) Math.min(100, (transferredBytes * 100) / fileSize);
    }

    @Override
    public String toString() {
        return "FileTransferBean{" +
                "uuid='" + transferUuid + '\'' +
                ", sender='" + senderUsername + '\'' +
                ", receiver='" + receiverUsername + '\'' +
                ", fileName='" + fileName + '\'' +
                ", fileSize=" + fileSize +
                ", status='" + status + '\'' +
                '}';
    }
}
