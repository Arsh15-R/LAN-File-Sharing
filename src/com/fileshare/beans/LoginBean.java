package com.fileshare.beans;

import java.io.Serializable;

/**
 * JavaBean encapsulating login credentials sent from client to server.
 */
public class LoginBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private String username;
    private String password;

    /**
     * Default no-argument constructor (required for JavaBeans).
     */
    public LoginBean() {
    }

    /**
     * Parameterized constructor.
     *
     * @param username user identifier
     * @param password raw user password
     */
    public LoginBean(String username, String password) {
        this.username = username;
        this.password = password;
    }

    // --- Getters and Setters ---

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return "LoginBean{username='" + username + "'}";
    }
}
