package com.am.market_hub.alert.service;

import java.util.List;
import java.util.Optional;

import com.am.market_hub.alert.domain.PriceAlert;
import com.am.market_hub.alert.dto.AlertResponse;
import com.am.market_hub.alert.dto.CreateAlertRequest;
import com.am.market_hub.alert.dto.UpdateAlertRequest;
import com.am.market_hub.alert.repository.AlertRepository;
import com.am.market_hub.auth.security.CurrentUser;
import com.am.market_hub.common.exception.ApiException;
import com.am.market_hub.market.domain.CryptoQuote;
import com.am.market_hub.market.repository.CryptoQuoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertService {

    private final AlertRepository alertRepository;
    private final CryptoQuoteRepository cryptoQuoteRepository;
    private final CurrentUser currentUser;

    public AlertService(AlertRepository alertRepository, CryptoQuoteRepository cryptoQuoteRepository,
            CurrentUser currentUser) {
        this.alertRepository = alertRepository;
        this.cryptoQuoteRepository = cryptoQuoteRepository;
        this.currentUser = currentUser;
    }

    @Transactional
    public AlertResponse create(CreateAlertRequest request) {
        CryptoQuote quote = cryptoQuoteRepository.findFirstBySymbolIgnoreCaseOrderByMarketCapRankAsc(request.symbol())
                .orElseThrow(() -> ApiException.badRequest("Unknown symbol: " + request.symbol()));
        if (request.condition().isSatisfiedBy(quote.getPrice(), request.targetPrice())) {
            throw ApiException.badRequest("Condition is already satisfied by the current price");
        }
        // Canonical casing from the matched quote, not the caller's raw input,
        // so evaluation's later case-insensitive lookups stay consistent.
        PriceAlert alert = PriceAlert.create(currentUser.userId(), quote.getSymbol(), request.condition(),
                request.targetPrice());
        alertRepository.save(alert);
        return AlertResponse.from(alert);
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> listActive() {
        return alertRepository.findByUserIdAndActiveTrue(currentUser.userId()).stream()
                .map(AlertResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> listTriggered() {
        return alertRepository.findByUserIdAndActiveFalseAndClearedAtIsNull(currentUser.userId()).stream()
                .map(AlertResponse::from).toList();
    }

    @Transactional
    public AlertResponse update(Long id, UpdateAlertRequest request) {
        PriceAlert alert = findOwnedActive(id);
        // If the alert's symbol has since left the universe there's no live
        // price to check against - the same "not evaluable" treatment
        // evaluation itself gives a vanished symbol, applied here to
        // update-time validation instead.
        Optional<CryptoQuote> quote = cryptoQuoteRepository
                .findFirstBySymbolIgnoreCaseOrderByMarketCapRankAsc(alert.getSymbol());
        if (quote.isPresent() && request.condition().isSatisfiedBy(quote.get().getPrice(), request.targetPrice())) {
            throw ApiException.badRequest("Condition is already satisfied by the current price");
        }
        alert.updateConditionAndTarget(request.condition(), request.targetPrice());
        return AlertResponse.from(alert);
    }

    @Transactional
    public void delete(Long id) {
        alertRepository.delete(findOwnedActive(id));
    }

    @Transactional
    public AlertResponse clear(Long id) {
        PriceAlert alert = alertRepository.findByIdAndUserId(id, currentUser.userId())
                .filter(a -> !a.isActive() && a.getClearedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Unknown alert: " + id));
        alert.clear();
        return AlertResponse.from(alert);
    }

    private PriceAlert findOwnedActive(Long id) {
        return alertRepository.findByIdAndUserId(id, currentUser.userId())
                .filter(PriceAlert::isActive)
                .orElseThrow(() -> ApiException.notFound("Unknown alert: " + id));
    }
}
