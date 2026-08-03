package com.am.market_hub.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import com.am.market_hub.alert.domain.AlertCondition;
import com.am.market_hub.alert.domain.PriceAlert;
import org.junit.jupiter.api.Test;

class NotificationTest {

    @Test
    void fromCopiesTheAlertsTriggeredStateAtCreationTime() {
        PriceAlert alert = PriceAlert.create(1L, "BTC", AlertCondition.ABOVE_OR_EQUAL, new BigDecimal("100"));
        alert.trigger(new BigDecimal("105"));

        Notification notification = Notification.from(alert);

        assertThat(notification.getUserId()).isEqualTo(1L);
        assertThat(notification.getAlertId()).isEqualTo(alert.getId());
        assertThat(notification.getSymbol()).isEqualTo("BTC");
        assertThat(notification.getCondition()).isEqualTo(AlertCondition.ABOVE_OR_EQUAL);
        assertThat(notification.getTargetPrice()).isEqualByComparingTo("100");
        assertThat(notification.getTriggeredPrice()).isEqualByComparingTo("105");
        assertThat(notification.getTriggeredAt()).isEqualTo(alert.getTriggeredAt());
        assertThat(notification.getClearedAt()).isNull();
    }

    @Test
    void clearSetsClearedAt() {
        PriceAlert alert = PriceAlert.create(1L, "BTC", AlertCondition.ABOVE_OR_EQUAL, new BigDecimal("100"));
        alert.trigger(new BigDecimal("105"));
        Notification notification = Notification.from(alert);

        notification.clear();

        assertThat(notification.getClearedAt()).isNotNull();
    }
}
