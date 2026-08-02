package com.am.market_hub.auth.security;

/** The authenticated identity carried by the JWT, once parsed and verified. */
public record AuthenticatedPrincipal(Long userId, String email) {
}
