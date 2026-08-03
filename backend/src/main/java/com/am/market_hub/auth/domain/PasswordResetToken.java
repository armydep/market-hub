package com.am.market_hub.auth.domain;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A single-use, time-limited password-reset token (F004-FR-004). Only the
 * hash is ever persisted — the raw value goes to {@code EmailSender} and is
 * never stored (constraints.md's security requirement for reset tokens).
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "token_hash")
    private String tokenHash;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    protected PasswordResetToken() {
    }

    public static PasswordResetToken issue(Long userId, String tokenHash, Duration lifetime) {
        PasswordResetToken token = new PasswordResetToken();
        token.userId = userId;
        token.tokenHash = tokenHash;
        token.expiresAt = Instant.now().plus(lifetime);
        token.createdAt = Instant.now();
        return token;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isValid() {
        return usedAt == null && expiresAt.isAfter(Instant.now());
    }

    /**
     * Marks the token unusable. Used both when it's actually consumed on
     * confirm, and when a new reset request supersedes it — both mean the
     * same thing operationally, so there's one method, not two.
     */
    public void markUsed() {
        usedAt = Instant.now();
    }
}
