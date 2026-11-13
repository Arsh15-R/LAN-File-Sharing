package com.fileshare.beans;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Objects;

/**
 * JavaBean representing a User in the File Sharing application.
 * Encapsulates user identification, profile data, and online status.
 */
public class UserBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private String username;
    private String password; // Raw password (for registration/login) or hashed
    private String fullName;
    private String email;
    private String status; // "ONLINE", "OFFLINE"
    private Timestamp createdAt;

    /**
     * Default no-argument constructor (required for JavaBeans).
     */
    public UserBean() {
        this.status = "OFFLINE";
    }

    /**
     * Parameterized constructor for credentials verification.
     */
    public UserBean(String username, String password) {
        this();
        this.username = username;
        this.password = password;
    }

    /**
     * Parameterized constructor for registration.
     */
    public UserBean(String username, String password, String fullName, String email) {
        this();
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.email = email;
    }

    /**
     * Parameterized constructor for loading existing user from database.
     */
    public UserBean(int id, String username, String fullName, String email, String status, Timestamp createdAt) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.status = status;
        this.createdAt = createdAt;
    }

    /**
     * Full parameterized constructor.
     */
    public UserBean(int id, String username, String password, String fullName, String email, String status, Timestamp createdAt) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.email = email;
        this.status = status;
        this.createdAt = createdAt;
    }

    // --- Getters and Setters ---

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

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

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserBean)) return false;
        UserBean userBean = (UserBean) o;
        return Objects.equals(username, userBean.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username);
    }

    @Override
    public String toString() {
        return username + (fullName != null && !fullName.isEmpty() ? " (" + fullName + ")" : "");
    }
}
