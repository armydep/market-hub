package com.am.market_hub.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * No {@code role} field: Spring's default Jackson config ignores unknown JSON
 * properties, so a client sending {@code "role":"ADMIN"} is silently dropped
 * rather than needing a defensive check. Registration always mints TRADER.
 */
public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8) String password) {
}
