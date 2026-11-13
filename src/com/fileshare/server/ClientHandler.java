package com.fileshare.server;

import com.fileshare.beans.FileTransferBean;
import com.fileshare.beans.LoginBean;
import com.fileshare.beans.TransferHistoryBean;
import com.fileshare.beans.UserBean;
import com.fileshare.network.FileChunk;
import com.fileshare.network.MessageType;
import com.fileshare.network.NetworkMessage;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;

/**
 * Handles communication with an individual connected client in a dedicated worker thread.
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final FileServer server;
    private ObjectOutputStream oos;
    private ObjectInputStream ois;
    private String authenticatedUser;
    private volatile boolean running = true;

    public ClientHandler(Socket socket, FileServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            // Establish Object streams: OOS must be flushed before creating OIS to prevent stream deadlock
            oos = new ObjectOutputStream(socket.getOutputStream());
            oos.flush();
            ois = new ObjectInputStream(socket.getInputStream());

            while (running && !socket.isClosed()) {
                Object obj = ois.readObject();
                if (obj instanceof NetworkMessage) {
                    processMessage((NetworkMessage) obj);
                }
            }
        } catch (EOFException | SocketException e) {
            // Normal client disconnection or connection drop
            server.log("Client disconnected: " + (authenticatedUser != null ? authenticatedUser : socket.getRemoteSocketAddress()));
        } catch (Exception e) {
            server.log("Error handling client " + (authenticatedUser != null ? authenticatedUser : "unauthenticated") + ": " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    /**
     * Processes incoming protocol messages from the client.
     */
    private void processMessage(NetworkMessage msg) throws IOException {
        if (msg == null || msg.getType() == null) return;

        switch (msg.getType()) {
            case LOGIN_REQUEST:
                handleLogin((LoginBean) msg.getData());
                break;

            case REGISTER_REQUEST:
                handleRegistration((UserBean) msg.getData());
                break;

            case LOGOUT_REQUEST:
                handleLogout();
                break;

            case USER_LIST_REQUEST:
                handleUserListRequest();
                break;

            case TRANSFER_REQUEST:
                handleTransferRequest((FileTransferBean) msg.getData());
                break;

            case TRANSFER_ACCEPT:
                handleTransferAccept((FileTransferBean) msg.getData());
                break;

            case TRANSFER_REJECT:
                handleTransferReject((FileTransferBean) msg.getData());
                break;

            case TRANSFER_CHUNK:
                handleTransferChunk((FileChunk) msg.getData());
                break;

            case TRANSFER_COMPLETE:
                handleTransferComplete((FileTransferBean) msg.getData());
                break;

            case TRANSFER_CANCEL:
                handleTransferCancel((FileTransferBean) msg.getData());
                break;

            case HISTORY_REQUEST:
                handleHistoryRequest();
                break;

            default:
                server.log("Unknown message type received: " + msg.getType());
                break;
        }
    }

    // --- Message Handlers ---

    private void handleLogin(LoginBean loginBean) {
        if (loginBean == null || loginBean.getUsername() == null || loginBean.getPassword() == null) {
            sendMessageSafe(NetworkMessage.error(MessageType.LOGIN_RESPONSE, "Invalid credentials provided."));
            return;
        }

        String username = loginBean.getUsername().trim();

        if (server.isUserOnline(username)) {
            sendMessageSafe(NetworkMessage.error(MessageType.LOGIN_RESPONSE, "User '" + username + "' is already logged in on another device."));
            return;
        }

        UserBean user = server.getUserDAO().authenticateUser(username, loginBean.getPassword());
        if (user != null) {
            this.authenticatedUser = user.getUsername();
            boolean registered = server.registerOnlineUser(this.authenticatedUser, this);
            if (registered) {
                sendMessageSafe(NetworkMessage.success(MessageType.LOGIN_RESPONSE, "Login successful.", user));
            } else {
                sendMessageSafe(NetworkMessage.error(MessageType.LOGIN_RESPONSE, "User session conflict. Try again."));
            }
        } else {
            sendMessageSafe(NetworkMessage.error(MessageType.LOGIN_RESPONSE, "Incorrect username or password."));
        }
    }

    private void handleRegistration(UserBean user) {
        if (user == null || user.getUsername() == null || user.getPassword() == null) {
            sendMessageSafe(NetworkMessage.error(MessageType.REGISTER_RESPONSE, "Invalid registration data."));
            return;
        }

        String username = user.getUsername().trim();
        String password = user.getPassword();
        String email = user.getEmail() != null ? user.getEmail().trim() : "";
        String fullName = user.getFullName() != null ? user.getFullName().trim() : "";

        // Validations
        if (username.length() < 3 || username.contains(" ")) {
            sendMessageSafe(NetworkMessage.error(MessageType.REGISTER_RESPONSE, "Username must be at least 3 characters and contain no spaces."));
            return;
        }
        if (password.length() < 6) {
            sendMessageSafe(NetworkMessage.error(MessageType.REGISTER_RESPONSE, "Password must be at least 6 characters."));
            return;
        }
        if (fullName.isEmpty()) {
            sendMessageSafe(NetworkMessage.error(MessageType.REGISTER_RESPONSE, "Full name cannot be empty."));
            return;
        }
        if (!email.contains("@") || !email.contains(".")) {
            sendMessageSafe(NetworkMessage.error(MessageType.REGISTER_RESPONSE, "Please provide a valid email address."));
            return;
        }

        if (server.getUserDAO().isUsernameTaken(username)) {
            sendMessageSafe(NetworkMessage.error(MessageType.REGISTER_RESPONSE, "Username '" + username + "' is already taken."));
            return;
        }

        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(fullName);

        boolean ok = server.getUserDAO().registerUser(user);
        if (ok) {
            server.log("New user registered: " + username);
            sendMessageSafe(NetworkMessage.success(MessageType.REGISTER_RESPONSE, "Account registered successfully! You may now log in.", null));
        } else {
            sendMessageSafe(NetworkMessage.error(MessageType.REGISTER_RESPONSE, "Database error during registration. Check server logs."));
        }
    }

    private void handleLogout() {
        if (authenticatedUser != null) {
            String u = authenticatedUser;
            authenticatedUser = null;
            server.unregisterOnlineUser(u);
        }
        disconnect("User logged out.");
    }

    private void handleUserListRequest() {
        List<String> list = server.getOnlineUsernames();
        sendMessageSafe(NetworkMessage.success(MessageType.USER_LIST_UPDATE, "Active users", list));
    }

    private void handleTransferRequest(FileTransferBean transfer) {
        if (transfer == null) return;

        // Validation
        if (authenticatedUser == null || !authenticatedUser.equals(transfer.getSenderUsername())) {
            sendMessageSafe(NetworkMessage.error(MessageType.TRANSFER_FAILED, "Unauthorized sender."));
            return;
        }

        String receiverName = transfer.getReceiverUsername();
        if (receiverName == null || receiverName.trim().isEmpty()) {
            sendMessageSafe(NetworkMessage.error(MessageType.TRANSFER_FAILED, "Receiver is not specified."));
            return;
        }

        if (receiverName.equalsIgnoreCase(authenticatedUser)) {
            sendMessageSafe(NetworkMessage.error(MessageType.TRANSFER_FAILED, "Cannot transfer file to yourself."));
            return;
        }

        if (!server.isUserOnline(receiverName)) {
            sendMessageSafe(NetworkMessage.error(MessageType.TRANSFER_FAILED, "Receiver '" + receiverName + "' is currently offline."));
            return;
        }

        if (transfer.getFileName() == null || transfer.getFileName().trim().isEmpty()) {
            sendMessageSafe(NetworkMessage.error(MessageType.TRANSFER_FAILED, "Invalid or empty filename."));
            return;
        }

        if (transfer.getFileSize() <= 0) {
            sendMessageSafe(NetworkMessage.error(MessageType.TRANSFER_FAILED, "File size must be greater than 0 bytes."));
            return;
        }

        // Save transfer in database
        transfer.setStatus(FileTransferBean.STATUS_PENDING);
        int dbId = server.getTransferDAO().saveTransfer(transfer);
        transfer.setId(dbId);

        server.registerActiveTransfer(transfer);
        server.log("Transfer requested: " + transfer.getFileName() + " (" + transfer.getFileSize() + " bytes) from "
                + transfer.getSenderUsername() + " to " + transfer.getReceiverUsername());

        // Forward request to the receiver client
        ClientHandler receiverHandler = server.getClient(receiverName);
        if (receiverHandler != null) {
            receiverHandler.sendMessageSafe(NetworkMessage.request(MessageType.TRANSFER_REQUEST, transfer));
        } else {
            sendMessageSafe(NetworkMessage.error(MessageType.TRANSFER_FAILED, "Receiver went offline unexpectedly."));
        }
    }

    private void handleTransferAccept(FileTransferBean transfer) {
        if (transfer == null) return;
        FileTransferBean active = server.getActiveTransfer(transfer.getTransferUuid());
        if (active == null) return;

        active.setStatus(FileTransferBean.STATUS_IN_PROGRESS);
        server.getTransferDAO().updateTransferStatus(active.getTransferUuid(), FileTransferBean.STATUS_IN_PROGRESS, 0);

        server.log("Transfer accepted: " + active.getFileName() + " by " + active.getReceiverUsername());

        ClientHandler senderHandler = server.getClient(active.getSenderUsername());
        if (senderHandler != null) {
            senderHandler.sendMessageSafe(NetworkMessage.request(MessageType.TRANSFER_ACCEPT, active));
        }
    }

    private void handleTransferReject(FileTransferBean transfer) {
        if (transfer == null) return;
        FileTransferBean active = server.getActiveTransfer(transfer.getTransferUuid());
        if (active == null) return;

        active.setStatus(FileTransferBean.STATUS_CANCELLED);
        server.getTransferDAO().updateTransferStatus(active.getTransferUuid(), FileTransferBean.STATUS_CANCELLED, 0);
        server.removeActiveTransfer(active.getTransferUuid());

        server.log("Transfer rejected: " + active.getFileName() + " by " + active.getReceiverUsername());

        ClientHandler senderHandler = server.getClient(active.getSenderUsername());
        if (senderHandler != null) {
            senderHandler.sendMessageSafe(NetworkMessage.error(MessageType.TRANSFER_REJECT,
                    "Transfer rejected by recipient '" + active.getReceiverUsername() + "'."));
        }
    }

    private void handleTransferChunk(FileChunk chunk) {
        if (chunk == null) return;
        FileTransferBean active = server.getActiveTransfer(chunk.getTransferUuid());
        if (active == null) return;

        // Route chunk to receiver
        ClientHandler receiverHandler = server.getClient(active.getReceiverUsername());
        if (receiverHandler != null) {
            receiverHandler.sendMessageSafe(NetworkMessage.request(MessageType.TRANSFER_CHUNK, chunk));
        } else {
            // Receiver disconnected during streaming
            server.log("Transfer failed: Receiver disconnected during transmission of " + active.getFileName());
            active.setStatus(FileTransferBean.STATUS_FAILED);
            server.getTransferDAO().updateTransferStatus(active.getTransferUuid(), FileTransferBean.STATUS_FAILED, active.getTransferredBytes());
            server.removeActiveTransfer(active.getTransferUuid());

            ClientHandler senderHandler = server.getClient(active.getSenderUsername());
            if (senderHandler != null) {
                senderHandler.sendMessageSafe(NetworkMessage.error(MessageType.TRANSFER_FAILED,
                        "Transfer failed: receiver disconnected unexpectedly."));
            }
        }
    }

    private void handleTransferComplete(FileTransferBean transfer) {
        if (transfer == null) return;
        FileTransferBean active = server.getActiveTransfer(transfer.getTransferUuid());
        if (active == null) return;

        active.setStatus(FileTransferBean.STATUS_COMPLETED);
        active.setTransferredBytes(active.getFileSize());
        server.getTransferDAO().updateTransferStatus(active.getTransferUuid(), FileTransferBean.STATUS_COMPLETED, active.getFileSize());
        server.removeActiveTransfer(active.getTransferUuid());

        server.log("Transfer completed: " + active.getFileName() + " to " + active.getReceiverUsername());

        ClientHandler receiverHandler = server.getClient(active.getReceiverUsername());
        if (receiverHandler != null) {
            receiverHandler.sendMessageSafe(NetworkMessage.request(MessageType.TRANSFER_COMPLETE, active));
        }
    }

    private void handleTransferCancel(FileTransferBean transfer) {
        if (transfer == null) return;
        FileTransferBean active = server.getActiveTransfer(transfer.getTransferUuid());
        if (active == null) return;

        active.setStatus(FileTransferBean.STATUS_CANCELLED);
        server.getTransferDAO().updateTransferStatus(active.getTransferUuid(), FileTransferBean.STATUS_CANCELLED, active.getTransferredBytes());
        server.removeActiveTransfer(active.getTransferUuid());

        server.log("Transfer cancelled: " + active.getFileName());

        // Notify counter-party
        String otherUser = authenticatedUser.equals(active.getSenderUsername()) ? active.getReceiverUsername() : active.getSenderUsername();
        ClientHandler otherHandler = server.getClient(otherUser);
        if (otherHandler != null) {
            otherHandler.sendMessageSafe(NetworkMessage.error(MessageType.TRANSFER_CANCEL,
                    "Transfer was cancelled by '" + authenticatedUser + "'."));
        }
    }

    private void handleHistoryRequest() {
        if (authenticatedUser == null) return;
        List<TransferHistoryBean> history = server.getTransferDAO().getHistoryForUser(authenticatedUser);
        sendMessageSafe(NetworkMessage.success(MessageType.HISTORY_RESPONSE, "Transfer history", history));
    }

    // --- Stream Communications ---

    /**
     * Sends message synchronously with cache reset to prevent memory leaks in serialization.
     */
    public synchronized void sendMessageSafe(NetworkMessage msg) {
        if (!running || socket.isClosed()) return;
        try {
            oos.writeObject(msg);
            oos.flush();
            oos.reset(); // Crucial: clears Java object serialization cache table
        } catch (IOException e) {
            server.log("Failed to send message to " + (authenticatedUser != null ? authenticatedUser : "client") + ": " + e.getMessage());
            disconnect("I/O write failure.");
        }
    }

    public synchronized void disconnect(String reason) {
        if (!running) return;
        running = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    private void cleanup() {
        running = false;
        if (authenticatedUser != null) {
            String u = authenticatedUser;
            authenticatedUser = null;
            server.unregisterOnlineUser(u);
        }
        try {
            if (ois != null) ois.close();
        } catch (IOException ignored) {}
        try {
            if (oos != null) oos.close();
        } catch (IOException ignored) {}
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }

    public String getAuthenticatedUser() {
        return authenticatedUser;
    }
}
