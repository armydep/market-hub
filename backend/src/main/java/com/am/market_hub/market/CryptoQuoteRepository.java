package com.am.market_hub.market;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CryptoQuoteRepository extends JpaRepository<CryptoQuote, Integer> {

    Optional<CryptoQuote> findBySymbolIgnoreCase(String symbol);

    List<CryptoQuote> findAllBy(Sort sort);

    /** Drop coins that are no longer in the freshly-fetched universe. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CryptoQuote q where q.cmcId not in :cmcIds")
    int deleteByCmcIdNotIn(Collection<Integer> cmcIds);
}
