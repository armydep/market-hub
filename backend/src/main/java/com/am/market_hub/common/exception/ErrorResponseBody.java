package com.am.market_hub.common.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * The one error body shape every layer of this app produces:
 * {@code {timestamp,status,error,message,details?}}. Shared by
 * {@link GlobalExceptionHandler} (exceptions thrown inside a controller) and
 * the Spring Security entry point / access-denied handler (rejections that
 * happen in the filter chain, before any controller runs, so they never reach
 * a {@code @RestControllerAdvice}) — both need the identical shape without
 * duplicating its construction.
 */
public final class ErrorResponseBody {

    private ErrorResponseBody() {
    }

    public static Map<String, Object> of(HttpStatus status, String message) {
        return of(status, message, null);
    }

    public static Map<String, Object> of(HttpStatus status, String message, Object details) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        if (details != null) {
            body.put("details", details);
        }
        return body;
    }
}
