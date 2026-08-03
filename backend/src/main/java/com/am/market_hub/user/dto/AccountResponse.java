package com.am.market_hub.user.dto;

import java.time.Instant;

import com.am.market_hub.user.domain.User;

/** Role, block status, and audit fields are never user-editable — this view exposes them read-only. */
public record AccountResponse(Long id, String email, String role, Instant createdAt) {

    public static AccountResponse from(User user) {
        return new AccountResponse(user.getId(), user.getEmail(), user.getRole().name(), user.getCreatedAt());
    }
}
