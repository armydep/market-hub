package com.am.market_hub.support;

import java.math.BigDecimal;
import java.util.List;

import com.am.market_hub.market.provider.PriceProvider;
import com.am.market_hub.market.provider.ProviderQuote;

/** Deterministic in-memory provider so tests never call CoinMarketCap. */
public class StubPriceProvider implements PriceProvider {

    private volatile List<ProviderQuote> quotes = List.of();

    public void setQuotes(List<ProviderQuote> quotes) {
        this.quotes = quotes;
    }

    @Override
    public List<ProviderQuote> fetchTopCoins(int limit, String convert) {
        return quotes;
    }

    @Override
    public String name() {
        return "stub";
    }

    /** Convenience builder for a crypto quote with the fields tests care about. */
    public static ProviderQuote quote(int cmcId, String symbol, int rank, String price) {
        return new ProviderQuote(cmcId, symbol, symbol + "-name", symbol.toLowerCase(), "CRYPTO",
                rank, new BigDecimal(price), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, "USD");
    }
}
