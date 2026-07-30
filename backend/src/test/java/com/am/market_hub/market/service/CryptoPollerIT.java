package com.am.market_hub.market.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

import com.am.market_hub.market.domain.CryptoQuote;
import com.am.market_hub.market.repository.CryptoQuoteRepository;
import com.am.market_hub.support.StubPriceProvider;
import com.am.market_hub.support.StubProviderConfig;
import com.am.market_hub.support.TestcontainersConfig;

/** Poller upsert: insert path, update path, stale removal, empty-skip. */
@SpringBootTest
@Import({TestcontainersConfig.class, StubProviderConfig.class})
@TestPropertySource(properties = "app.poller.enabled=false") // drive pollOnce() manually
class CryptoPollerIT {

    @Autowired
    private StubPriceProvider stub;
    @Autowired
    private CryptoPoller poller;
    @Autowired
    private CryptoQuoteRepository repository;
    @Autowired
    private ApplicationEventPublisher events;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void insertsThenUpdatesAndRemovesStale() {
        stub.setQuotes(List.of(
                StubPriceProvider.quote(1, "BTC", 1, "60000"),
                StubPriceProvider.quote(1027, "ETH", 2, "3000")));

        int inserted = poller.pollOnce();

        assertThat(inserted).isEqualTo(2);
        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.findFirstBySymbolIgnoreCaseOrderByMarketCapRankAsc("BTC")).get()
                .extracting(CryptoQuote::getPrice).satisfies(p -> assertThat(p).isEqualByComparingTo("60000"));

        // BTC price changes (update path); ETH leaves the top-N (stale removal).
        stub.setQuotes(List.of(StubPriceProvider.quote(1, "BTC", 1, "61000")));

        poller.pollOnce();

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findFirstBySymbolIgnoreCaseOrderByMarketCapRankAsc("BTC")).get()
                .extracting(CryptoQuote::getPrice).satisfies(p -> assertThat(p).isEqualByComparingTo("61000"));
        assertThat(repository.findFirstBySymbolIgnoreCaseOrderByMarketCapRankAsc("ETH")).isEmpty();
    }

    @Test
    void emptyResultSkipsCycleAndPreservesUniverse() {
        stub.setQuotes(List.of(StubPriceProvider.quote(1, "BTC", 1, "60000")));
        poller.pollOnce();

        stub.setQuotes(List.of()); // e.g. missing key / transient failure
        int upserted = poller.pollOnce();

        assertThat(upserted).isZero();
        assertThat(repository.count()).isEqualTo(1); // last-known universe preserved
    }

    @Test
    void suspiciouslyPartialResultSkipsCycleAndPreservesUniverse() {
        stub.setQuotes(List.of(
                StubPriceProvider.quote(1, "C1", 1, "1"), StubPriceProvider.quote(2, "C2", 2, "1"),
                StubPriceProvider.quote(3, "C3", 3, "1"), StubPriceProvider.quote(4, "C4", 4, "1"),
                StubPriceProvider.quote(5, "C5", 5, "1"), StubPriceProvider.quote(6, "C6", 6, "1"),
                StubPriceProvider.quote(7, "C7", 7, "1"), StubPriceProvider.quote(8, "C8", 8, "1"),
                StubPriceProvider.quote(9, "C9", 9, "1"), StubPriceProvider.quote(10, "C10", 10, "1")));
        poller.pollOnce();
        assertThat(repository.count()).isEqualTo(10);

        // e.g. a paginated/truncated CMC response - well under half the current universe.
        stub.setQuotes(List.of(StubPriceProvider.quote(1, "C1", 1, "1"), StubPriceProvider.quote(2, "C2", 2, "1")));
        int upserted = poller.pollOnce();

        assertThat(upserted).isZero();
        assertThat(repository.count()).isEqualTo(10); // full universe preserved, nothing wrongly delisted
    }

    @Test
    void reproduceLoweredCoinLimitScenario() {
        // Simulate an operator lowering POLLER_COIN_LIMIT after the universe has
        // already grown to the old, larger size (e.g. for CMC-credit cost control).
        stub.setQuotes(List.of(
                StubPriceProvider.quote(1, "C1", 1, "1"), StubPriceProvider.quote(2, "C2", 2, "1"),
                StubPriceProvider.quote(3, "C3", 3, "1"), StubPriceProvider.quote(4, "C4", 4, "1"),
                StubPriceProvider.quote(5, "C5", 5, "1"), StubPriceProvider.quote(6, "C6", 6, "1"),
                StubPriceProvider.quote(7, "C7", 7, "1"), StubPriceProvider.quote(8, "C8", 8, "1"),
                StubPriceProvider.quote(9, "C9", 9, "1"), StubPriceProvider.quote(10, "C10", 10, "1")));
        poller.pollOnce();
        assertThat(repository.count()).isEqualTo(10);

        // A second poller, standing in for the app restarted with a lower configured
        // limit. The "provider" now legitimately only returns the new smaller top-N.
        CryptoPoller lowerLimitPoller = new CryptoPoller(stub, repository, events, transactionManager,
                true, 2, "USD");
        stub.setQuotes(List.of(StubPriceProvider.quote(1, "C1", 1, "1"), StubPriceProvider.quote(2, "C2", 2, "1")));

        lowerLimitPoller.pollOnce();
        lowerLimitPoller.pollOnce();
        lowerLimitPoller.pollOnce();

        assertThat(repository.count()).isEqualTo(2);
    }
}
