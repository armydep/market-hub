package com.am.market_hub.market.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.am.market_hub.market.repository.CryptoQuoteRepository;
import com.am.market_hub.support.StubPriceProvider;
import com.am.market_hub.support.StubProviderConfig;
import com.am.market_hub.support.TestcontainersConfig;

/**
 * Scheduled poll entrypoint: pins the transaction boundary the poller-transaction
 * fix established. The provider fetch must run with no transaction open (a
 * hung HTTP call must never pin a DB connection); only the upsert is
 * transactional.
 */
@SpringBootTest
@Import({TestcontainersConfig.class, StubProviderConfig.class})
@TestPropertySource(properties = {
        "app.poller.enabled=true",
        "app.poller.initial-delay-ms=3600000",
        "app.poller.interval-ms=3600000"
})
class CryptoPollerSchedulingTest {

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
    void providerFetchRunsWithNoTransactionOpen() {
        stub.setQuotes(List.of(StubPriceProvider.quote(1, "BTC", 1, "60000")));

        poller.scheduledPoll();

        assertThat(stub.wasTransactionActiveDuringLastFetch()).isFalse();
    }

    @Test
    void scheduledPollUpsertsThenUpdatesAndRemovesStale() {
        stub.setQuotes(List.of(
                StubPriceProvider.quote(1, "BTC", 1, "60000"),
                StubPriceProvider.quote(1027, "ETH", 2, "3000")));
        poller.scheduledPoll();

        stub.setQuotes(List.of(StubPriceProvider.quote(1, "BTC", 1, "61000")));

        poller.scheduledPoll();

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findFirstBySymbolIgnoreCaseOrderByMarketCapRankAsc("BTC")).isPresent();
        assertThat(repository.findFirstBySymbolIgnoreCaseOrderByMarketCapRankAsc("ETH")).isEmpty();
    }
}
