package com.am.market_hub.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Pure unit test (no Spring/Docker): CMC JSON parsing and missing-key degradation.
 */
class CoinMarketCapProviderTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesListingsFixture() throws Exception {
        CoinMarketCapProvider provider = new CoinMarketCapProvider(WebClient.create(), "dummy-key");
        JsonNode root = mapper.readTree(getClass().getResourceAsStream("/cmc-listings-latest.json"));

        List<ProviderQuote> quotes = provider.mapResponse(root, "USD");

        assertThat(quotes).hasSize(2);
        ProviderQuote btc = quotes.get(0);
        assertThat(btc.cmcId()).isEqualTo(1);
        assertThat(btc.symbol()).isEqualTo("BTC");
        assertThat(btc.name()).isEqualTo("Bitcoin");
        assertThat(btc.slug()).isEqualTo("bitcoin");
        assertThat(btc.assetType()).isEqualTo("CRYPTO");
        assertThat(btc.marketCapRank()).isEqualTo(1);
        assertThat(btc.price()).isEqualByComparingTo("60000.12");
        assertThat(btc.pctChange1h()).isEqualByComparingTo("0.10");
        assertThat(btc.pctChange24h()).isEqualByComparingTo("1.25");
        assertThat(btc.pctChange7d()).isEqualByComparingTo("-3.40");
        assertThat(btc.marketCap()).isEqualByComparingTo("1180000000000.0");
        assertThat(btc.volume24h()).isEqualByComparingTo("25000000000.0");
        assertThat(btc.circulatingSupply()).isEqualByComparingTo("19700000.0");
        assertThat(btc.convertCurrency()).isEqualTo("USD");

        assertThat(quotes.get(1).symbol()).isEqualTo("ETH");
        assertThat(quotes.get(1).marketCapRank()).isEqualTo(2);
    }

    @Test
    void missingApiKeyDegradesToEmpty() {
        CoinMarketCapProvider provider = new CoinMarketCapProvider(WebClient.create(), "   ");

        assertThat(provider.fetchTopCoins(100, "USD")).isEmpty();
    }
}
