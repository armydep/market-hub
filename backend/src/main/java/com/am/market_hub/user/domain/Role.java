package com.am.market_hub.user.domain;

/**
 * Single-role RBAC (constraints.md): a Spring {@code RoleHierarchy} grants
 * downward (ADMIN &gt; MODERATOR &gt; TRADER), so an admin implicitly holds
 * trader authorities without a second grant. MODERATOR is reserved — no
 * Phase 1 workflow grants or requires it.
 */
public enum Role {
    TRADER,
    MODERATOR,
    ADMIN
}
