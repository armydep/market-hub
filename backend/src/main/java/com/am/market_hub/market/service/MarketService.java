package com.am.market_hub.market.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.am.market_hub.common.exception.ApiException;
import com.am.market_hub.market.domain.CoinColumn;
import com.am.market_hub.market.dto.CoinResponse;
import com.am.market_hub.market.repository.CryptoQuoteRepository;

@Service
public class MarketService {

    private static final String DEFAULT_SORT = CoinColumn.MARKET_CAP_RANK.key();

    private final CryptoQuoteRepository repository;

    public MarketService(CryptoQuoteRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CoinResponse> list(String sort, String order) {
        String field = StringUtils.hasText(sort) ? sort : DEFAULT_SORT;
        CoinColumn.byKey(field)
                .orElseThrow(() -> ApiException.badRequest("Unknown sort field: " + field));
        Sort.Direction direction = parseDirection(order);
        // Postgres's default null placement flips with direction (NULLS LAST on
        // ASC, NULLS FIRST on DESC); pin nulls last regardless so a coin missing
        // a value never jumps to the top of a descending sort.
        Sort resolvedSort = Sort.by(new Sort.Order(direction, field).nullsLast());
        return repository.findAllBy(resolvedSort).stream()
                .map(CoinResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CoinResponse getBySymbol(String symbol) {
        return repository.findBySymbolIgnoreCase(symbol)
                .map(CoinResponse::from)
                .orElseThrow(() -> ApiException.notFound("Unknown symbol: " + symbol));
    }

    private Sort.Direction parseDirection(String order) {
        if (!StringUtils.hasText(order)) {
            return Sort.Direction.ASC;
        }
        try {
            return Sort.Direction.fromString(order);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Invalid sort order: " + order + " (use asc or desc)");
        }
    }
}
