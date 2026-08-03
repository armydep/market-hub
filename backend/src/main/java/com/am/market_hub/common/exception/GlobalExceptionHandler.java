package com.am.market_hub.common.exception;

import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Single advice mapping {@link ApiException} and validation failures to a consistent JSON body
 * {@code {timestamp,status,error,message,details?}}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException ex) {
        return build(ex.getStatus(), ex.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField,
                        fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(),
                        (a, b) -> a));
        return build(HttpStatus.BAD_REQUEST, "Validation failed", fields);
    }

    /**
     * {@code @PreAuthorize} throws this from inside handler invocation — i.e.
     * inside {@code DispatcherServlet}'s own exception resolution, which runs
     * (and fully handles it) before it could ever reach Spring Security's
     * {@code ExceptionTranslationFilter}/{@code JwtAccessDeniedHandler}.
     * Without this handler the catch-all below collapses it to 500, since
     * {@code AccessDeniedException} (and its {@code AuthorizationDeniedException}
     * subtype) doesn't implement {@link ErrorResponse}.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "Access denied", null);
    }

    /**
     * Safety net for a unique-constraint race the caller's own pre-check can't
     * always catch: a JPA update on an already-managed entity (unlike a brand-new
     * {@code IDENTITY} insert) defers its flush to transaction commit, which
     * happens after a local {@code try/catch} in the service method has already
     * gone out of scope — see {@code AccountService.updateAccount}. Without this
     * handler that race falls through to the generic 500 below instead of a
     * clean 409.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return build(HttpStatus.CONFLICT, "The request conflicts with existing data", null);
    }

    /**
     * Catch-all. Spring MVC's own exceptions (unknown route, wrong HTTP method,
     * unreadable body, ...) implement {@link ErrorResponse} and carry a real
     * status code; honor it instead of collapsing every unmapped exception to
     * 500. Anything that isn't an {@link ErrorResponse} — or resolves to a
     * 5xx — is logged, since it represents a genuinely unexpected failure.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatus resolved = HttpStatus.resolve(errorResponse.getStatusCode().value());
            if (resolved != null) {
                status = resolved;
            }
        }
        if (status.is5xxServerError()) {
            log.error("Unhandled exception", ex);
        }
        return build(status, status.getReasonPhrase(), null);
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message, Object details) {
        return ResponseEntity.status(status).body(ErrorResponseBody.of(status, message, details));
    }
}
