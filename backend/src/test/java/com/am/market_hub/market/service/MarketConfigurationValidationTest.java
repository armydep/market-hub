package com.am.market_hub.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.am.market_hub.market.repository.CryptoQuoteRepository;

/**
 * Startup validation of the S2 presentation config. A bad column key or an
 * unusable page-size list must fail the boot loudly rather than surface later
 * as an unexplained empty grid.
 *
 * <p>Pure unit test: {@code MarketService}'s constructor does the validating, so
 * no Spring context or database is needed.
 */
class MarketConfigurationValidationTest {

    private static final List<String> VALID_COLUMNS = List.of("marketCapRank", "name", "price");
    private static final List<Integer> VALID_SIZES = List.of(20, 50, 100);

    private static MarketService build(int defaultPageSize, List<Integer> sizes, List<String> columns) {
        // Null repository is fine: nothing is dereferenced during construction.
        return new MarketService((CryptoQuoteRepository) null, defaultPageSize, sizes, columns);
    }

    @Test
    void acceptsValidConfiguration() {
        assertThat(build(20, VALID_SIZES, VALID_COLUMNS)).isNotNull();
    }

    @Test
    void rejectsUnknownColumnKey() {
        assertThatThrownBy(() -> build(20, VALID_SIZES, List.of("marketCapRank", "bogusColumn")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bogusColumn")
                .hasMessageContaining("MARKET_DEFAULT_VISIBLE_COLUMNS");
    }

    @Test
    void rejectsEmptyColumnList() {
        assertThatThrownBy(() -> build(20, VALID_SIZES, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be empty");
    }

    @Test
    void rejectsDuplicateColumnKeys() {
        assertThatThrownBy(() -> build(20, VALID_SIZES, List.of("price", "name", "price")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void rejectsEmptyPageSizeList() {
        assertThatThrownBy(() -> build(20, List.of(), VALID_COLUMNS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MARKET_SUPPORTED_PAGE_SIZES");
    }

    @Test
    void rejectsDefaultPageSizeOutsideSupportedList() {
        assertThatThrownBy(() -> build(25, VALID_SIZES, VALID_COLUMNS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MARKET_DEFAULT_PAGE_SIZE")
                .hasMessageContaining("25");
    }
}
