package com.am.market_hub.common;

import java.util.List;

/**
 * Actuator/springdoc paths that are both unauthenticated ({@code SecurityConfig})
 * and excluded from request logging ({@code HttpRequestLoggingFilter}) — one
 * list, so the two can't silently drift apart when a path is added.
 */
public final class PublicApiPaths {

    public static final List<String> OPERATIONAL = List.of(
            "/actuator/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**");

    private PublicApiPaths() {
    }
}
