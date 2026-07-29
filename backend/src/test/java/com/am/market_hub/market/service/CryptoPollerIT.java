package com.am.market_hub.market.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

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
        assertThat(repository.findBySymbolIgnoreCase("BTC")).get()
                .extracting(CryptoQuote::getPrice).satisfies(p -> assertThat(p).isEqualByComparingTo("60000"));

        // BTC price changes (update path); ETH leaves the top-N (stale removal).
        stub.setQuotes(List.of(StubPriceProvider.quote(1, "BTC", 1, "61000")));

        poller.pollOnce();

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findBySymbolIgnoreCase("BTC")).get()
                .extracting(CryptoQuote::getPrice).satisfies(p -> assertThat(p).isEqualByComparingTo("61000"));
        assertThat(repository.findBySymbolIgnoreCase("ETH")).isEmpty();
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
}
