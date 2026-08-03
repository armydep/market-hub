package com.am.market_hub.alert.repository;

import java.util.List;
import java.util.Optional;

import com.am.market_hub.alert.domain.PriceAlert;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<PriceAlert, Long> {

    List<PriceAlert> findByUserIdAndActiveTrue(Long userId);

    List<PriceAlert> findByUserIdAndActiveFalseAndClearedAtIsNull(Long userId);

    /** Ownership-scoped lookup for update/delete/clear; empty means "not found or not yours". */
    Optional<PriceAlert> findByIdAndUserId(Long id, Long userId);

    /** Across all users — the evaluator's own read, not ownership-scoped. */
    List<PriceAlert> findByActiveTrue();
}
