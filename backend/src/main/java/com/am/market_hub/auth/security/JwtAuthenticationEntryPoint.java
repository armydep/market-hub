package com.am.market_hub.auth.security;

import java.io.IOException;

import com.am.market_hub.common.exception.ErrorResponseBody;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * Handles an unauthenticated request to a protected endpoint. Spring
 * Security rejects this in the filter chain, before any controller runs, so
 * {@link com.am.market_hub.common.exception.GlobalExceptionHandler}'s
 * {@code @RestControllerAdvice} never sees it — this is the equivalent entry
 * point for that layer, producing the identical JSON body.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                ErrorResponseBody.of(HttpStatus.UNAUTHORIZED, "Authentication required"));
    }
}
