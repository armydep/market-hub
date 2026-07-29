package com.am.market_hub.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Same-package test so it can call the protected {@code shouldNotFilter}
 * override directly, without a Spring context.
 */
class HttpRequestLoggingFilterTest {

    private final HttpRequestLoggingFilter filter = new HttpRequestLoggingFilter();

    @Test
    void skipsActuatorAndSwaggerPaths() {
        assertThat(shouldNotFilter("/api/actuator/health")).isTrue();
        assertThat(shouldNotFilter("/api/swagger-ui.html")).isTrue();
        assertThat(shouldNotFilter("/api/swagger-ui/index.css")).isTrue();
        assertThat(shouldNotFilter("/api/v3/api-docs")).isTrue();
    }

    @Test
    void logsApplicationPaths() {
        assertThat(shouldNotFilter("/api/market/coins")).isFalse();
        assertThat(shouldNotFilter("/api/auth/login")).isFalse();
    }

    private boolean shouldNotFilter(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/api");
        request.setRequestURI(uri);
        return filter.shouldNotFilter(request);
    }
}
