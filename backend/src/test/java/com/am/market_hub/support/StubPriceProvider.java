package com.am.market_hub.support;

import java.math.BigDecimal;
import java.util.List;

import com.am.market_hub.market.provider.PriceProvider;
import com.am.market_hub.market.provider.ProviderQuote;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Deterministic in-memory provider so tests never call CoinMarketCap. */
public class StubPriceProvider implements PriceProvider {

    private volatile List<ProviderQuote> quotes = List.of();
    private volatile Boolean transactionActiveDuringLastFetch;

    public void setQuotes(List<ProviderQuote> quotes) {
        this.quotes = quotes;
    }

    /**
     * Whether a transaction was active on the calling thread during the most
     * recent {@link #fetchTopCoins}. Lets tests assert the poller's HTTP call
     * runs outside any transaction, since a hung call must never pin a DB
     * connection. Null until fetchTopCoins has been called at least once.
     */
    public Boolean wasTransactionActiveDuringLastFetch() {
        return transactionActiveDuringLastFetch;
    }

    @Override
    public List<ProviderQuote> fetchTopCoins(int limit, String convert) {
        transactionActiveDuringLastFetch = TransactionSynchronizationManager.isActualTransactionActive();
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
