package com.am.market_hub.market.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.am.market_hub.common.exception.ApiException;
import com.am.market_hub.market.domain.CoinColumn;
import com.am.market_hub.market.dto.CoinPageResponse;
import com.am.market_hub.market.dto.CoinResponse;
import com.am.market_hub.market.dto.ColumnCatalogResponse;
import com.am.market_hub.market.repository.CryptoQuoteRepository;

@Service
public class MarketService {

    private static final String DEFAULT_SORT = CoinColumn.MARKET_CAP_RANK.key();
    /** Matches every row, so the no-search case shares the search query path. */
    private static final String MATCH_ALL = "%";

    private final CryptoQuoteRepository repository;
    private final int defaultPageSize;
    private final Set<Integer> supportedPageSizes;
    private final List<String> defaultVisibleColumns;

    public MarketService(CryptoQuoteRepository repository,
                         @Value("${app.market.default-page-size}") int defaultPageSize,
                         @Value("${app.market.supported-page-sizes}") List<Integer> supportedPageSizes,
                         @Value("${app.market.default-visible-columns}") List<String> defaultVisibleColumns) {
        this.repository = repository;
        this.defaultPageSize = defaultPageSize;
        this.supportedPageSizes = new LinkedHashSet<>(supportedPageSizes);
        this.defaultVisibleColumns = List.copyOf(defaultVisibleColumns);
        validateConfiguration();
    }

    /**
     * Fail fast at startup rather than serving a catalog the client can't use:
     * a typo in the configured column list would otherwise surface as an
     * unexplained empty grid much later.
     */
    private void validateConfiguration() {
        List<String> unknown = defaultVisibleColumns.stream()
                .filter(key -> CoinColumn.byKey(key).isEmpty())
                .toList();
        if (!unknown.isEmpty()) {
            throw new IllegalStateException(
                    "app.market.default-visible-columns contains unknown column key(s): " + unknown
                            + "; supported: " + CoinColumn.keys());
        }
        if (supportedPageSizes.isEmpty()) {
            throw new IllegalStateException("app.market.supported-page-sizes must not be empty");
        }
        if (!supportedPageSizes.contains(defaultPageSize)) {
            throw new IllegalStateException("app.market.default-page-size (" + defaultPageSize
                    + ") must be one of app.market.supported-page-sizes " + supportedPageSizes);
        }
    }

    /**
     * Paginated, sorted, optionally-searched view of the cached universe.
     * Search and sort are pushed into the query so they apply to the complete
     * matching dataset before the page is cut (F001-FR-011).
     */
    @Transactional(readOnly = true)
    public CoinPageResponse list(String sort, String order, Integer page, Integer size, String q) {
        Pageable pageable = PageRequest.of(resolvePage(page), resolveSize(size), resolveSort(sort, order));
        return CoinPageResponse.from(
                repository.search(toLikePattern(q), pageable),
                repository.findLastUpdatedAt());
    }

    @Transactional(readOnly = true)
    public CoinResponse getBySymbol(String symbol) {
        return repository.findFirstBySymbolIgnoreCaseOrderByMarketCapRankAsc(symbol)
                .map(CoinResponse::from)
                .orElseThrow(() -> ApiException.notFound("Unknown symbol: " + symbol));
    }

    @Transactional(readOnly = true)
    public ColumnCatalogResponse columnCatalog() {
        return new ColumnCatalogResponse(
                List.copyOf(CoinColumn.keys()),
                defaultVisibleColumns,
                List.copyOf(supportedPageSizes),
                defaultPageSize);
    }

    private int resolvePage(Integer page) {
        if (page == null) {
            return 0;
        }
        if (page < 0) {
            throw ApiException.badRequest("Page must not be negative: " + page);
        }
        return page;
    }

    private int resolveSize(Integer size) {
        if (size == null) {
            return defaultPageSize;
        }
        if (!supportedPageSizes.contains(size)) {
            throw ApiException.badRequest(
                    "Unsupported page size: " + size + " (supported: " + supportedPageSizes + ")");
        }
        return size;
    }

    private Sort resolveSort(String sort, String order) {
        String field = StringUtils.hasText(sort) ? sort : DEFAULT_SORT;
        CoinColumn.byKey(field)
                .orElseThrow(() -> ApiException.badRequest("Unknown sort field: " + field));
        // Postgres's default null placement flips with direction (NULLS LAST on
        // ASC, NULLS FIRST on DESC); pin nulls last regardless so a coin missing
        // a value never jumps to the top of a descending sort.
        return Sort.by(new Sort.Order(parseDirection(order), field).nullsLast());
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

    /**
     * Build the LIKE pattern for a raw search term. The term is lowercased to
     * match the query's {@code lower(...)} comparison, and LIKE metacharacters
     * are escaped so a user typing {@code %} or {@code _} searches for that
     * character instead of injecting a wildcard.
     */
    private static String toLikePattern(String q) {
        if (!StringUtils.hasText(q)) {
            return MATCH_ALL;
        }
        String escaped = q.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
