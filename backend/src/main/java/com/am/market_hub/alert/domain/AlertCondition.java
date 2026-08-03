package com.am.market_hub.alert.domain;

import java.math.BigDecimal;

/**
 * PRD naming (`ABOVE_OR_EQUAL`/`BELOW_OR_EQUAL`); the >=/<= semantics are the
 * one piece of domain logic shared by creation validation, update
 * validation, and evaluation, so it lives here rather than being
 * duplicated in three places.
 */
public enum AlertCondition {
    ABOVE_OR_EQUAL {
        @Override
        public boolean isSatisfiedBy(BigDecimal price, BigDecimal target) {
            return price.compareTo(target) >= 0;
        }
    },
    BELOW_OR_EQUAL {
        @Override
        public boolean isSatisfiedBy(BigDecimal price, BigDecimal target) {
            return price.compareTo(target) <= 0;
        }
    };

    public abstract boolean isSatisfiedBy(BigDecimal price, BigDecimal target);
}
