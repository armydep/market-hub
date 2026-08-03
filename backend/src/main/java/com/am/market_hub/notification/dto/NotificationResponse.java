package com.am.market_hub.notification.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.am.market_hub.notification.domain.Notification;

public record NotificationResponse(
        Long id,
        String symbol,
        String condition,
        BigDecimal targetPrice,
        BigDecimal triggeredPrice,
        Instant triggeredAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getSymbol(),
                notification.getCondition().name(),
                notification.getTargetPrice(),
                notification.getTriggeredPrice(),
                notification.getTriggeredAt());
    }
}
