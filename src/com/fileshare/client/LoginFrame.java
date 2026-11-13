package com.fileshare.client;

import com.fileshare.beans.LoginBean;
import com.fileshare.beans.UserBean;
import com.fileshare.network.MessageType;
import com.fileshare.network.NetworkMessage;
import com.fileshare.utils.Constants;
import com.fileshare.utils.UIUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

/**
 * Swing Login Window for the LAN File Sharing Client.
 */
public class LoginFrame extends JFrame implements NetworkEventListener {

    private JTextField hostField;
    private JTextField portField;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JButton registerButton;
    private JLabel statusLabel;

    private final ClientSocketManager socketManager = ClientSocketManager.getInstance();

    public LoginFrame() {
        setTitle(Constants.APP_NAME + " - Login");
        setSize(420, 460);
        setResizable(false);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        initUI();
        initEvents();

        socketManager.addListener(this);
    }

    private void initUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        JPanel rootPanel = new JPanel(new BorderLayout(15, 15));
        rootPanel.setBorder(new EmptyBorder(25, 30, 25, 30));

        // Header Title
        JPanel titlePanel = new JPanel(new GridLayout(2, 1, 3, 3));
        JLabel titleLabel = new JLabel("LAN File Sharing", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        titleLabel.setForeground(new Color(25, 118, 210));
        JLabel subtitleLabel = new JLabel("Sign in to connect and transfer files", SwingConstants.CENTER);
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitleLabel.setForeground(Color.GRAY);
        titlePanel.add(titleLabel);
        titlePanel.add(subtitleLabel);
        rootPanel.add(titlePanel, BorderLayout.NORTH);

        // Form Panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 4, 6, 4);

        // Server Connection Sub-panel
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        formPanel.add(new JLabel("Server Host:"), gbc);

        gbc.gridx = 1;
        hostField = new JTextField(Constants.DEFAULT_HOST, 14);
        formPanel.add(hostField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        formPanel.add(new JLabel("Port:"), gbc);

        gbc.gridx = 1;
        portField = new JTextField(String.valueOf(Constants.DEFAULT_PORT), 14);
        formPanel.add(portField, gbc);

        // Hint for other laptops
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        JLabel hostHint = new JLabel("<html><i>💡 For other laptops: enter Host PC's Wi-Fi IP (e.g. 10.6.x.x)</i></html>");
        hostHint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        hostHint.setForeground(new Color(100, 116, 139));
        formPanel.add(hostHint, gbc);

        // Separator
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        JSeparator sep = new JSeparator();
        sep.setBorder(new EmptyBorder(6, 0, 6, 0));
        formPanel.add(sep, gbc);

        // Credentials
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 1;
        formPanel.add(new JLabel("Username:"), gbc);

        gbc.gridx = 1;
        usernameField = new JTextField(14);
        formPanel.add(usernameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 5;
        formPanel.add(new JLabel("Password:"), gbc);

        gbc.gridx = 1;
        passwordField = new JPasswordField(14);
        formPanel.add(passwordField, gbc);

        rootPanel.add(formPanel, BorderLayout.CENTER);

        // Bottom Actions & Status
        JPanel bottomPanel = new JPanel(new BorderLayout(8, 8));

        statusLabel = new JLabel(" ", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(211, 47, 47));
        bottomPanel.add(statusLabel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 10, 10));
        loginButton = UIUtils.createPrimaryButton("Login", UIUtils.PRIMARY_BLUE, Color.WHITE);
        registerButton = UIUtils.createSecondaryButton("Create New Account");

        buttonPanel.add(loginButton);
        buttonPanel.add(registerButton);
        bottomPanel.add(buttonPanel, BorderLayout.CENTER);

        rootPanel.add(bottomPanel, BorderLayout.SOUTH);
        setContentPane(rootPanel);
    }

    private void initEvents() {
        // AWT ActionEvent on Login Button
        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performLogin();
            }
        });

        // Enter key to login
        KeyAdapter enterKeyAdapter = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    performLogin();
                }
            }
        };
        usernameField.addKeyListener(enterKeyAdapter);
        passwordField.addKeyListener(enterKeyAdapter);

        // AWT ActionEvent on Register Button
        registerButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openRegisterFrame();
            }
        });

        // Window Closing
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                socketManager.removeListener(LoginFrame.this);
            }
        });
    }

    private void performLogin() {
        String host = hostField.getText().trim();
        String portStr = portField.getText().trim();
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword()).trim();

        // Validation
        if (host.isEmpty()) {
            setStatus("Please specify server host.", true);
            hostField.requestFocus();
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1024 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            setStatus("Invalid port (1024 - 65535).", true);
            portField.requestFocus();
            return;
        }

        if (username.isEmpty()) {
            setStatus("Username cannot be empty.", true);
            usernameField.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            setStatus("Password cannot be empty.", true);
            passwordField.requestFocus();
            return;
        }

        setStatus("Connecting to server...", false);
        loginButton.setEnabled(false);
        registerButton.setEnabled(false);

        // Perform connection in background thread so GUI doesn't freeze
        new Thread(() -> {
            try {
                if (!socketManager.isConnected()) {
                    socketManager.connect(host, port);
                }

                LoginBean loginBean = new LoginBean(username, password);
                NetworkMessage loginMsg = NetworkMessage.request(MessageType.LOGIN_REQUEST, loginBean);
                socketManager.sendMessage(loginMsg);

            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> {
                    setStatus("Connection error: " + ex.getMessage(), true);
                    loginButton.setEnabled(true);
                    registerButton.setEnabled(true);
                });
            }
        }, "Login-WorkerThread").start();
    }

    private void openRegisterFrame() {
        String host = hostField.getText().trim();
        String portStr = portField.getText().trim();
        int port = Constants.DEFAULT_PORT;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException ignored) {
        }

        RegisterFrame regFrame = new RegisterFrame(this, host, port);
        regFrame.setVisible(true);
        this.setVisible(false);
    }

    private void setStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setForeground(isError ? new Color(211, 47, 47) : new Color(46, 125, 50));
    }

    // --- NetworkEventListener Callbacks ---

    @Override
    public void onConnected() {
        SwingUtilities.invokeLater(() -> setStatus("Connected to server. Verifying credentials...", false));
    }

    @Override
    public void onDisconnected(String reason) {
        SwingUtilities.invokeLater(() -> {
            setStatus("Disconnected: " + reason, true);
            loginButton.setEnabled(true);
            registerButton.setEnabled(true);
        });
    }

    @Override
    public void onMessageReceived(NetworkMessage message) {
        if (message.getType() == MessageType.LOGIN_RESPONSE) {
            SwingUtilities.invokeLater(() -> {
                loginButton.setEnabled(true);
                registerButton.setEnabled(true);

                if (message.isSuccess() && message.getData() instanceof UserBean) {
                    UserBean user = (UserBean) message.getData();
                    socketManager.setCurrentUser(user);
                    socketManager.removeListener(this);

                    // Open Dashboard
                    DashboardFrame dashboard = new DashboardFrame();
                    dashboard.setVisible(true);
                    dispose();
                } else {
                    setStatus(message.getMessage(), true);
                }
            });
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LoginFrame frame = new LoginFrame();
            frame.setVisible(true);
        });
    }
}
