package com.fileshare.network;

import java.io.Serializable;

/**
 * Encapsulates a single byte buffer chunk of an in-flight file transfer.
 */
public class FileChunk implements Serializable {

    private static final long serialVersionUID = 1L;

    private String transferUuid;
    private byte[] data;
    private int bytesRead;
    private long offset;
    private boolean isLast;

    public FileChunk() {
    }

    public FileChunk(String transferUuid, byte[] data, int bytesRead, long offset, boolean isLast) {
        this.transferUuid = transferUuid;
        this.data = data;
        this.bytesRead = bytesRead;
        this.offset = offset;
        this.isLast = isLast;
    }

    public String getTransferUuid() {
        return transferUuid;
    }

    public void setTransferUuid(String transferUuid) {
        this.transferUuid = transferUuid;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public int getBytesRead() {
        return bytesRead;
    }

    public void setBytesRead(int bytesRead) {
        this.bytesRead = bytesRead;
    }

    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public boolean isLast() {
        return isLast;
    }

    public void setLast(boolean last) {
        isLast = last;
    }
}
