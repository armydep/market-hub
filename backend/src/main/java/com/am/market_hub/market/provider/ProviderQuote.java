package com.am.market_hub.market.provider;

import java.math.BigDecimal;

/**
 * Provider-neutral quote reading returned by a {@link PriceProvider}. Decouples
 * business logic from any specific price source (CoinMarketCap, CoinGecko, ...).
 */
public record ProviderQuote(
        int cmcId,
        String symbol,
        String name,
        String slug,
        String assetType,
        Integer marketCapRank,
        BigDecimal price,
        BigDecimal pctChange1h,
        BigDecimal pctChange24h,
        BigDecimal pctChange7d,
        BigDecimal marketCap,
        BigDecimal volume24h,
        BigDecimal circulatingSupply,
        String convertCurrency) {
}
