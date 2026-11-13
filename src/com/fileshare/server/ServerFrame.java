package com.fileshare.server;

import com.fileshare.database.DatabaseConnection;
import com.fileshare.utils.Constants;
import com.fileshare.utils.UIUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.List;

/**
 * Graphical Management Console for the FileShare Server.
 */
public class ServerFrame extends JFrame implements FileServer.ServerListener {

    private JTextField portField;
    private JButton startButton;
    private JButton stopButton;
    private JLabel statusLabel;
    private DefaultListModel<String> clientsListModel;
    private JList<String> clientsList;
    private JLabel clientsCountLabel;
    private JTextArea logTextArea;

    private FileServer server;

    public ServerFrame() {
        setTitle(Constants.APP_NAME + " - Server Control Panel");
        setSize(850, 550);
        setMinimumSize(new Dimension(700, 450));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        initUI();
        initEvents();
    }

    private void initUI() {
        // Try System Look and Feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(12, 12, 12, 12));

        // --- Top Control Panel ---
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(),
                new EmptyBorder(10, 15, 10, 15)
        ));

        JPanel configPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        configPanel.add(new JLabel("Port:"));
        portField = new JTextField(String.valueOf(Constants.DEFAULT_PORT), 6);
        configPanel.add(portField);

        startButton = UIUtils.createPrimaryButton("Start Server", UIUtils.SUCCESS_GREEN, Color.WHITE);
        configPanel.add(startButton);

        stopButton = UIUtils.createPrimaryButton("Stop Server", UIUtils.DANGER_RED, Color.WHITE);
        stopButton.setEnabled(false);
        configPanel.add(stopButton);

        JPanel statusWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        statusLabel = new JLabel("Status: STOPPED");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 13f));
        statusLabel.setForeground(new Color(180, 0, 0));
        statusWrapper.add(statusLabel);

        JPanel controlsRow = new JPanel(new BorderLayout(10, 5));
        controlsRow.add(configPanel, BorderLayout.WEST);
        controlsRow.add(statusWrapper, BorderLayout.EAST);

        // --- LAN IP Banner for Wi-Fi Multi-Laptop Connection ---
        String lanIp = getLocalLanIp();
        JPanel ipBannerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        ipBannerPanel.setBackground(new Color(237, 247, 237));
        ipBannerPanel.setBorder(BorderFactory.createLineBorder(new Color(186, 222, 186), 1));

        JLabel ipIcon = new JLabel("🌐");
        JLabel ipLabel = new JLabel("Host Wi-Fi / LAN IP: " + lanIp + "  (Enter this IP on other laptops to connect)");
        ipLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        ipLabel.setForeground(new Color(30, 70, 32));

        JButton copyIpBtn = new JButton("📋 Copy IP");
        copyIpBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        copyIpBtn.setFocusPainted(false);
        copyIpBtn.addActionListener(e -> {
            java.awt.datatransfer.StringSelection sel = new java.awt.datatransfer.StringSelection(lanIp);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
            JOptionPane.showMessageDialog(this, "Copied IP (" + lanIp + ") to clipboard!\nShare this with other laptops on your Wi-Fi.", "IP Copied", JOptionPane.INFORMATION_MESSAGE);
        });

        ipBannerPanel.add(ipIcon);
        ipBannerPanel.add(ipLabel);
        ipBannerPanel.add(copyIpBtn);

        topPanel.add(controlsRow, BorderLayout.NORTH);
        topPanel.add(ipBannerPanel, BorderLayout.SOUTH);

        // --- Center Split Pane ---
        // Left: Connected Clients
        JPanel clientsPanel = new JPanel(new BorderLayout(5, 5));
        clientsPanel.setPreferredSize(new Dimension(220, 300));
        clientsPanel.setBorder(BorderFactory.createTitledBorder("Online Clients"));

        clientsCountLabel = new JLabel("Clients connected: 0", SwingConstants.CENTER);
        clientsCountLabel.setBorder(new EmptyBorder(4, 4, 4, 4));
        clientsPanel.add(clientsCountLabel, BorderLayout.NORTH);

        clientsListModel = new DefaultListModel<>();
        clientsList = new JList<>(clientsListModel);
        clientsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        clientsList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        JScrollPane clientsScroll = new JScrollPane(clientsList);
        clientsPanel.add(clientsScroll, BorderLayout.CENTER);

        // Right: Log Console
        JPanel logsPanel = new JPanel(new BorderLayout(5, 5));
        logsPanel.setBorder(BorderFactory.createTitledBorder("Server Activity Log"));

        logTextArea = new JTextArea();
        logTextArea.setEditable(false);
        logTextArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logTextArea.setBackground(new Color(248, 249, 250));
        JScrollPane logScroll = new JScrollPane(logTextArea);
        logsPanel.add(logScroll, BorderLayout.CENTER);

        JPanel logButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton clearLogBtn = UIUtils.createSecondaryButton("Clear Logs");
        clearLogBtn.addActionListener(e -> logTextArea.setText(""));
        logButtonPanel.add(clearLogBtn);
        logsPanel.add(logButtonPanel, BorderLayout.SOUTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, clientsPanel, logsPanel);
        splitPane.setDividerLocation(230);
        splitPane.setContinuousLayout(true);

        // --- Bottom Info Bar ---
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBorder(new EmptyBorder(5, 5, 0, 5));
        JLabel dbInfo = new JLabel("Database: " + DatabaseConnection.getDbUrl());
        dbInfo.setForeground(Color.DARK_GRAY);
        dbInfo.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        bottomBar.add(dbInfo, BorderLayout.WEST);

        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(splitPane, BorderLayout.CENTER);
        mainPanel.add(bottomBar, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private void initEvents() {
        // Start Server Action
        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startServerAction();
            }
        });

        // Stop Server Action
        stopButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                stopServerAction();
            }
        });

        // Window Closing Listener
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (server != null && server.isRunning()) {
                    int choice = JOptionPane.showConfirmDialog(
                            ServerFrame.this,
                            "Server is currently running. Stopping will disconnect all clients. Continue?",
                            "Exit FileShare Server",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE
                    );
                    if (choice == JOptionPane.YES_OPTION) {
                        server.stop();
                        dispose();
                        System.exit(0);
                    }
                } else {
                    dispose();
                    System.exit(0);
                }
            }
        });
    }

    private void startServerAction() {
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port < 1024 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Please enter a valid port number between 1024 and 65535.", "Invalid Port", JOptionPane.ERROR_MESSAGE);
            return;
        }

        server = new FileServer(port);
        server.addListener(this);

        try {
            server.start();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not start server: " + ex.getMessage(), "Start Failure", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void stopServerAction() {
        if (server != null && server.isRunning()) {
            server.stop();
        }
    }

    // --- FileServer.ServerListener Callbacks ---

    @Override
    public void onLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logTextArea.append(message + "\n");
            logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
        });
    }

    @Override
    public void onClientsChanged(List<String> onlineUsernames) {
        SwingUtilities.invokeLater(() -> {
            clientsListModel.clear();
            for (String u : onlineUsernames) {
                clientsListModel.addElement(u);
            }
            clientsCountLabel.setText("Clients connected: " + onlineUsernames.size());
        });
    }

    @Override
    public void onServerStateChanged(boolean isRunning) {
        SwingUtilities.invokeLater(() -> {
            startButton.setEnabled(!isRunning);
            stopButton.setEnabled(isRunning);
            portField.setEditable(!isRunning);

            if (isRunning) {
                statusLabel.setText("Status: RUNNING on Port " + portField.getText());
                statusLabel.setForeground(new Color(46, 125, 50));
            } else {
                statusLabel.setText("Status: STOPPED");
                statusLabel.setForeground(new Color(198, 40, 40));
            }
        });
    }

    /**
     * Resolves the primary non-loopback IPv4 address on the active Wi-Fi or LAN adapter.
     */
    public static String getLocalLanIp() {
        String bestIp = null;
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) continue;
                String name = (iface.getName() + " " + iface.getDisplayName()).toLowerCase();
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        if (ip.startsWith("169.254.")) continue; // Skip link-local APIPA
                        if (name.contains("wi-fi") || name.contains("wireless") || name.contains("wlan") || iface.getName().toLowerCase().startsWith("wl")) {
                            return ip; // Prioritize Wi-Fi adapter (Windows & Linux)
                        }
                        if (bestIp == null) {
                            bestIp = ip;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (bestIp != null) return bestIp;
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ServerFrame frame = new ServerFrame();
            frame.setVisible(true);
            if (args.length > 0 && "--autostart".equalsIgnoreCase(args[0])) {
                frame.startServerAction();
            }
        });
    }
}
