package com.am.market_hub.market.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.am.market_hub.market.dto.CoinPageResponse;
import com.am.market_hub.market.dto.CoinResponse;
import com.am.market_hub.market.dto.ColumnCatalogResponse;
import com.am.market_hub.market.provider.ProviderQuote;
import com.am.market_hub.market.repository.CryptoQuoteRepository;
import com.am.market_hub.market.service.CryptoPoller;
import com.am.market_hub.support.StubPriceProvider;
import com.am.market_hub.support.StubProviderConfig;
import com.am.market_hub.support.TestcontainersConfig;

/**
 * Public read API: pagination, search, sort ordering, column catalog, and error
 * contracts.
 *
 * <p>The default fixture seeds more coins than fit on one page on purpose — a
 * single-page fixture would let a sort-within-the-page bug pass while still
 * violating F001-FR-011.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfig.class, StubProviderConfig.class})
@TestPropertySource(properties = {
        "app.poller.enabled=false",
        "app.market.supported-page-sizes=5,20,50,100",
        "app.market.default-page-size=20"
})
class MarketControllerIT {

    private static final int SEEDED_COINS = 12;

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
        // Rank n has price n; coin 1 is the cheapest, coin 12 the priciest.
        List<ProviderQuote> quotes = new ArrayList<>();
        for (int i = 1; i <= SEEDED_COINS; i++) {
            quotes.add(StubPriceProvider.quote(i, "C" + i, i, String.valueOf(i)));
        }
        stub.setQuotes(quotes);
        poller.pollOnce();
        client = RestClient.create("http://localhost:" + port + "/api");
    }

    private CoinPageResponse get(String uri) {
        return client.get().uri(uri).retrieve().body(CoinPageResponse.class);
    }

    private static void assertBadRequest(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(HttpClientErrorException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    // --- pagination -------------------------------------------------------

    @Test
    void defaultsToFirstPageOfTwenty() {
        CoinPageResponse response = get("/market/coins");

        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(SEEDED_COINS);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.content()).hasSize(SEEDED_COINS);
    }

    @Test
    void pagesDoNotOverlapAndCoverTheDataset() {
        CoinPageResponse first = get("/market/coins?size=5&page=0");
        CoinPageResponse second = get("/market/coins?size=5&page=1");
        CoinPageResponse third = get("/market/coins?size=5&page=2");

        assertThat(first.content()).hasSize(5);
        assertThat(second.content()).hasSize(5);
        assertThat(third.content()).hasSize(2);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.totalElements()).isEqualTo(SEEDED_COINS);

        List<String> all = new ArrayList<>();
        first.content().forEach(c -> all.add(c.symbol()));
        second.content().forEach(c -> all.add(c.symbol()));
        third.content().forEach(c -> all.add(c.symbol()));
        assertThat(all).doesNotHaveDuplicates().hasSize(SEEDED_COINS);
    }

    @Test
    void pageBeyondTheEndIsEmptyNotAnError() {
        CoinPageResponse response = get("/market/coins?size=5&page=99");

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isEqualTo(SEEDED_COINS);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    void unsupportedPageSizeReturns400() {
        assertBadRequest(() -> get("/market/coins?size=25"));
        assertBadRequest(() -> get("/market/coins?size=0"));
        assertBadRequest(() -> get("/market/coins?size=-1"));
    }

    @Test
    void negativePageReturns400() {
        assertBadRequest(() -> get("/market/coins?page=-1"));
    }

    // --- sort applies before pagination (F001-FR-011) ---------------------

    @Test
    void sortAppliesAcrossWholeDatasetNotJustThePage() {
        // C12 is the globally priciest coin but sorts last by rank, so it only
        // reaches page 0 if the sort ran before the page was cut.
        CoinPageResponse response = get("/market/coins?sort=price&order=desc&size=5&page=0");

        assertThat(response.content()).extracting(CoinResponse::symbol)
                .containsExactly("C12", "C11", "C10", "C9", "C8");
    }

    @Test
    void defaultSortIsByMarketCapRankAscending() {
        CoinPageResponse response = get("/market/coins?size=5");

        assertThat(response.content()).extracting(CoinResponse::symbol)
                .containsExactly("C1", "C2", "C3", "C4", "C5");
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

        CoinPageResponse response = get("/market/coins?sort=price&order=desc");

        assertThat(response.content()).extracting(CoinResponse::symbol).containsExactly("BTC", "NEW");
    }

    @Test
    void invalidSortFieldOrOrderReturns400() {
        assertBadRequest(() -> get("/market/coins?sort=bogus"));
        assertBadRequest(() -> get("/market/coins?order=sideways"));
    }

    // --- search (F-002) ---------------------------------------------------

    @Test
    void searchesByNameSubstringCaseInsensitively() {
        // Stub names are "<symbol>-name", so "1-NAME" matches C1, C10, C11, C12.
        CoinPageResponse response = get("/market/coins?q=1-NAME");

        assertThat(response.content()).extracting(CoinResponse::symbol)
                .containsExactlyInAnyOrder("C1", "C10", "C11", "C12");
    }

    @Test
    void searchesBySymbolSubstringCaseInsensitively() {
        CoinPageResponse lower = get("/market/coins?q=c1");
        CoinPageResponse upper = get("/market/coins?q=C1");

        assertThat(lower.content()).extracting(CoinResponse::symbol)
                .containsExactlyInAnyOrder("C1", "C10", "C11", "C12");
        assertThat(upper.content()).extracting(CoinResponse::symbol)
                .containsExactlyElementsOf(lower.content().stream().map(CoinResponse::symbol).toList());
    }

    @Test
    void noMatchIsAnEmptyPageNotA404() {
        CoinPageResponse response = get("/market/coins?q=doge");

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
    }

    @Test
    void blankSearchRestoresTheFullDataset() {
        assertThat(get("/market/coins?q=").totalElements()).isEqualTo(SEEDED_COINS);
        assertThat(get("/market/coins?q=%20").totalElements()).isEqualTo(SEEDED_COINS);
    }

    @Test
    void likeMetacharactersAreTreatedLiterally() {
        // Without escaping these would wildcard-match the entire universe.
        assertThat(get("/market/coins?q=%25").totalElements()).isZero();
        assertThat(get("/market/coins?q=_").totalElements()).isZero();
    }

    @Test
    void searchAndSortComposeAcrossTheWholeMatchingSet() {
        CoinPageResponse response = get("/market/coins?q=c1&sort=price&order=desc&size=5");

        assertThat(response.totalElements()).isEqualTo(4);
        assertThat(response.content()).extracting(CoinResponse::symbol)
                .containsExactly("C12", "C11", "C10", "C1");
    }

    // --- column catalog ---------------------------------------------------

    @Test
    void columnCatalogExposesSupportedAndDefaultSets() {
        ColumnCatalogResponse catalog = client.get().uri("/market/columns")
                .retrieve().body(ColumnCatalogResponse.class);

        assertThat(catalog.supported()).containsExactlyInAnyOrder(
                "symbol", "name", "marketCapRank", "price", "pctChange1h", "pctChange24h",
                "pctChange7d", "marketCap", "volume24h", "circulatingSupply");
        assertThat(catalog.defaultVisible()).isNotEmpty();
        assertThat(catalog.supported()).containsAll(catalog.defaultVisible());
        assertThat(catalog.supportedPageSizes()).contains(20);
        assertThat(catalog.defaultPageSize()).isEqualTo(20);
    }

    // --- freshness (F001-FR-019) -----------------------------------------

    @Test
    void lastUpdatedAtReflectsTheStoredUniverse() {
        CoinPageResponse response = get("/market/coins");

        assertThat(response.lastUpdatedAt()).isNotNull();
        assertThat(response.lastUpdatedAt()).isEqualTo(repository.findLastUpdatedAt());
    }

    @Test
    void lastUpdatedAtIsNullWhenUniverseIsEmpty() {
        repository.deleteAll();

        CoinPageResponse response = get("/market/coins");

        assertThat(response.content()).isEmpty();
        assertThat(response.lastUpdatedAt()).isNull();
    }

    // --- detail endpoint (unchanged by this slice) ------------------------

    @Test
    void getBySymbolIsCaseInsensitive() {
        CoinResponse coin = client.get().uri("/market/coins/c2")
                .retrieve().body(CoinResponse.class);

        assertThat(coin.symbol()).isEqualTo("C2");
        assertThat(coin.marketCapRank()).isEqualTo(2);
    }

    @Test
    void unknownSymbolReturns404() {
        assertThatThrownBy(() -> client.get().uri("/market/coins/DOGE")
                .retrieve().body(CoinResponse.class))
                .isInstanceOfSatisfying(HttpClientErrorException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(404));
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
}
