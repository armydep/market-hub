package com.am.market_hub.user.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

/**
 * The one persisted, per-user view setting in Phase 1: an ordered visible-
 * column selection (F001-FR-007, F009-FR-004). 1:1 with {@link User} — the id
 * is both the PK and the FK, cascading on delete since this is live per-user
 * state, not an audit trail (unlike {@code AdminAuditLog}).
 *
 * <p>{@code visibleColumnsJson} stores a JSON array of {@code CoinColumn} keys;
 * the (de)serialization and catalog validation live in {@code AccountService},
 * not here — this entity only owns the persisted shape.
 *
 * <p>Implements {@link Persistable} for the same reason {@code CryptoQuote}
 * does: {@code userId} is an assigned, not generated, id, so without it
 * {@code save()} on a genuinely new row would issue an avoidable
 * SELECT-then-insert. The transient {@code isNew} flag, set only in
 * {@link #of}, lets a first-time save go straight to insert.
 */
@Entity
@Table(name = "user_preferences")
public class UserPreference implements Persistable<Long> {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Transient
    private boolean isNew = false;

    @Column(name = "visible_columns_json")
    private String visibleColumnsJson;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected UserPreference() {
    }

    public static UserPreference of(Long userId, String visibleColumnsJson) {
        UserPreference preference = new UserPreference();
        preference.userId = userId;
        preference.isNew = true;
        preference.visibleColumnsJson = visibleColumnsJson;
        preference.updatedAt = Instant.now();
        return preference;
    }

    @Override
    public Long getId() {
        return userId;
    }

    @Override
    public boolean isNew() {
        return isNew;
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
