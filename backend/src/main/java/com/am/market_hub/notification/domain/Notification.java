package com.am.market_hub.notification.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.am.market_hub.alert.domain.AlertCondition;
import com.am.market_hub.alert.domain.PriceAlert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One in-app notification per alert trigger (PRD F-007). Deliberately
 * separate from {@link PriceAlert} so clearing a notification and clearing a
 * triggered alert are independent actions (domain-model.md). Every field
 * except {@code id}/{@code alertId}/{@code clearedAt}/{@code createdAt} is
 * denormalized from the alert at trigger time, so the notification stays
 * fully readable regardless of what later happens to the alert.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "alert_id")
    private Long alertId;

    private String symbol;

    @Enumerated(EnumType.STRING)
    private AlertCondition condition;

    @Column(name = "target_price")
    private BigDecimal targetPrice;

    @Column(name = "triggered_price")
    private BigDecimal triggeredPrice;

    @Column(name = "triggered_at")
    private Instant triggeredAt;

    @Column(name = "cleared_at")
    private Instant clearedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    protected Notification() {
    }

    /** {@code alert} must already be triggered — its triggeredAt/triggeredPrice are copied verbatim. */
    public static Notification from(PriceAlert alert) {
        Notification notification = new Notification();
        notification.userId = alert.getUserId();
        notification.alertId = alert.getId();
        notification.symbol = alert.getSymbol();
        notification.condition = alert.getCondition();
        notification.targetPrice = alert.getTargetPrice();
        notification.triggeredPrice = alert.getTriggeredPrice();
        notification.triggeredAt = alert.getTriggeredAt();
        notification.createdAt = Instant.now();
        return notification;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getAlertId() {
        return alertId;
    }

    public String getSymbol() {
        return symbol;
    }

    public AlertCondition getCondition() {
        return condition;
    }

    public BigDecimal getTargetPrice() {
        return targetPrice;
    }

    public BigDecimal getTriggeredPrice() {
        return triggeredPrice;
    }

    public Instant getTriggeredAt() {
        return triggeredAt;
    }

    public Instant getClearedAt() {
        return clearedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void clear() {
        this.clearedAt = Instant.now();
    }
}
