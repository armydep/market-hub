package com.am.market_hub.alert.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.am.market_hub.alert.domain.PriceAlert;
import com.am.market_hub.alert.repository.AlertRepository;
import com.am.market_hub.market.domain.CryptoQuote;
import com.am.market_hub.market.domain.PollCompletedEvent;
import com.am.market_hub.market.repository.CryptoQuoteRepository;
import com.am.market_hub.notification.domain.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The post-poll alert-evaluation hook {@code PollCompletedEvent} was
 * introduced for. Fires only after a successful poll cycle already committed
 * its upsert (constraints.md: "quotes and alert checks stay consistent and
 * evaluation can never run against a half-written or skipped universe").
 *
 * <p>The read here is {@code @Transactional(readOnly = true)}, deliberately
 * separate from each alert's own write: each trigger and its
 * {@link Notification} commit in {@link AlertTriggerService}'s own
 * {@code REQUIRES_NEW} transaction (PRD F006-FR-009/F007-FR-001's "trigger
 * and notification either both commit or neither does" — just scoped per
 * alert instead of per cycle, so one alert's constraint violation can never
 * roll back every other legitimately-triggered alert in the same batch).
 */
@Service
public class AlertEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(AlertEvaluationService.class);

    private final AlertRepository alertRepository;
    private final CryptoQuoteRepository cryptoQuoteRepository;
    private final AlertTriggerService alertTriggerService;

    public AlertEvaluationService(
            AlertRepository alertRepository,
            CryptoQuoteRepository cryptoQuoteRepository,
            AlertTriggerService alertTriggerService) {
        this.alertRepository = alertRepository;
        this.cryptoQuoteRepository = cryptoQuoteRepository;
        this.alertTriggerService = alertTriggerService;
    }

    @EventListener
    @Transactional(readOnly = true)
    public void onPollCompleted(PollCompletedEvent event) {
        List<PriceAlert> activeAlerts = alertRepository.findByActiveTrue();
        if (activeAlerts.isEmpty()) {
            return;
        }
        int triggered = 0;
        for (PriceAlert alert : activeAlerts) {
            Optional<CryptoQuote> quote = cryptoQuoteRepository
                    .findFirstBySymbolIgnoreCaseOrderByMarketCapRankAsc(alert.getSymbol());
            if (quote.isEmpty()) {
                // Symbol left the universe since creation - not evaluable this cycle, not an error.
                continue;
            }
            if (alert.getCondition().isSatisfiedBy(quote.get().getPrice(), alert.getTargetPrice())) {
                if (triggerOne(alert, quote.get().getPrice())) {
                    triggered++;
                }
            }
        }
        if (triggered > 0) {
            log.info("Alert evaluation: {} of {} active alerts triggered", triggered, activeAlerts.size());
        }
    }

    /**
     * One alert's constraint violation (e.g. a duplicate notification, which
     * should never happen given no-re-arm, but the DB constraint — not that
     * invariant — is the real guarantee) must not stop the rest of the
     * cycle's legitimately-triggered alerts. It stays active and is simply
     * re-evaluated next cycle: self-healing, not data loss.
     */
    private boolean triggerOne(PriceAlert alert, BigDecimal price) {
        try {
            alertTriggerService.trigger(alert.getId(), price);
            return true;
        } catch (DataIntegrityViolationException e) {
            log.warn("Could not trigger alert {}: {}", alert.getId(), e.getMessage());
            return false;
        }
    }
}
