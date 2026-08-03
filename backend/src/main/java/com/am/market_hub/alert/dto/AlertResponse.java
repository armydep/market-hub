package com.am.market_hub.alert.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.am.market_hub.alert.domain.PriceAlert;

public record AlertResponse(
        Long id,
        String symbol,
        String condition,
        BigDecimal targetPrice,
        boolean active,
        Instant triggeredAt,
        BigDecimal triggeredPrice,
        Instant clearedAt,
        Instant createdAt) {

    public static AlertResponse from(PriceAlert alert) {
        return new AlertResponse(
                alert.getId(),
                alert.getSymbol(),
                alert.getCondition().name(),
                alert.getTargetPrice(),
                alert.isActive(),
                alert.getTriggeredAt(),
                alert.getTriggeredPrice(),
                alert.getClearedAt(),
                alert.getCreatedAt());
    }
}
