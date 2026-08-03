package com.am.market_hub.alert.dto;

import java.math.BigDecimal;

import com.am.market_hub.alert.domain.AlertCondition;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * No {@code symbol} field: like {@code RegisterRequest}'s dropped {@code role},
 * a client sending one is silently ignored rather than needing a defensive
 * check — symbol is immutable after creation (spec's resolved open question 1).
 */
public record UpdateAlertRequest(
        @NotNull AlertCondition condition,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal targetPrice) {
}
