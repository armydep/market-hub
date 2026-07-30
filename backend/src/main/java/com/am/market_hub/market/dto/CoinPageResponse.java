package com.am.market_hub.market.dto;

import java.time.Instant;
import java.util.List;

import com.am.market_hub.market.domain.CryptoQuote;

import org.springframework.data.domain.Page;

/**
 * Page envelope for the public coin list (F001-FR-013). Deliberately a hand-rolled
 * record rather than a serialized {@code Page}: the wire shape is a public API
 * contract the SPA depends on, and Spring Data's own serialization is neither
 * stable across versions nor meant to be one.
 *
 * @param lastUpdatedAt time of the last successful market-data update
 *                      (F001-FR-019); null when the universe is empty
 */
public record CoinPageResponse(
        List<CoinResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        Instant lastUpdatedAt) {

    public static CoinPageResponse from(Page<CryptoQuote> page, Instant lastUpdatedAt) {
        return new CoinPageResponse(
                page.getContent().stream().map(CoinResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                lastUpdatedAt);
    }
}
