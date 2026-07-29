package com.am.market_hub.config;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Logs completed HTTP requests with status and latency. Skips actuator and
 * springdoc paths: they're polled/fetched constantly and, once S2 adds
 * bearer tokens, a query string is not a place we want request logs to echo.
 */
@Component
public class HttpRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestLoggingFilter.class);

    private static final List<String> EXCLUDED_PATTERNS = List.of(
            "/actuator/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**");
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return EXCLUDED_PATTERNS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            String query = request.getQueryString();
            String path = request.getRequestURI() + (query == null ? "" : "?" + query);
            log.info("{} {} -> {} ({} ms) from {}",
                    request.getMethod(),
                    path,
                    response.getStatus(),
                    durationMs,
                    request.getRemoteAddr());
        }
    }
}
