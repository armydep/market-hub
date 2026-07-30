package com.am.market_hub.market.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.am.market_hub.market.dto.CoinResponse;
import com.am.market_hub.market.provider.ProviderQuote;
import com.am.market_hub.market.repository.CryptoQuoteRepository;
import com.am.market_hub.market.service.CryptoPoller;
import com.am.market_hub.support.StubPriceProvider;
import com.am.market_hub.support.StubProviderConfig;
import com.am.market_hub.support.TestcontainersConfig;

/** Public read API: sort ordering, symbol lookup, and error contracts. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfig.class, StubProviderConfig.class})
@TestPropertySource(properties = "app.poller.enabled=false")
class MarketControllerIT {

    @LocalServerPort
    private int port;
    @Autowired
    private StubPriceProvider stub;
    @Autowired
    private CryptoPoller poller;
    @Autowired
    private CryptoQuoteRepository repository;

    private RestClient client;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        stub.setQuotes(List.of(
                StubPriceProvider.quote(1, "BTC", 1, "60000"),
                StubPriceProvider.quote(1027, "ETH", 2, "3000")));
        poller.pollOnce();
        client = RestClient.create("http://localhost:" + port + "/api");
    }

    @Test
    void listsSortedByPriceDescending() {
        CoinResponse[] coins = client.get().uri("/market/coins?sort=price&order=desc")
                .retrieve().body(CoinResponse[].class);

        assertThat(coins).extracting(CoinResponse::symbol).containsExactly("BTC", "ETH");
    }

    @Test
    void defaultSortIsByMarketCapRankAscending() {
        CoinResponse[] coins = client.get().uri("/market/coins")
                .retrieve().body(CoinResponse[].class);

        assertThat(coins).extracting(CoinResponse::symbol).containsExactly("BTC", "ETH");
    }

    @Test
    void getBySymbolIsCaseInsensitive() {
        CoinResponse eth = client.get().uri("/market/coins/eth")
                .retrieve().body(CoinResponse.class);

        assertThat(eth.symbol()).isEqualTo("ETH");
        assertThat(eth.marketCapRank()).isEqualTo(2);
    }

    @Test
    void unknownSymbolReturns404() {
        assertThatThrownBy(() -> client.get().uri("/market/coins/DOGE")
                .retrieve().body(CoinResponse.class))
                .isInstanceOfSatisfying(HttpClientErrorException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void descendingSortPlacesNullValueLastNotFirst() {
        repository.deleteAll();
        stub.setQuotes(List.of(
                StubPriceProvider.quote(1, "BTC", 1, "60000"),
                new ProviderQuote(99, "NEW", "New Coin", "new-coin", "CRYPTO", 3, null,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, "USD")));
        poller.pollOnce();

        CoinResponse[] coins = client.get().uri("/market/coins?sort=price&order=desc")
                .retrieve().body(CoinResponse[].class);

        assertThat(coins).extracting(CoinResponse::symbol).containsExactly("BTC", "NEW");
    }

    @Test
    void getBySymbolPicksHighestRankedWhenSymbolIsDuplicated() {
        repository.deleteAll();
        stub.setQuotes(List.of(
                StubPriceProvider.quote(1, "BTC", 5, "1"),
                StubPriceProvider.quote(2, "BTC", 1, "2")));
        poller.pollOnce();

        CoinResponse btc = client.get().uri("/market/coins/BTC")
                .retrieve().body(CoinResponse.class);

        assertThat(btc.cmcId()).isEqualTo(2);
        assertThat(btc.marketCapRank()).isEqualTo(1);
    }

    @Test
    void emptyUniverseReturnsEmptyListNotAnError() {
        repository.deleteAll();

        CoinResponse[] coins = client.get().uri("/market/coins")
                .retrieve().body(CoinResponse[].class);

        assertThat(coins).isEmpty();
    }

    @Test
    void invalidSortFieldReturns400() {
        assertThatThrownBy(() -> client.get().uri("/market/coins?sort=bogus")
                .retrieve().body(CoinResponse[].class))
                .isInstanceOfSatisfying(HttpClientErrorException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }
}
