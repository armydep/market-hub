package com.am.market_hub.market;

import java.util.List;

/**
 * Single seam over the external price source. Only the poller depends on this;
 * the read path never calls a provider. Implementations must degrade gracefully
 * (return an empty list, never throw) when they cannot produce data.
 */
public interface PriceProvider {

    /**
     * Fetch the top-{@code limit} coins by market cap in {@code convert} currency.
     * Returns an empty list when the source is unavailable (e.g. missing API key).
     */
    List<ProviderQuote> fetchTopCoins(int limit, String convert);

    /** Short identifier for logging (e.g. {@code "coinmarketcap"}). */
    String name();
}
