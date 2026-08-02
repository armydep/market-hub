package com.am.market_hub.user.domain;

import java.time.Duration;
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
 * <p>{@code block()}/{@code unblock()} exist now for S11 (admin user
 * management) to call, though no admin endpoint invokes them yet — S6's own
 * tests use them to simulate an administrative block directly, since that's
 * explicitly out of scope here. {@code failedLoginAttempts} and
 * {@code lockedUntil} are owned end-to-end by {@link #registerFailedLogin}
 * and {@link #registerSuccessfulLogin} below, and are deliberately
 * independent of {@code blocked} — see domain-model.md.
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

    /**
     * Records a wrong-password attempt. Locks the account once the threshold
     * is reached; below it, only the counter moves.
     */
    public void registerFailedLogin(int maxFailedAttempts, Duration lockoutDuration) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxFailedAttempts) {
            lockedUntil = Instant.now().plus(lockoutDuration);
        }
    }

    /**
     * Clears both failed-attempt state fields. Called both on an actual
     * successful login and, per the S6 spec's resolved "lazy expiry"
     * decision, when a temporary lock is found to have already elapsed —
     * the next attempt after that is evaluated as a fresh cycle either way.
     */
    public void registerSuccessfulLogin() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    public boolean isLockActive() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    public boolean hasExpiredLock() {
        return lockedUntil != null && !isLockActive();
    }

    public void block() {
        blocked = true;
    }

    public void unblock() {
        blocked = false;
    }
}
