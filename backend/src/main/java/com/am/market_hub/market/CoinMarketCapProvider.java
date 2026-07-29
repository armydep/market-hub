package com.am.market_hub.market;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import tools.jackson.databind.JsonNode;

/**
 * Phase-1 {@link PriceProvider} backed by CoinMarketCap's
 * {@code /v1/cryptocurrency/listings/latest}. Degrades gracefully: a missing API
 * key or any call failure yields an empty list instead of throwing, so the app
 * boots and serves an empty universe without a key.
 */
@Component
public class CoinMarketCapProvider implements PriceProvider {

    private static final Logger log = LoggerFactory.getLogger(CoinMarketCapProvider.class);

    private final WebClient webClient;
    private final String apiKey;
    private boolean missingKeyWarned;

    public CoinMarketCapProvider(WebClient coinMarketCapWebClient,
                                 @Value("${app.coinmarketcap.api-key}") String apiKey) {
        this.webClient = coinMarketCapWebClient;
        this.apiKey = apiKey;
    }

    @Override
    public String name() {
        return "coinmarketcap";
    }

    @Override
    public List<ProviderQuote> fetchTopCoins(int limit, String convert) {
        if (!StringUtils.hasText(apiKey)) {
            if (!missingKeyWarned) {
                log.warn("CMC_API_KEY not set; serving an empty universe. Set it for live data.");
                missingKeyWarned = true;
            }
            return List.of();
        }
        try {
            JsonNode root = webClient.get()
                    .uri(uri -> uri.path("/v1/cryptocurrency/listings/latest")
                            .queryParam("start", 1)
                            .queryParam("limit", limit)
                            .queryParam("convert", convert)
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            return mapResponse(root, convert);
        } catch (Exception ex) {
            log.warn("CoinMarketCap fetch failed ({}); keeping last-known universe", ex.toString());
            return List.of();
        }
    }

    /** Map a listings/latest response body into provider-neutral quotes. Package-private for testing. */
    List<ProviderQuote> mapResponse(JsonNode root, String convert) {
        List<ProviderQuote> quotes = new ArrayList<>();
        if (root == null) {
            return quotes;
        }
        for (JsonNode coin : root.path("data")) {
            JsonNode q = coin.path("quote").path(convert);
            quotes.add(new ProviderQuote(
                    coin.path("id").asInt(),
                    text(coin, "symbol"),
                    text(coin, "name"),
                    text(coin, "slug"),
                    "CRYPTO",
                    integer(coin.get("cmc_rank")),
                    decimal(q.get("price")),
                    decimal(q.get("percent_change_1h")),
                    decimal(q.get("percent_change_24h")),
                    decimal(q.get("percent_change_7d")),
                    decimal(q.get("market_cap")),
                    decimal(q.get("volume_24h")),
                    decimal(coin.get("circulating_supply")),
                    convert));
        }
        return quotes;
    }

    private static String text(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }

    private static Integer integer(JsonNode n) {
        return n == null || !n.isNumber() ? null : n.intValue();
    }

    private static BigDecimal decimal(JsonNode n) {
        return n == null || !n.isNumber() ? null : n.decimalValue();
    }
}
