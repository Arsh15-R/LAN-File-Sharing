package com.fileshare.client;

import com.fileshare.beans.UserBean;
import com.fileshare.network.NetworkMessage;
import com.fileshare.utils.Constants;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages the TCP socket connection, stream serialization, and continuous receiver thread for the client.
 */
public class ClientSocketManager {

    private static ClientSocketManager instance;

    private Socket socket;
    private ObjectOutputStream oos;
    private ObjectInputStream ois;

    private String host;
    private int port;
    private volatile boolean connected = false;

    private UserBean currentUser;
    private Thread receiverThread;
    private final List<NetworkEventListener> listeners = new CopyOnWriteArrayList<>();

    private ClientSocketManager() {
    }

    public static synchronized ClientSocketManager getInstance() {
        if (instance == null) {
            instance = new ClientSocketManager();
        }
        return instance;
    }

    public void addListener(NetworkEventListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(NetworkEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Establishes socket connection and initializes Object streams.
     */
    public synchronized void connect(String host, int port) throws IOException {
        if (connected && socket != null && !socket.isClosed()) {
            return; // Already connected
        }

        this.host = host;
        this.port = port;

        socket = new Socket();
        // High-performance socket tuning for large file streaming
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
        try {
            socket.setSendBufferSize(Constants.SOCKET_BUFFER_SIZE);
            socket.setReceiveBufferSize(Constants.SOCKET_BUFFER_SIZE);
        } catch (SocketException ignored) {
        }

        // 5-second connection timeout
        socket.connect(new InetSocketAddress(host, port), 5000);

        // OOS must be initialized and flushed before OIS to avoid handshake deadlock
        oos = new ObjectOutputStream(socket.getOutputStream());
        oos.flush();
        ois = new ObjectInputStream(socket.getInputStream());

        connected = true;

        // Start background message receiver
        receiverThread = new Thread(this::listenForMessages, "FileShare-ReceiverThread");
        receiverThread.setDaemon(true);
        receiverThread.start();

        for (NetworkEventListener listener : listeners) {
            listener.onConnected();
        }
    }

    /**
     * Continuous background loop reading protocol messages from the server.
     */
    private void listenForMessages() {
        try {
            while (connected && !socket.isClosed()) {
                Object obj = ois.readObject();
                if (obj instanceof NetworkMessage) {
                    NetworkMessage msg = (NetworkMessage) obj;
                    for (NetworkEventListener listener : listeners) {
                        try {
                            listener.onMessageReceived(msg);
                        } catch (Exception ex) {
                            System.err.println("[ClientSocketManager] Listener dispatch error: " + ex.getMessage());
                        }
                    }
                }
            }
        } catch (EOFException | SocketException e) {
            handleDisconnect("Connection closed by server.");
        } catch (Exception e) {
            handleDisconnect("Network stream error: " + e.getMessage());
        }
    }

    /**
     * Sends a protocol message over the network safely.
     */
    public synchronized void sendMessage(NetworkMessage msg) throws IOException {
        if (!connected || socket == null || socket.isClosed()) {
            throw new IOException("Socket is not connected to server.");
        }
        oos.writeObject(msg);
        oos.flush();
        oos.reset(); // Crucial: clear Java serialization object cache table
    }

    /**
     * Disconnects from the server gracefully.
     */
    public synchronized void disconnect() {
        if (!connected) return;
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (ois != null) ois.close();
        } catch (IOException ignored) {
        }
        try {
            if (oos != null) oos.close();
        } catch (IOException ignored) {
        }
        handleDisconnect("Disconnected by user.");
    }

    private void handleDisconnect(String reason) {
        connected = false;
        currentUser = null;
        for (NetworkEventListener listener : listeners) {
            try {
                listener.onDisconnected(reason);
            } catch (Exception ignored) {
            }
        }
    }

    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }

    public UserBean getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(UserBean currentUser) {
        this.currentUser = currentUser;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}
