package com.fileshare.server;

import com.fileshare.beans.FileTransferBean;
import com.fileshare.dao.FileTransferDAO;
import com.fileshare.dao.UserDAO;
import com.fileshare.database.DatabaseConnection;
import com.fileshare.network.MessageType;
import com.fileshare.network.NetworkMessage;
import com.fileshare.utils.Constants;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Multi-threaded LAN File Sharing Server.
 * Accepts client connections, manages routing, and interfaces with the MySQL database.
 */
public class FileServer {

    public interface ServerListener {
        void onLog(String message);
        void onClientsChanged(List<String> onlineUsernames);
        void onServerStateChanged(boolean isRunning);
    }

    private final int port;
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    private final Map<String, ClientHandler> onlineClients = new ConcurrentHashMap<>();
    private final Map<String, FileTransferBean> activeTransfers = new ConcurrentHashMap<>();

    private final UserDAO userDAO = new UserDAO();
    private final FileTransferDAO transferDAO = new FileTransferDAO();

    private ExecutorService threadPool;
    private final List<ServerListener> listeners = new CopyOnWriteArrayList<>();

    public FileServer(int port) {
        this.port = port;
    }

    public void addListener(ServerListener listener) {
        if (listener != null) listeners.add(listener);
    }

    public void removeListener(ServerListener listener) {
        listeners.remove(listener);
    }

    public void log(String message) {
        String logEntry = String.format("[%s] %s", new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()), message);
        System.out.println(logEntry);
        for (ServerListener listener : listeners) {
            listener.onLog(logEntry);
        }
    }

    private void notifyClientsChanged() {
        List<String> list = new ArrayList<>(onlineClients.keySet());
        for (ServerListener listener : listeners) {
            listener.onClientsChanged(list);
        }
    }

    private void notifyServerStateChanged(boolean isRunning) {
        for (ServerListener listener : listeners) {
            listener.onServerStateChanged(isRunning);
        }
    }

    /**
     * Starts the ServerSocket and listens for incoming client connections.
     */
    public synchronized void start() throws IOException {
        if (running) return;

        // Verify and initialize database tables
        log("Testing database connection...");
        if (DatabaseConnection.testConnection()) {
            log("Database connected. Initializing schema...");
            DatabaseConnection.initializeDatabase();
            // Reset any stale ONLINE statuses from previous server runs
            userDAO.resetAllUserStatuses();
        } else {
            log("WARNING: Could not connect to MySQL. Verify resources/db.properties and MySQL service.");
        }

        serverSocket = new ServerSocket(port);
        running = true;
        threadPool = Executors.newCachedThreadPool();

        log("FileServer started on port " + port + ". Ready for client connections.");
        notifyServerStateChanged(true);

        Thread acceptThread = new Thread(() -> {
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    clientSocket.setTcpNoDelay(true);
                    clientSocket.setKeepAlive(true);
                    try {
                        clientSocket.setSendBufferSize(Constants.SOCKET_BUFFER_SIZE);
                        clientSocket.setReceiveBufferSize(Constants.SOCKET_BUFFER_SIZE);
                    } catch (Exception ignored) {
                    }
                    log("Incoming socket connection from " + clientSocket.getRemoteSocketAddress());
                    ClientHandler handler = new ClientHandler(clientSocket, this);
                    threadPool.submit(handler);
                } catch (IOException e) {
                    if (!running) {
                        break; // Normal shutdown
                    }
                    log("Socket accept error: " + e.getMessage());
                }
            }
        }, "FileServer-AcceptThread");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    /**
     * Gracefully stops the server and disconnects active clients.
     */
    public synchronized void stop() {
        if (!running) return;
        running = false;
        log("Stopping FileServer...");

        // Disconnect all clients
        for (ClientHandler handler : onlineClients.values()) {
            handler.disconnect("Server is shutting down.");
        }
        onlineClients.clear();
        notifyClientsChanged();

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }

        if (threadPool != null) {
            threadPool.shutdownNow();
        }

        userDAO.resetAllUserStatuses();
        notifyServerStateChanged(false);
        log("FileServer stopped.");
    }

    public boolean isRunning() {
        return running;
    }

    // --- Client Management ---

    public synchronized boolean registerOnlineUser(String username, ClientHandler handler) {
        if (onlineClients.containsKey(username)) {
            return false;
        }
        onlineClients.put(username, handler);
        log("User '" + username + "' is now ONLINE.");
        notifyClientsChanged();
        broadcastOnlineUsers();
        return true;
    }

    public synchronized void unregisterOnlineUser(String username) {
        if (username != null && onlineClients.remove(username) != null) {
            log("User '" + username + "' went OFFLINE.");
            userDAO.updateUserStatus(username, Constants.STATUS_OFFLINE);
            transferDAO.markIncompleteTransfersFailedForUser(username);

            // Cancel any in-flight active transfers for this user
            for (Map.Entry<String, FileTransferBean> entry : activeTransfers.entrySet()) {
                FileTransferBean t = entry.getValue();
                if (username.equals(t.getSenderUsername()) || username.equals(t.getReceiverUsername())) {
                    String peer = username.equals(t.getSenderUsername()) ? t.getReceiverUsername() : t.getSenderUsername();
                    ClientHandler peerHandler = onlineClients.get(peer);
                    if (peerHandler != null) {
                        peerHandler.sendMessageSafe(NetworkMessage.error(MessageType.TRANSFER_FAILED,
                                "Transfer cancelled: peer '" + username + "' disconnected."));
                    }
                    transferDAO.updateTransferStatus(t.getTransferUuid(), FileTransferBean.STATUS_FAILED, t.getTransferredBytes());
                    activeTransfers.remove(entry.getKey());
                }
            }

            notifyClientsChanged();
            broadcastOnlineUsers();
        }
    }

    public boolean isUserOnline(String username) {
        return onlineClients.containsKey(username);
    }

    public ClientHandler getClient(String username) {
        return onlineClients.get(username);
    }

    /**
     * Broadcasts the updated list of online usernames to all connected clients.
     */
    public void broadcastOnlineUsers() {
        List<String> usersList = new ArrayList<>(onlineClients.keySet());
        NetworkMessage msg = NetworkMessage.success(MessageType.USER_LIST_UPDATE, "Online users update", usersList);

        for (ClientHandler handler : onlineClients.values()) {
            handler.sendMessageSafe(msg);
        }
    }

    // --- Transfer Tracking ---

    public void registerActiveTransfer(FileTransferBean transfer) {
        activeTransfers.put(transfer.getTransferUuid(), transfer);
    }

    public FileTransferBean getActiveTransfer(String transferUuid) {
        return activeTransfers.get(transferUuid);
    }

    public void removeActiveTransfer(String transferUuid) {
        activeTransfers.remove(transferUuid);
    }

    public UserDAO getUserDAO() {
        return userDAO;
    }

    public FileTransferDAO getTransferDAO() {
        return transferDAO;
    }

    public List<String> getOnlineUsernames() {
        return new ArrayList<>(onlineClients.keySet());
    }

    /**
     * Entry point to launch FileServer directly.
     */
    public static void main(String[] args) {
        boolean headless = false;
        int port = Constants.DEFAULT_PORT;

        for (String arg : args) {
            if ("--headless".equalsIgnoreCase(arg) || "--cli".equalsIgnoreCase(arg)) {
                headless = true;
            } else if (arg.startsWith("--port=")) {
                try {
                    port = Integer.parseInt(arg.substring(7));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        if (headless) {
            FileServer server = new FileServer(port);
            try {
                server.start();
                System.out.println("Press Ctrl+C to stop server.");
                while (server.isRunning()) {
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // Launch Server GUI
            javax.swing.SwingUtilities.invokeLater(() -> {
                ServerFrame serverFrame = new ServerFrame();
                serverFrame.setVisible(true);
            });
        }
    }
}
