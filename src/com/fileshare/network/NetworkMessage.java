package com.fileshare.network;

import java.io.Serializable;

/**
 * Standard protocol envelope passed over the Object socket streams.
 */
public class NetworkMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private MessageType type;
    private boolean success;
    private String message;
    private Object data;

    /**
     * Default constructor.
     */
    public NetworkMessage() {
    }

    /**
     * Parameterized constructor.
     */
    public NetworkMessage(MessageType type, boolean success, String message, Object data) {
        this.type = type;
        this.success = success;
        this.message = message;
        this.data = data;
    }

    // --- Static Factory Helpers ---

    public static NetworkMessage success(MessageType type, String message, Object data) {
        return new NetworkMessage(type, true, message, data);
    }

    public static NetworkMessage error(MessageType type, String message) {
        return new NetworkMessage(type, false, message, null);
    }

    public static NetworkMessage request(MessageType type, Object data) {
        return new NetworkMessage(type, true, null, data);
    }

    // --- Getters and Setters ---

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "NetworkMessage{" +
                "type=" + type +
                ", success=" + success +
                ", message='" + message + '\'' +
                ", data=" + (data != null ? data.getClass().getSimpleName() : "null") +
                '}';
    }
}
