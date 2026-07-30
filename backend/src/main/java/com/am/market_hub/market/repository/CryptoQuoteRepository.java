package com.am.market_hub.market.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.am.market_hub.market.domain.CryptoQuote;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

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
     * Drop coins that are no longer in the freshly-fetched universe. Relies on
     * the caller (CryptoPoller's upsert transaction) providing the active
     * transaction this modifying query requires.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CryptoQuote q where q.cmcId not in :cmcIds")
    int deleteByCmcIdNotIn(Collection<Integer> cmcIds);
}
