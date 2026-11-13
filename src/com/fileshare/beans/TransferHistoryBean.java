package com.fileshare.beans;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * JavaBean representing a File Transfer record for historical auditing and UI tables.
 */
public class TransferHistoryBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private int historyId;
    private String transferUuid;
    private String senderUsername;
    private String receiverUsername;
    private String fileName;
    private long fileSize;
    private String status;
    private Timestamp timestamp;

    /**
     * Default no-argument constructor (required for JavaBeans).
     */
    public TransferHistoryBean() {
    }

    /**
     * Parameterized constructor.
     */
    public TransferHistoryBean(int historyId, String transferUuid, String senderUsername, String receiverUsername,
                               String fileName, long fileSize, String status, Timestamp timestamp) {
        this.historyId = historyId;
        this.transferUuid = transferUuid;
        this.senderUsername = senderUsername;
        this.receiverUsername = receiverUsername;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.status = status;
        this.timestamp = timestamp;
    }

    // --- Getters and Setters ---

    public int getHistoryId() {
        return historyId;
    }

    public void setHistoryId(int historyId) {
        this.historyId = historyId;
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

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "TransferHistoryBean{" +
                "id=" + historyId +
                ", sender='" + senderUsername + '\'' +
                ", receiver='" + receiverUsername + '\'' +
                ", file='" + fileName + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
