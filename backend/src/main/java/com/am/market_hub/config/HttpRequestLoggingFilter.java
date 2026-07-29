package com.am.market_hub.config;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Logs completed HTTP requests with status and latency. */
@Component
public class HttpRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestLoggingFilter.class);

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
