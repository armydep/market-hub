package com.am.market_hub.user.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A registered account. Guests are never persisted — no anonymous rows exist.
 *
 * <p>{@code blocked}/{@code failedLoginAttempts}/{@code lockedUntil} are
 * mapped now because the table needs its final shape, but nothing reads or
 * writes them until S6 (failed-attempt lockout and administrative blocking).
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Always stored lowercased by the service layer; uniqueness is case-insensitive. */
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private Role role;

    private boolean blocked;

    @Column(name = "failed_login_attempts")
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at")
    private Instant createdAt;

    protected User() {
    }

    public static User register(String email, String passwordHash) {
        User user = new User();
        user.email = email;
        user.passwordHash = passwordHash;
        user.role = Role.TRADER;
        user.blocked = false;
        user.failedLoginAttempts = 0;
        user.createdAt = Instant.now();
        return user;
    }

    /** Only the env-provisioned startup seed creates an ADMIN; see AdminSeeder. */
    public static User seedAdmin(String email, String passwordHash) {
        User user = new User();
        user.email = email;
        user.passwordHash = passwordHash;
        user.role = Role.ADMIN;
        user.blocked = false;
        user.failedLoginAttempts = 0;
        user.createdAt = Instant.now();
        return user;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
