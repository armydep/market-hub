package com.am.market_hub.market.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.am.market_hub.market.domain.CryptoQuote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CryptoQuoteRepository extends JpaRepository<CryptoQuote, Integer> {

    /**
     * Symbol is not enforced unique at the DB level (only indexed) - CMC can
     * return duplicate tickers once the tracked universe grows past the very
     * top of the market. Pick the highest-ranked match deterministically
     * rather than risk IncorrectResultSizeDataAccessException on a plain
     * findBy that assumes at most one row.
     */
    Optional<CryptoQuote> findFirstBySymbolIgnoreCaseOrderByMarketCapRankAsc(String symbol);

    List<CryptoQuote> findAllBy(Sort sort);

    /**
     * Paginated substring search over name OR symbol (F-002). Filtering, sorting
     * and paging all happen here, in the query, so they apply to the complete
     * matching dataset rather than to an already-fetched slice (F001-FR-011).
     *
     * <p>Callers pass an already-lowercased, already-escaped pattern; {@code %}
     * matches everything, which is how the no-search case stays on this single
     * code path. The explicit ESCAPE clause is what keeps {@code %} and
     * {@code _} typed by a user literal instead of wildcards.
     */
    @Query("""
            select q from CryptoQuote q
            where lower(q.symbol) like :pattern escape '\\'
               or lower(q.name) like :pattern escape '\\'
            """)
    Page<CryptoQuote> search(@Param("pattern") String pattern, Pageable pageable);

    /**
     * Time of the last successful poll (F001-FR-019): every successful cycle
     * rewrites every row, so the newest {@code updatedAt} is exactly that.
     * Null when the universe is empty.
     */
    @Query("select max(q.updatedAt) from CryptoQuote q")
    Instant findLastUpdatedAt();

    /**
     * Drop coins that are no longer in the freshly-fetched universe. Relies on
     * the caller (CryptoPoller's upsert transaction) providing the active
     * transaction this modifying query requires.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CryptoQuote q where q.cmcId not in :cmcIds")
    int deleteByCmcIdNotIn(Collection<Integer> cmcIds);
}
