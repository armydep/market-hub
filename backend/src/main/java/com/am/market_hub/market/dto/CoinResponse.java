package com.am.market_hub.market.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.am.market_hub.market.domain.CryptoQuote;

/** Public representation of a cached quote (entities are never returned directly). */
public record CoinResponse(
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
        String convertCurrency,
        Instant updatedAt) {

    public static CoinResponse from(CryptoQuote q) {
        return new CoinResponse(
                q.getCmcId(), q.getSymbol(), q.getName(), q.getSlug(), q.getAssetType(),
                q.getMarketCapRank(), q.getPrice(), q.getPctChange1h(), q.getPctChange24h(),
                q.getPctChange7d(), q.getMarketCap(), q.getVolume24h(), q.getCirculatingSupply(),
                q.getConvertCurrency(), q.getUpdatedAt());
    }
}
