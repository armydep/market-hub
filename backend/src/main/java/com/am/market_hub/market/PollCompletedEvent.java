package com.am.market_hub.market;

/**
 * Published after a successful poll cycle upserts the universe. The seam for the
 * post-poll alert-evaluation hook introduced in S4; no-op until then.
 *
 * @param coinCount number of coins in the refreshed universe
 */
public record PollCompletedEvent(int coinCount) {
}
