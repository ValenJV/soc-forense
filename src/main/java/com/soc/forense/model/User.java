package com.soc.forense.model;

import java.time.LocalDateTime;

/**
 * Usuario del sistema. El rol define lo que puede hacer:
 * ADMIN gestiona usuarios y evidencias; ANALYST sube y analiza;
 * VIEWER solo consulta resultados.
 */
public class User {

    private long id;
    private String username;
    private String email;
    private String fullName;
    private String passwordHash;
    private String role;       // ADMIN, ANALYST, VIEWER
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;

    public User() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }

    public boolean canAnalyze() {
        return "ADMIN".equalsIgnoreCase(role) || "ANALYST".equalsIgnoreCase(role);
    }
}
