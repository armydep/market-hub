package com.am.market_hub.auth.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The authenticated caller's id, for ownership-scoped operations. Introduced
 * now so later slices (S9's alerts, S10's notifications, S8's account) don't
 * each reinvent it; no caller in S5 itself.
 */
@Component
public class CurrentUser {

    public Long userId() {
        var principal = (AuthenticatedPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return principal.userId();
    }
}
