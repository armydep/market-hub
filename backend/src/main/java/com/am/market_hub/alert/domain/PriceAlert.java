package com.am.market_hub.alert.domain;

import java.math.BigDecimal;
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
 * A one-time above/below price alert (PRD F-006). Lifecycle: active=true
 * &rarr; condition met at evaluation &rarr; {@link #trigger} sets
 * triggeredAt/triggeredPrice and active=false &rarr; owner calls
 * {@link #clear}. No re-arm: re-arming means creating a new alert.
 */
@Entity
@Table(name = "price_alerts")
public class PriceAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    private String symbol;

    @Enumerated(EnumType.STRING)
    private AlertCondition condition;

    @Column(name = "target_price")
    private BigDecimal targetPrice;

    private boolean active;

    @Column(name = "triggered_at")
    private Instant triggeredAt;

    @Column(name = "triggered_price")
    private BigDecimal triggeredPrice;

    @Column(name = "cleared_at")
    private Instant clearedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    protected PriceAlert() {
    }

    public static PriceAlert create(Long userId, String symbol, AlertCondition condition, BigDecimal targetPrice) {
        PriceAlert alert = new PriceAlert();
        alert.userId = userId;
        alert.symbol = symbol;
        alert.condition = condition;
        alert.targetPrice = targetPrice;
        alert.active = true;
        alert.createdAt = Instant.now();
        return alert;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
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

    public boolean isActive() {
        return active;
    }

    public Instant getTriggeredAt() {
        return triggeredAt;
    }

    public BigDecimal getTriggeredPrice() {
        return triggeredPrice;
    }

    public Instant getClearedAt() {
        return clearedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Active-alert-only field update; symbol is immutable after creation. */
    public void updateConditionAndTarget(AlertCondition condition, BigDecimal targetPrice) {
        this.condition = condition;
        this.targetPrice = targetPrice;
    }

    public void trigger(BigDecimal price) {
        this.triggeredAt = Instant.now();
        this.triggeredPrice = price;
        this.active = false;
    }

    public void clear() {
        this.clearedAt = Instant.now();
    }
}
