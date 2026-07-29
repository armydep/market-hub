package com.am.market_hub.market.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.am.market_hub.market.domain.CryptoQuote;
import com.am.market_hub.market.domain.PollCompletedEvent;
import com.am.market_hub.market.provider.PriceProvider;
import com.am.market_hub.market.provider.ProviderQuote;
import com.am.market_hub.market.repository.CryptoQuoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled, single-instance poller. Each cycle upserts the top-N universe from
 * the {@link PriceProvider} and drops coins that left it, then publishes
 * {@link PollCompletedEvent}. An empty provider result (no key / transient
 * failure) skips the cycle so the last-known universe is preserved rather than
 * wiped.
 */
@Component
public class CryptoPoller {

    private static final Logger log = LoggerFactory.getLogger(CryptoPoller.class);

    private final PriceProvider provider;
    private final CryptoQuoteRepository repository;
    private final ApplicationEventPublisher events;
    private final boolean enabled;
    private final int coinLimit;
    private final String convert;

    public CryptoPoller(PriceProvider provider,
                        CryptoQuoteRepository repository,
                        ApplicationEventPublisher events,
                        @Value("${app.poller.enabled}") boolean enabled,
                        @Value("${app.poller.coin-limit}") int coinLimit,
                        @Value("${app.coinmarketcap.convert}") String convert) {
        this.provider = provider;
        this.repository = repository;
        this.events = events;
        this.enabled = enabled;
        this.coinLimit = coinLimit;
        this.convert = convert;
    }

    @Scheduled(fixedDelayString = "${app.poller.interval-ms}",
            initialDelayString = "${app.poller.initial-delay-ms}")
    public void scheduledPoll() {
        if (enabled) {
            pollOnce();
        }
    }

    /**
     * Run one poll cycle. Returns the number of coins upserted (0 when the
     * provider had no data and the cycle was skipped).
     */
    @Transactional
    public int pollOnce() {
        List<ProviderQuote> quotes = provider.fetchTopCoins(coinLimit, convert);
        if (quotes.isEmpty()) {
            log.warn("Provider '{}' returned no data; skipping cycle (universe unchanged)", provider.name());
            return 0;
        }

        List<Integer> ids = quotes.stream().map(ProviderQuote::cmcId).toList();
        Map<Integer, CryptoQuote> existing = repository.findAllById(ids).stream()
                .collect(Collectors.toMap(CryptoQuote::getCmcId, Function.identity()));

        List<CryptoQuote> toSave = new ArrayList<>(quotes.size());
        for (ProviderQuote q : quotes) {
            CryptoQuote entity = existing.get(q.cmcId());
            if (entity == null) {
                entity = CryptoQuote.fromProvider(q);
            } else {
                entity.applyProvider(q);
            }
            toSave.add(entity);
        }
        repository.saveAll(toSave);
        int removed = repository.deleteByCmcIdNotIn(ids);

        log.info("Poll complete: {} coins upserted, {} stale removed", toSave.size(), removed);
        events.publishEvent(new PollCompletedEvent(toSave.size()));
        return toSave.size();
    }
}
