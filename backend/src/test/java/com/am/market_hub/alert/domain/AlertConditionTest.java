package com.am.market_hub.alert.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class AlertConditionTest {

    @Test
    void aboveOrEqualIsSatisfiedAtAndAboveTheTarget() {
        assertThat(AlertCondition.ABOVE_OR_EQUAL.isSatisfiedBy(new BigDecimal("100"), new BigDecimal("100"))).isTrue();
        assertThat(AlertCondition.ABOVE_OR_EQUAL.isSatisfiedBy(new BigDecimal("101"), new BigDecimal("100"))).isTrue();
        assertThat(AlertCondition.ABOVE_OR_EQUAL.isSatisfiedBy(new BigDecimal("99"), new BigDecimal("100"))).isFalse();
    }

    @Test
    void belowOrEqualIsSatisfiedAtAndBelowTheTarget() {
        assertThat(AlertCondition.BELOW_OR_EQUAL.isSatisfiedBy(new BigDecimal("100"), new BigDecimal("100"))).isTrue();
        assertThat(AlertCondition.BELOW_OR_EQUAL.isSatisfiedBy(new BigDecimal("99"), new BigDecimal("100"))).isTrue();
        assertThat(AlertCondition.BELOW_OR_EQUAL.isSatisfiedBy(new BigDecimal("101"), new BigDecimal("100"))).isFalse();
    }
}
