package com.fileshare.client;

import com.fileshare.beans.FileTransferBean;
import com.fileshare.beans.TransferHistoryBean;
import com.fileshare.beans.UserBean;
import com.fileshare.network.FileChunk;
import com.fileshare.network.MessageType;
import com.fileshare.network.NetworkMessage;
import com.fileshare.utils.Constants;
import com.fileshare.utils.FormatUtils;
import com.fileshare.utils.UIUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Main GUI Dashboard for the LAN File Sharing Client.
 * Implements MVC client presentation, asynchronous SwingWorker file streaming,
 * real-time JProgressBar tracking, and transfer history auditing.
 */
public class DashboardFrame extends JFrame implements NetworkEventListener {

    private final ClientSocketManager socketManager = ClientSocketManager.getInstance();
    private final UserBean currentUser;

    // --- GUI Components ---
    // Header
    private JLabel userWelcomeLabel;
    private JButton refreshUsersBtn;
    private JButton logoutBtn;

    // Tab 1: File Transfer
    private DefaultListModel<String> onlineUsersListModel;
    private JList<String> onlineUsersList;

    private JTextField receiverField;
    private JButton clearReceiverBtn;
    private JTextField selectedFilePathField;
    private JButton chooseFileBtn;
    private JLabel fileDetailsLabel;

    private JButton sendFileBtn;
    private JButton cancelTransferBtn;

    private JLabel transferStatusLabel;
    private JProgressBar transferProgressBar;
    private JLabel progressDetailsLabel;

    // Tab 2: Transfer History
    private JTable historyTable;
    private DefaultTableModel historyTableModel;
    private JButton refreshHistoryBtn;

    // --- State Tracking ---
    private File selectedFile;
    private FileSenderWorker activeSenderWorker;

    // Incoming transfer state
    private FileTransferBean incomingTransfer;
    private BufferedOutputStream incomingOutputStream;
    private File incomingTargetFile;
    private long incomingBytesReceived = 0;
    private long incomingStartTime = 0;

    public DashboardFrame() {
        this.currentUser = socketManager.getCurrentUser();
        if (this.currentUser == null) {
            throw(new IllegalStateException("Cannot open dashboard without authenticated user."));
        }

        setTitle(Constants.APP_NAME + " - Dashboard [" + currentUser.getUsername() + "]");
        setSize(900, 620);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        initUI();
        initEvents();

        socketManager.addListener(this);

        // Fetch initial online users and history
        requestOnlineUsers();
        requestTransferHistory();
    }

    private void initUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 15, 12, 15));

        // --- 1. Top Header Panel ---
        JPanel headerPanel = new JPanel(new BorderLayout(10, 10));
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(),
                new EmptyBorder(10, 15, 10, 15)
        ));

        JPanel userPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JLabel userIcon = new JLabel("👤");
        userIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 22));
        userWelcomeLabel = new JLabel("Logged in: " + currentUser.getUsername());
        userWelcomeLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        userWelcomeLabel.setForeground(new Color(25, 118, 210));
        userPanel.add(userIcon);
        userPanel.add(userWelcomeLabel);

        JPanel headerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        refreshUsersBtn = UIUtils.createSecondaryButton("Refresh Users");
        logoutBtn = UIUtils.createPrimaryButton("Logout", UIUtils.DANGER_RED, Color.WHITE);
        headerButtons.add(refreshUsersBtn);
        headerButtons.add(logoutBtn);

        headerPanel.add(userPanel, BorderLayout.WEST);
        headerPanel.add(headerButtons, BorderLayout.EAST);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // --- 2. Main Tabbed Pane ---
        JTabbedPane tabbedPane = new JTabbedPane();

        // TAB 1: File Transfer
        JPanel transferTab = createTransferTab();
        tabbedPane.addTab("File Transfer", transferTab);

        // TAB 2: Transfer History
        JPanel historyTab = createHistoryTab();
        tabbedPane.addTab("Transfer History", historyTab);

        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        setContentPane(mainPanel);
    }

    /**
     * Builds Tab 1: Split pane with online users on left and transfer control on right.
     */
    private JPanel createTransferTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Left Panel: Online Users
        JPanel usersPanel = new JPanel(new BorderLayout(5, 5));
        usersPanel.setPreferredSize(new Dimension(240, 400));
        usersPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Online Users on LAN", TitledBorder.LEFT, TitledBorder.TOP
        ));

        onlineUsersListModel = new DefaultListModel<>();
        onlineUsersList = new JList<>(onlineUsersListModel);
        onlineUsersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        onlineUsersList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        onlineUsersList.setCellRenderer(new OnlineUserListRenderer());
        JScrollPane userScroll = new JScrollPane(onlineUsersList);

        JLabel selectHint = new JLabel("Click a user to select as receiver", SwingConstants.CENTER);
        selectHint.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        selectHint.setForeground(Color.GRAY);
        selectHint.setBorder(new EmptyBorder(4, 2, 4, 2));

        usersPanel.add(userScroll, BorderLayout.CENTER);
        usersPanel.add(selectHint, BorderLayout.SOUTH);

        // Right Panel: Transfer Controller
        JPanel controlPanel = new JPanel(new BorderLayout(12, 12));
        controlPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Transfer Controls", TitledBorder.LEFT, TitledBorder.TOP
        ));

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 8, 8, 8);

        // Row 1: Receiver Selection
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Selected Receiver:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        receiverField = new JTextField();
        receiverField.setEditable(false);
        receiverField.setFont(new Font("Segoe UI", Font.BOLD, 12));
        formPanel.add(receiverField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        clearReceiverBtn = UIUtils.createSecondaryButton("Clear");
        formPanel.add(clearReceiverBtn, gbc);

        // Row 2: File Chooser
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Choose File:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        selectedFilePathField = new JTextField();
        selectedFilePathField.setEditable(false);
        formPanel.add(selectedFilePathField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        chooseFileBtn = UIUtils.createSecondaryButton("Browse...");
        formPanel.add(chooseFileBtn, gbc);

        // Row 3: File Details Label
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        fileDetailsLabel = new JLabel("No file selected.");
        fileDetailsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        fileDetailsLabel.setForeground(Color.DARK_GRAY);
        formPanel.add(fileDetailsLabel, gbc);

        // Row 4: Buttons
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 3;
        JPanel actionButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        sendFileBtn = UIUtils.createPrimaryButton("Send File", UIUtils.PRIMARY_BLUE, Color.WHITE);
        sendFileBtn.setPreferredSize(new Dimension(140, 38));

        cancelTransferBtn = UIUtils.createPrimaryButton("Cancel", UIUtils.DANGER_RED, Color.WHITE);
        cancelTransferBtn.setPreferredSize(new Dimension(140, 38));
        cancelTransferBtn.setEnabled(false);

        actionButtonPanel.add(sendFileBtn);
        actionButtonPanel.add(cancelTransferBtn);
        formPanel.add(actionButtonPanel, gbc);

        // Row 5: Progress & Status Section
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 3;
        JPanel progressSection = new JPanel(new BorderLayout(8, 8));
        progressSection.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(),
                new EmptyBorder(10, 12, 10, 12)
        ));

        JPanel statusLine = new JPanel(new BorderLayout());
        transferStatusLabel = new JLabel("Status: Idle");
        transferStatusLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        progressDetailsLabel = new JLabel("0 / 0 bytes (0%)");
        progressDetailsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLine.add(transferStatusLabel, BorderLayout.WEST);
        statusLine.add(progressDetailsLabel, BorderLayout.EAST);

        transferProgressBar = new JProgressBar(0, 100);
        transferProgressBar.setStringPainted(true);
        transferProgressBar.setPreferredSize(new Dimension(300, 24));

        progressSection.add(statusLine, BorderLayout.NORTH);
        progressSection.add(transferProgressBar, BorderLayout.CENTER);

        formPanel.add(progressSection, gbc);

        controlPanel.add(formPanel, BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, usersPanel, controlPanel);
        splitPane.setDividerLocation(250);
        splitPane.setContinuousLayout(true);
        panel.add(splitPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Builds Tab 2: Transfer History table and controls.
     */
    private JPanel createHistoryTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        String[] columns = {"ID", "UUID", "Sender", "Receiver", "File Name", "File Size", "Status", "Date & Time"};
        historyTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        historyTable = new JTable(historyTableModel);
        historyTable.setRowHeight(24);
        historyTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        historyTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Hide UUID column from view (keep in model)
        historyTable.getColumnModel().getColumn(1).setMinWidth(0);
        historyTable.getColumnModel().getColumn(1).setMaxWidth(0);
        historyTable.getColumnModel().getColumn(1).setWidth(0);

        // Custom status badge renderer
        historyTable.getColumnModel().getColumn(6).setCellRenderer(new StatusCellRenderer());

        JScrollPane scrollPane = new JScrollPane(historyTable);
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        refreshHistoryBtn = UIUtils.createSecondaryButton("Refresh History");
        bottomPanel.add(refreshHistoryBtn);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * Registers AWT Event listeners (ActionEvent, MouseEvent, WindowEvent).
     */
    private void initEvents() {
        // List selection listener ensuring instant receiver field update
        onlineUsersList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = onlineUsersList.getSelectedValue();
                if (selected != null) {
                    receiverField.setText(selected);
                }
            }
        });

        // 1. AWT ActionEvent on Choose File button
        chooseFileBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chooseFileAction();
            }
        });

        // 2. AWT ActionEvent on Send File button
        sendFileBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendFileAction();
            }
        });

        // 3. AWT ActionEvent on Cancel Transfer button
        cancelTransferBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cancelTransferAction();
            }
        });

        // 4. AWT ActionEvent on Clear Receiver button
        clearReceiverBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                receiverField.setText("");
                onlineUsersList.clearSelection();
            }
        });

        // 5. AWT ActionEvent on Refresh Users button
        refreshUsersBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                requestOnlineUsers();
            }
        });

        // 6. AWT ActionEvent on Refresh History button
        refreshHistoryBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                requestTransferHistory();
            }
        });

        // 7. AWT ActionEvent on Logout button
        logoutBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performLogout();
            }
        });

        // 8. AWT MouseEvent on Online Users list selection
        onlineUsersList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                String selected = onlineUsersList.getSelectedValue();
                if (selected != null) {
                    receiverField.setText(selected);
                }
            }
        });

        // 9. AWT WindowEvent on Dashboard close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                performLogout();
            }
        });
    }

    // --- Action Methods ---

    private void chooseFileAction() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select File to Share");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setMultiSelectionEnabled(false);

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (file == null || !file.exists()) {
                JOptionPane.showMessageDialog(this, "Selected file does not exist.", "File Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (file.isDirectory()) {
                JOptionPane.showMessageDialog(this, "Directories cannot be sent directly. Please select a file or archive.", "Invalid Selection", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (file.length() <= 0) {
                JOptionPane.showMessageDialog(this, "The selected file is empty (0 bytes). Cannot send empty files.", "Empty File", JOptionPane.WARNING_MESSAGE);
                return;
            }

            selectedFile = file;
            selectedFilePathField.setText(file.getAbsolutePath());
            fileDetailsLabel.setText("File: " + file.getName() + " | Size: " + FormatUtils.formatFileSize(file.length()));
        }
    }

    private void sendFileAction() {
        String receiver = receiverField.getText().trim();

        // Validation 1: Receiver specified
        if (receiver.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select an online receiver from the list.", "Receiver Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Validation 2: Cannot send to self
        if (receiver.equalsIgnoreCase(currentUser.getUsername())) {
            JOptionPane.showMessageDialog(this, "You cannot send files to yourself.", "Invalid Receiver", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Validation 3: File selected and exists
        if (selectedFile == null || !selectedFile.exists()) {
            JOptionPane.showMessageDialog(this, "Please choose a valid file to send.", "No File Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Validation 4: Non-empty file
        if (selectedFile.length() <= 0) {
            JOptionPane.showMessageDialog(this, "Selected file is empty (0 bytes).", "Empty File", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Setup transfer bean
        FileTransferBean transfer = new FileTransferBean(
                currentUser.getUsername(),
                receiver,
                selectedFile.getName(),
                selectedFile.length()
        );

        sendFileBtn.setEnabled(false);
        chooseFileBtn.setEnabled(false);
        cancelTransferBtn.setEnabled(true);

        transferStatusLabel.setText("Status: Pending (Waiting for " + receiver + " to accept...)");
        transferStatusLabel.setForeground(new Color(230, 124, 0));
        transferProgressBar.setValue(0);
        transferProgressBar.setIndeterminate(true);
        progressDetailsLabel.setText("Negotiating transfer with server...");

        // Send request to server
        new Thread(() -> {
            try {
                NetworkMessage req = NetworkMessage.request(MessageType.TRANSFER_REQUEST, transfer);
                socketManager.sendMessage(req);
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> {
                    transferStatusLabel.setText("Status: Failed");
                    transferStatusLabel.setForeground(new Color(198, 40, 40));
                    JOptionPane.showMessageDialog(DashboardFrame.this, "Network error: " + ex.getMessage(), "Transfer Error", JOptionPane.ERROR_MESSAGE);
                    resetTransferControls();
                });
            }
        }, "SendRequest-Thread").start();
    }

    private void cancelTransferAction() {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to cancel the current file transfer?",
                "Confirm Cancellation",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );
        if (confirm != JOptionPane.YES_OPTION) return;

        // If outgoing sender
        if (activeSenderWorker != null && !activeSenderWorker.isDone()) {
            activeSenderWorker.cancel(true);
        }

        // If incoming receiver
        if (incomingTransfer != null) {
            try {
                if (incomingOutputStream != null) {
                    incomingOutputStream.close();
                }
            } catch (IOException ignored) {}

            try {
                socketManager.sendMessage(NetworkMessage.request(MessageType.TRANSFER_CANCEL, incomingTransfer));
            } catch (IOException ignored) {}

            incomingTransfer = null;
            incomingOutputStream = null;
        }

        transferStatusLabel.setText("Status: Cancelled");
        transferStatusLabel.setForeground(Color.GRAY);
        transferProgressBar.setIndeterminate(false);
        transferProgressBar.setValue(0);
        progressDetailsLabel.setText("Transfer cancelled by user.");
        resetTransferControls();
    }

    private void resetTransferControls() {
        sendFileBtn.setEnabled(true);
        chooseFileBtn.setEnabled(true);
        cancelTransferBtn.setEnabled(false);
        transferProgressBar.setIndeterminate(false);
    }

    private void performLogout() {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to log out and disconnect?",
                "Confirm Logout",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            new Thread(() -> {
                try {
                    socketManager.sendMessage(NetworkMessage.request(MessageType.LOGOUT_REQUEST, null));
                } catch (Exception ignored) {
                }
                socketManager.disconnect();
            }).start();

            socketManager.removeListener(this);
            dispose();

            // Return to LoginFrame
            SwingUtilities.invokeLater(() -> {
                LoginFrame loginFrame = new LoginFrame();
                loginFrame.setVisible(true);
            });
        }
    }

    private void requestOnlineUsers() {
        new Thread(() -> {
            try {
                socketManager.sendMessage(NetworkMessage.request(MessageType.USER_LIST_REQUEST, null));
            } catch (IOException ignored) {
            }
        }).start();
    }

    private void requestTransferHistory() {
        new Thread(() -> {
            try {
                socketManager.sendMessage(NetworkMessage.request(MessageType.HISTORY_REQUEST, null));
            } catch (IOException ignored) {
            }
        }).start();
    }

    // --- NetworkEventListener Callbacks ---

    @Override
    public void onConnected() {
    }

    @Override
    public void onDisconnected(String reason) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this, "Disconnected from server: " + reason, "Connection Lost", JOptionPane.ERROR_MESSAGE);
            socketManager.removeListener(this);
            dispose();
            LoginFrame loginFrame = new LoginFrame();
            loginFrame.setVisible(true);
        });
    }

    @Override
    public void onMessageReceived(NetworkMessage message) {
        SwingUtilities.invokeLater(() -> dispatchNetworkMessage(message));
    }

    private void dispatchNetworkMessage(NetworkMessage message) {
        if (message == null || message.getType() == null) return;

        switch (message.getType()) {
            case USER_LIST_UPDATE:
                handleOnlineUsersUpdate(message);
                break;

            case TRANSFER_REQUEST:
                handleIncomingTransferRequest((FileTransferBean) message.getData());
                break;

            case TRANSFER_ACCEPT:
                handleTransferAcceptedByReceiver((FileTransferBean) message.getData());
                break;

            case TRANSFER_REJECT:
                handleTransferRejected(message.getMessage());
                break;

            case TRANSFER_CHUNK:
                handleIncomingChunk((FileChunk) message.getData());
                break;

            case TRANSFER_COMPLETE:
                handleIncomingTransferComplete((FileTransferBean) message.getData());
                break;

            case TRANSFER_CANCEL:
                handlePeerCancelled(message.getMessage());
                break;

            case TRANSFER_FAILED:
                handleTransferFailed(message.getMessage());
                break;

            case HISTORY_RESPONSE:
                handleHistoryResponse(message);
                break;

            default:
                break;
        }
    }

    @SuppressWarnings("unchecked")
    private void handleOnlineUsersUpdate(NetworkMessage message) {
        if (message.getData() instanceof List) {
            List<String> users = (List<String>) message.getData();
            onlineUsersListModel.clear();
            for (String u : users) {
                if (!u.equalsIgnoreCase(currentUser.getUsername())) {
                    onlineUsersListModel.addElement(u);
                }
            }
        }
    }

    private void handleIncomingTransferRequest(FileTransferBean transfer) {
        if (transfer == null) return;

        String formattedSize = FormatUtils.formatFileSize(transfer.getFileSize());
        String msg = String.format("User '%s' wants to send you a file:\n\nFile: %s\nSize: %s\n\nDo you want to accept this file?",
                transfer.getSenderUsername(), transfer.getFileName(), formattedSize);

        int choice = JOptionPane.showConfirmDialog(this, msg, "Incoming File Transfer Request", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (choice == JOptionPane.YES_OPTION) {
            // Prepare downloads folder
            File downloadsDir = new File(Constants.DEFAULT_DOWNLOADS_DIR);
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs();
            }

            // Ensure unique filename if duplicate already exists
            File targetFile = new File(downloadsDir, transfer.getFileName());
            int counter = 1;
            String baseName = transfer.getFileName();
            String extension = "";
            int dotIdx = baseName.lastIndexOf('.');
            if (dotIdx > 0) {
                extension = baseName.substring(dotIdx);
                baseName = baseName.substring(0, dotIdx);
            }
            while (targetFile.exists()) {
                targetFile = new File(downloadsDir, baseName + " (" + counter + ")" + extension);
                counter++;
            }

            try {
                incomingOutputStream = new BufferedOutputStream(new FileOutputStream(targetFile), Constants.BUFFER_SIZE);
                incomingTargetFile = targetFile;
                incomingTransfer = transfer;
                incomingBytesReceived = 0;
                incomingStartTime = System.currentTimeMillis();

                transferStatusLabel.setText("Status: In Progress (Receiving from " + transfer.getSenderUsername() + ")");
                transferStatusLabel.setForeground(new Color(25, 118, 210));
                transferProgressBar.setIndeterminate(false);
                transferProgressBar.setValue(0);
                progressDetailsLabel.setText("Starting transfer...");
                cancelTransferBtn.setEnabled(true);
                sendFileBtn.setEnabled(false);

                // Notify server of acceptance
                new Thread(() -> {
                    try {
                        socketManager.sendMessage(NetworkMessage.request(MessageType.TRANSFER_ACCEPT, transfer));
                    } catch (IOException e) {
                        SwingUtilities.invokeLater(() -> handleTransferFailed("Error sending acceptance: " + e.getMessage()));
                    }
                }).start();

            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Could not create local file: " + e.getMessage(), "File System Error", JOptionPane.ERROR_MESSAGE);
                rejectTransfer(transfer);
            }

        } else {
            rejectTransfer(transfer);
        }
    }

    private void rejectTransfer(FileTransferBean transfer) {
        new Thread(() -> {
            try {
                socketManager.sendMessage(NetworkMessage.request(MessageType.TRANSFER_REJECT, transfer));
            } catch (IOException ignored) {}
        }).start();
    }

    private void handleTransferAcceptedByReceiver(FileTransferBean transfer) {
        if (selectedFile == null || !selectedFile.exists()) {
            handleTransferFailed("Selected file is no longer available.");
            return;
        }

        transferStatusLabel.setText("Status: In Progress (Streaming to " + transfer.getReceiverUsername() + ")");
        transferStatusLabel.setForeground(new Color(46, 125, 50));
        transferProgressBar.setIndeterminate(false);
        transferProgressBar.setValue(0);

        // Start background file streaming using SwingWorker to prevent GUI freezing
        activeSenderWorker = new FileSenderWorker(selectedFile, transfer);
        activeSenderWorker.execute();
    }

    private void handleTransferRejected(String reason) {
        transferStatusLabel.setText("Status: Failed (Rejected)");
        transferStatusLabel.setForeground(new Color(198, 40, 40));
        progressDetailsLabel.setText(reason != null ? reason : "Receiver declined the file transfer.");
        resetTransferControls();
        JOptionPane.showMessageDialog(this, reason != null ? reason : "Receiver declined the transfer.", "Transfer Declined", JOptionPane.WARNING_MESSAGE);
        requestTransferHistory();
    }

    private void handleIncomingChunk(FileChunk chunk) {
        if (chunk == null || incomingOutputStream == null || incomingTransfer == null) return;
        if (!chunk.getTransferUuid().equals(incomingTransfer.getTransferUuid())) return;

        try {
            incomingOutputStream.write(chunk.getData(), 0, chunk.getBytesRead());
            incomingBytesReceived += chunk.getBytesRead();

            int percent = incomingTransfer.getFileSize() > 0
                    ? (int) Math.min(100, (incomingBytesReceived * 100) / incomingTransfer.getFileSize())
                    : 0;

            long elapsedMs = Math.max(1, System.currentTimeMillis() - incomingStartTime);
            double speedMBps = (incomingBytesReceived / (1024.0 * 1024.0)) / (elapsedMs / 1000.0);
            long remainingBytes = Math.max(0, incomingTransfer.getFileSize() - incomingBytesReceived);
            long etaSec = (long) (remainingBytes / Math.max(1.0, speedMBps * 1024.0 * 1024.0));
            String speedStr = String.format("%.1f MB/s", speedMBps);
            String etaStr = etaSec < 60 ? (etaSec + "s") : ((etaSec / 60) + "m " + (etaSec % 60) + "s");

            transferProgressBar.setValue(percent);
            progressDetailsLabel.setText(String.format("%s / %s (%d%%) • %s • ETA: %s",
                    FormatUtils.formatFileSize(incomingBytesReceived),
                    FormatUtils.formatFileSize(incomingTransfer.getFileSize()),
                    percent, speedStr, etaStr));

        } catch (IOException e) {
            handleTransferFailed("Error writing to file: " + e.getMessage());
            cancelTransferAction();
        }
    }

    private void handleIncomingTransferComplete(FileTransferBean transfer) {
        try {
            if (incomingOutputStream != null) {
                incomingOutputStream.flush();
                incomingOutputStream.close();
                incomingOutputStream = null;
            }
        } catch (IOException ignored) {
        }

        transferStatusLabel.setText("Status: Completed");
        transferStatusLabel.setForeground(new Color(46, 125, 50));
        transferProgressBar.setValue(100);
        progressDetailsLabel.setText("File received completely: " + incomingTargetFile.getName());
        resetTransferControls();

        JOptionPane.showMessageDialog(
                this,
                "File successfully received and saved to:\n" + incomingTargetFile.getAbsolutePath(),
                "Transfer Complete",
                JOptionPane.INFORMATION_MESSAGE
        );

        incomingTransfer = null;
        incomingTargetFile = null;
        incomingBytesReceived = 0;
        requestTransferHistory();
    }

    private void handlePeerCancelled(String reason) {
        cleanupIncomingStream();
        transferStatusLabel.setText("Status: Cancelled");
        transferStatusLabel.setForeground(Color.GRAY);
        progressDetailsLabel.setText(reason != null ? reason : "Transfer cancelled by counter-party.");
        resetTransferControls();
        JOptionPane.showMessageDialog(this, reason != null ? reason : "The file transfer was cancelled.", "Transfer Cancelled", JOptionPane.INFORMATION_MESSAGE);
        requestTransferHistory();
    }

    private void handleTransferFailed(String reason) {
        cleanupIncomingStream();
        transferStatusLabel.setText("Status: Failed");
        transferStatusLabel.setForeground(new Color(198, 40, 40));
        progressDetailsLabel.setText(reason != null ? reason : "Transfer failed.");
        resetTransferControls();
        JOptionPane.showMessageDialog(this, reason != null ? reason : "Transfer failed due to an error.", "Transfer Failed", JOptionPane.ERROR_MESSAGE);
        requestTransferHistory();
    }

    private void cleanupIncomingStream() {
        try {
            if (incomingOutputStream != null) {
                incomingOutputStream.close();
                incomingOutputStream = null;
            }
        } catch (IOException ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private void handleHistoryResponse(NetworkMessage message) {
        if (message.getData() instanceof List) {
            List<TransferHistoryBean> list = (List<TransferHistoryBean>) message.getData();
            historyTableModel.setRowCount(0);
            for (TransferHistoryBean h : list) {
                historyTableModel.addRow(new Object[]{
                        h.getHistoryId(),
                        h.getTransferUuid(),
                        h.getSenderUsername(),
                        h.getReceiverUsername(),
                        h.getFileName(),
                        FormatUtils.formatFileSize(h.getFileSize()),
                        h.getStatus(),
                        FormatUtils.formatTimestamp(h.getTimestamp())
                });
            }
        }
    }

    // --- SwingWorker for Asynchronous File Transmission ---

    /**
     * SwingWorker implementation streaming file slices in a dedicated worker thread,
     * publishing progress updates to the Event Dispatch Thread (EDT) safely.
     */
    private class FileSenderWorker extends SwingWorker<Void, Long> {

        private final File file;
        private final FileTransferBean transfer;
        private long bytesSent = 0;
        private long sendStartTime = 0;

        public FileSenderWorker(File file, FileTransferBean transfer) {
            this.file = file;
            this.transfer = transfer;
        }

        @Override
        protected Void doInBackground() throws Exception {
            byte[] buffer = new byte[Constants.BUFFER_SIZE];
            long totalSize = file.length();
            long offset = 0;
            sendStartTime = System.currentTimeMillis();

            try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
                int bytesRead;
                while ((bytesRead = bis.read(buffer)) != -1) {
                    if (isCancelled()) {
                        // User cancelled
                        socketManager.sendMessage(NetworkMessage.request(MessageType.TRANSFER_CANCEL, transfer));
                        return null;
                    }

                    byte[] chunkData = new byte[bytesRead];
                    System.arraycopy(buffer, 0, chunkData, 0, bytesRead);

                    boolean isLast = (offset + bytesRead >= totalSize);
                    FileChunk chunk = new FileChunk(transfer.getTransferUuid(), chunkData, bytesRead, offset, isLast);

                    socketManager.sendMessage(NetworkMessage.request(MessageType.TRANSFER_CHUNK, chunk));

                    offset += bytesRead;
                    bytesSent = offset;
                    publish(bytesSent);
                }

                if (!isCancelled()) {
                    transfer.setTransferredBytes(totalSize);
                    socketManager.sendMessage(NetworkMessage.request(MessageType.TRANSFER_COMPLETE, transfer));
                }
            }
            return null;
        }

        @Override
        protected void process(List<Long> chunks) {
            if (chunks.isEmpty() || isCancelled()) return;
            long latestBytes = chunks.get(chunks.size() - 1);
            long total = file.length();
            int percent = total > 0 ? (int) Math.min(100, (latestBytes * 100) / total) : 0;

            long elapsedMs = Math.max(1, System.currentTimeMillis() - sendStartTime);
            double speedMBps = (latestBytes / (1024.0 * 1024.0)) / (elapsedMs / 1000.0);
            long remainingBytes = Math.max(0, total - latestBytes);
            long etaSec = (long) (remainingBytes / Math.max(1.0, speedMBps * 1024.0 * 1024.0));
            String speedStr = String.format("%.1f MB/s", speedMBps);
            String etaStr = etaSec < 60 ? (etaSec + "s") : ((etaSec / 60) + "m " + (etaSec % 60) + "s");

            transferProgressBar.setValue(percent);
            progressDetailsLabel.setText(String.format("%s / %s (%d%%) • %s • ETA: %s",
                    FormatUtils.formatFileSize(latestBytes),
                    FormatUtils.formatFileSize(total),
                    percent, speedStr, etaStr));
        }

        @Override
        protected void done() {
            if (isCancelled()) {
                transferStatusLabel.setText("Status: Cancelled");
                transferStatusLabel.setForeground(Color.GRAY);
                progressDetailsLabel.setText("Transfer cancelled.");
            } else {
                try {
                    get(); // Check for exceptions during streaming
                    transferStatusLabel.setText("Status: Completed");
                    transferStatusLabel.setForeground(new Color(46, 125, 50));
                    transferProgressBar.setValue(100);
                    progressDetailsLabel.setText("File sent successfully: " + file.getName());
                    JOptionPane.showMessageDialog(DashboardFrame.this,
                            "File '" + file.getName() + "' successfully delivered to " + transfer.getReceiverUsername() + "!",
                            "Transfer Succeeded",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    transferStatusLabel.setText("Status: Failed");
                    transferStatusLabel.setForeground(new Color(198, 40, 40));
                    progressDetailsLabel.setText("Stream failure: " + ex.getMessage());
                }
            }
            resetTransferControls();
            requestTransferHistory();
        }
    }

    // --- Custom UI Renderers ---

    /**
     * Renders online user entries with a green status indicator icon.
     */
    private static class OnlineUserListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            label.setText("🟢 " + value.toString());
            label.setBorder(new EmptyBorder(4, 6, 4, 6));
            return label;
        }
    }

    /**
     * Renders status badges in history table with appropriate indicator colors.
     */
    private static class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (value != null) {
                String status = value.toString();
                setHorizontalAlignment(SwingConstants.CENTER);
                setFont(getFont().deriveFont(Font.BOLD));
                if (FileTransferBean.STATUS_COMPLETED.equalsIgnoreCase(status)) {
                    setForeground(new Color(46, 125, 50)); // Green
                } else if (FileTransferBean.STATUS_FAILED.equalsIgnoreCase(status)) {
                    setForeground(new Color(198, 40, 40)); // Red
                } else if (FileTransferBean.STATUS_IN_PROGRESS.equalsIgnoreCase(status) || FileTransferBean.STATUS_PENDING.equalsIgnoreCase(status)) {
                    setForeground(new Color(230, 124, 0)); // Orange
                } else {
                    setForeground(Color.GRAY); // Cancelled
                }
            }
            return c;
        }
    }
}
