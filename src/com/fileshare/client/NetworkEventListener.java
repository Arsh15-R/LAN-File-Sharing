package com.fileshare.client;

import com.fileshare.network.NetworkMessage;

/**
 * Callback listener for asynchronous network events from the FileShare server.
 */
public interface NetworkEventListener {

    /**
     * Fired when the socket successfully connects to the server.
     */
    void onConnected();

    /**
     * Fired when disconnected from the server.
     *
     * @param reason human-readable disconnection explanation
     */
    void onDisconnected(String reason);

    /**
     * Fired when a protocol message is received from the server.
     *
     * @param message decoded NetworkMessage
     */
    void onMessageReceived(NetworkMessage message);
}
