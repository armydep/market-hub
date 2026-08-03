package com.am.market_hub.alert.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class PriceAlertTest {

    @Test
    void createStartsActiveWithNoTriggerOrClearState() {
        PriceAlert alert = PriceAlert.create(1L, "BTC", AlertCondition.ABOVE_OR_EQUAL, new BigDecimal("100"));

        assertThat(alert.isActive()).isTrue();
        assertThat(alert.getTriggeredAt()).isNull();
        assertThat(alert.getTriggeredPrice()).isNull();
        assertThat(alert.getClearedAt()).isNull();
    }

    @Test
    void triggerSetsTriggeredFieldsAndClearsActive() {
        PriceAlert alert = PriceAlert.create(1L, "BTC", AlertCondition.ABOVE_OR_EQUAL, new BigDecimal("100"));

        alert.trigger(new BigDecimal("105"));

        assertThat(alert.isActive()).isFalse();
        assertThat(alert.getTriggeredAt()).isNotNull();
        assertThat(alert.getTriggeredPrice()).isEqualByComparingTo("105");
    }

    @Test
    void clearSetsClearedAt() {
        PriceAlert alert = PriceAlert.create(1L, "BTC", AlertCondition.ABOVE_OR_EQUAL, new BigDecimal("100"));
        alert.trigger(new BigDecimal("105"));

        alert.clear();

        assertThat(alert.getClearedAt()).isNotNull();
    }

    @Test
    void updateConditionAndTargetLeavesSymbolAndActiveUntouched() {
        PriceAlert alert = PriceAlert.create(1L, "BTC", AlertCondition.ABOVE_OR_EQUAL, new BigDecimal("100"));

        alert.updateConditionAndTarget(AlertCondition.BELOW_OR_EQUAL, new BigDecimal("50"));

        assertThat(alert.getSymbol()).isEqualTo("BTC");
        assertThat(alert.isActive()).isTrue();
        assertThat(alert.getCondition()).isEqualTo(AlertCondition.BELOW_OR_EQUAL);
        assertThat(alert.getTargetPrice()).isEqualByComparingTo("50");
    }
}
