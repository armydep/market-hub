package com.am.market_hub.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Email is the only editable account field in Phase 1 (OQ-006, resolved in
 * docs/slices/08-account-management.md). {@code currentPassword} is the
 * required security check: a bearer token alone must not be enough to change
 * the identity S7's password-reset flow delivers to.
 */
public record UpdateAccountRequest(
        @Email @NotBlank String email,
        @NotBlank String currentPassword) {
}
