package com.am.market_hub.alert.service;

import java.math.BigDecimal;

import com.am.market_hub.alert.domain.PriceAlert;
import com.am.market_hub.alert.repository.AlertRepository;
import com.am.market_hub.notification.domain.Notification;
import com.am.market_hub.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triggers a single alert and saves its notification in its own transaction,
 * separate from {@code AlertEvaluationService}'s per-cycle read loop.
 *
 * <p>{@code REQUIRES_NEW} matters, not just style: Postgres aborts an entire
 * transaction after any statement error, so if one alert's write ever hit a
 * constraint violation (e.g. the {@code notifications.alert_id} uniqueness
 * guarantee, which should never fire given the no-re-arm invariant, but is
 * the real backstop if it ever did) inside the same transaction as every
 * other alert in the cycle, it would silently roll back every other
 * legitimately-triggered alert too — not just the offending one. A separate
 * transaction per alert means one bad row can only cost that one alert a
 * cycle (it stays active and is simply re-evaluated next time); it can never
 * defer the rest of the batch.
 */
@Service
public class AlertTriggerService {

    private final AlertRepository alertRepository;
    private final NotificationRepository notificationRepository;

    public AlertTriggerService(AlertRepository alertRepository, NotificationRepository notificationRepository) {
        this.alertRepository = alertRepository;
        this.notificationRepository = notificationRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void trigger(Long alertId, BigDecimal price) {
        // Not an ApiException case: this is background poller work, not an
        // HTTP request, and alertId was read from findByActiveTrue() moments
        // earlier in the same evaluation cycle — its absence here would mean
        // a genuinely unexpected concurrent deletion, not a normal 404.
        PriceAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalStateException("Alert " + alertId + " vanished mid-evaluation"));
        alert.trigger(price);
        notificationRepository.save(Notification.from(alert));
    }
}
