package com.am.market_hub.auth.security;

import java.io.IOException;

import com.am.market_hub.common.exception.ErrorResponseBody;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * Handles an authenticated-but-forbidden request. Same rationale as
 * {@link JwtAuthenticationEntryPoint}: this runs in the filter chain, before
 * any controller, so it can't go through {@code GlobalExceptionHandler}.
 */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JwtAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                ErrorResponseBody.of(HttpStatus.FORBIDDEN, "Access denied"));
    }
}
