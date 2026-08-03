package com.am.market_hub.user.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The one persisted, per-user view setting in Phase 1: an ordered visible-
 * column selection (F001-FR-007, F009-FR-004). 1:1 with {@link User} — the id
 * is both the PK and the FK, cascading on delete since this is live per-user
 * state, not an audit trail (unlike {@code AdminAuditLog}).
 *
 * <p>{@code visibleColumnsJson} stores a JSON array of {@code CoinColumn} keys;
 * the (de)serialization and catalog validation live in {@code AccountService},
 * not here — this entity only owns the persisted shape.
 */
@Entity
@Table(name = "user_preferences")
public class UserPreference {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "visible_columns_json")
    private String visibleColumnsJson;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected UserPreference() {
    }

    public static UserPreference of(Long userId, String visibleColumnsJson) {
        UserPreference preference = new UserPreference();
        preference.userId = userId;
        preference.visibleColumnsJson = visibleColumnsJson;
        preference.updatedAt = Instant.now();
        return preference;
    }

    public Long getUserId() {
        return userId;
    }

    public String getVisibleColumnsJson() {
        return visibleColumnsJson;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateVisibleColumns(String visibleColumnsJson) {
        this.visibleColumnsJson = visibleColumnsJson;
        this.updatedAt = Instant.now();
    }
}
