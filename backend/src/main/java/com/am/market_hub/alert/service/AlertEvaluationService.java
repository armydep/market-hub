package com.am.market_hub.alert.service;

import java.util.List;
import java.util.Optional;

import com.am.market_hub.alert.domain.PriceAlert;
import com.am.market_hub.alert.repository.AlertRepository;
import com.am.market_hub.market.domain.CryptoQuote;
import com.am.market_hub.market.domain.PollCompletedEvent;
import com.am.market_hub.market.repository.CryptoQuoteRepository;
import com.am.market_hub.notification.domain.Notification;
import com.am.market_hub.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The post-poll alert-evaluation hook {@code PollCompletedEvent} was
 * introduced for. Fires only after a successful poll cycle already committed
 * its upsert (constraints.md: "quotes and alert checks stay consistent and
 * evaluation can never run against a half-written or skipped universe").
 *
 * <p>S10: a trigger creates its {@link Notification} in this same
 * transaction — not a second listener — so a trigger and its notification
 * either both commit or neither does (PRD F006-FR-009/F007-FR-001).
 */
@Service
public class AlertEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(AlertEvaluationService.class);

    private final AlertRepository alertRepository;
    private final CryptoQuoteRepository cryptoQuoteRepository;
    private final NotificationRepository notificationRepository;

    public AlertEvaluationService(
            AlertRepository alertRepository,
            CryptoQuoteRepository cryptoQuoteRepository,
            NotificationRepository notificationRepository) {
        this.alertRepository = alertRepository;
        this.cryptoQuoteRepository = cryptoQuoteRepository;
        this.notificationRepository = notificationRepository;
    }

    @EventListener
    @Transactional
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
                alert.trigger(quote.get().getPrice());
                notificationRepository.save(Notification.from(alert));
                triggered++;
            }
        }
        if (triggered > 0) {
            log.info("Alert evaluation: {} of {} active alerts triggered", triggered, activeAlerts.size());
        }
    }
}
