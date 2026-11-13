package com.fileshare.client;

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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

/**
 * Registration Window allowing new users to create an account.
 */
public class RegisterFrame extends JFrame implements NetworkEventListener {

    private final LoginFrame parentLoginFrame;
    private final String host;
    private final int port;

    private JTextField fullNameField;
    private JTextField emailField;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JPasswordField confirmPasswordField;
    private JButton registerButton;
    private JButton backButton;
    private JLabel statusLabel;

    private final ClientSocketManager socketManager = ClientSocketManager.getInstance();

    public RegisterFrame(LoginFrame parentLoginFrame, String host, int port) {
        this.parentLoginFrame = parentLoginFrame;
        this.host = host;
        this.port = port;

        setTitle(Constants.APP_NAME + " - User Registration");
        setSize(440, 520);
        setResizable(false);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

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
        rootPanel.setBorder(new EmptyBorder(20, 30, 20, 30));

        // Header Title
        JPanel titlePanel = new JPanel(new GridLayout(2, 1, 3, 3));
        JLabel titleLabel = new JLabel("Create an Account", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(new Color(25, 118, 210));
        JLabel subtitleLabel = new JLabel("Join the LAN file sharing network", SwingConstants.CENTER);
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitleLabel.setForeground(Color.GRAY);
        titlePanel.add(titleLabel);
        titlePanel.add(subtitleLabel);
        rootPanel.add(titlePanel, BorderLayout.NORTH);

        // Form Fields
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 4, 6, 4);

        // Full Name
        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(new JLabel("Full Name:"), gbc);
        gbc.gridx = 1;
        fullNameField = new JTextField(15);
        formPanel.add(fullNameField, gbc);

        // Email
        gbc.gridx = 0;
        gbc.gridy = 1;
        formPanel.add(new JLabel("Email Address:"), gbc);
        gbc.gridx = 1;
        emailField = new JTextField(15);
        formPanel.add(emailField, gbc);

        // Username
        gbc.gridx = 0;
        gbc.gridy = 2;
        formPanel.add(new JLabel("Username:"), gbc);
        gbc.gridx = 1;
        usernameField = new JTextField(15);
        formPanel.add(usernameField, gbc);

        // Password
        gbc.gridx = 0;
        gbc.gridy = 3;
        formPanel.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1;
        passwordField = new JPasswordField(15);
        formPanel.add(passwordField, gbc);

        // Confirm Password
        gbc.gridx = 0;
        gbc.gridy = 4;
        formPanel.add(new JLabel("Confirm Password:"), gbc);
        gbc.gridx = 1;
        confirmPasswordField = new JPasswordField(15);
        formPanel.add(confirmPasswordField, gbc);

        rootPanel.add(formPanel, BorderLayout.CENTER);

        // Bottom Actions & Status
        JPanel bottomPanel = new JPanel(new BorderLayout(8, 8));

        statusLabel = new JLabel(" ", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(211, 47, 47));
        bottomPanel.add(statusLabel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 10, 10));
        registerButton = UIUtils.createPrimaryButton("Register Account", UIUtils.SUCCESS_GREEN, Color.WHITE);
        backButton = UIUtils.createSecondaryButton("Back to Login");

        buttonPanel.add(registerButton);
        buttonPanel.add(backButton);
        bottomPanel.add(buttonPanel, BorderLayout.CENTER);

        rootPanel.add(bottomPanel, BorderLayout.SOUTH);
        setContentPane(rootPanel);
    }

    private void initEvents() {
        // Register Action
        registerButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performRegistration();
            }
        });

        // Enter key to trigger registration
        java.awt.event.KeyAdapter enterAdapter = new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) {
                    performRegistration();
                }
            }
        };
        usernameField.addKeyListener(enterAdapter);
        passwordField.addKeyListener(enterAdapter);
        confirmPasswordField.addKeyListener(enterAdapter);

        // Back Action
        backButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                returnToLogin();
            }
        });

        // Window Closing
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                returnToLogin();
            }
        });
    }

    private void performRegistration() {
        String fullName = fullNameField.getText().trim();
        String email = emailField.getText().trim();
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        String confirmPassword = new String(confirmPasswordField.getPassword());

        // Field Validations
        if (fullName.isEmpty()) {
            setStatus("Please enter your full name.", true);
            fullNameField.requestFocus();
            return;
        }

        if (email.isEmpty() || !email.contains("@") || !email.contains(".")) {
            setStatus("Please enter a valid email address.", true);
            emailField.requestFocus();
            return;
        }

        if (username.isEmpty()) {
            setStatus("Please choose a username.", true);
            usernameField.requestFocus();
            return;
        }

        if (username.length() < 3 || username.contains(" ")) {
            setStatus("Username must be at least 3 chars without spaces.", true);
            usernameField.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            setStatus("Please enter a password.", true);
            passwordField.requestFocus();
            return;
        }

        if (password.length() < 6) {
            setStatus("Password must be at least 6 characters long.", true);
            passwordField.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            setStatus("Passwords do not match.", true);
            confirmPasswordField.requestFocus();
            return;
        }

        setStatus("Submitting registration...", false);
        registerButton.setEnabled(false);
        backButton.setEnabled(false);

        new Thread(() -> {
            try {
                if (!socketManager.isConnected()) {
                    socketManager.connect(host, port);
                }

                UserBean newUser = new UserBean(username, password, fullName, email);
                NetworkMessage regMsg = NetworkMessage.request(MessageType.REGISTER_REQUEST, newUser);
                socketManager.sendMessage(regMsg);

            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> {
                    setStatus("Connection error: " + ex.getMessage(), true);
                    registerButton.setEnabled(true);
                    backButton.setEnabled(true);
                });
            }
        }, "Register-WorkerThread").start();
    }

    private void returnToLogin() {
        socketManager.removeListener(this);
        if (parentLoginFrame != null) {
            parentLoginFrame.setVisible(true);
        }
        dispose();
    }

    private void setStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setForeground(isError ? new Color(211, 47, 47) : new Color(46, 125, 50));
    }

    // --- NetworkEventListener Callbacks ---

    @Override
    public void onConnected() {
    }

    @Override
    public void onDisconnected(String reason) {
        SwingUtilities.invokeLater(() -> {
            setStatus("Disconnected: " + reason, true);
            registerButton.setEnabled(true);
            backButton.setEnabled(true);
        });
    }

    @Override
    public void onMessageReceived(NetworkMessage message) {
        if (message.getType() == MessageType.REGISTER_RESPONSE) {
            SwingUtilities.invokeLater(() -> {
                registerButton.setEnabled(true);
                backButton.setEnabled(true);

                if (message.isSuccess()) {
                    JOptionPane.showMessageDialog(
                            RegisterFrame.this,
                            message.getMessage(),
                            "Registration Successful",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                    returnToLogin();
                } else {
                    setStatus(message.getMessage(), true);
                }
            });
        }
    }
}
