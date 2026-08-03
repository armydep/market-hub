package com.am.market_hub.admin.domain;

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
 * A single administrative action, written in the same transaction as the
 * state change it records (PRD §3.7) — see {@code AdminUserService}. Not
 * exposed through any user-facing endpoint.
 */
@Entity
@Table(name = "admin_audit_log")
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    private AdminAction action;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(name = "created_at")
    private Instant createdAt;

    protected AdminAuditLog() {
    }

    public static AdminAuditLog of(Long actorUserId, AdminAction action, Long targetUserId) {
        AdminAuditLog log = new AdminAuditLog();
        log.actorUserId = actorUserId;
        log.action = action;
        log.targetUserId = targetUserId;
        log.createdAt = Instant.now();
        return log;
    }

    public Long getId() {
        return id;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public AdminAction getAction() {
        return action;
    }

    public Long getTargetUserId() {
        return targetUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
