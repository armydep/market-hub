package com.am.market_hub.auth.email;

/**
 * Single seam over transactional email, mirroring {@code PriceProvider}'s
 * role for market data. Password reset only — never a market-alert channel
 * (constraints.md). The concrete provider is deliberately undecided
 * (PRD OQ-007) and must not leak into business logic.
 */
public interface EmailSender {

    void sendPasswordResetEmail(String toEmail, String rawToken);
}
