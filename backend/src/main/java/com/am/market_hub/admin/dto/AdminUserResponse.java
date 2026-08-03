package com.am.market_hub.admin.dto;

import java.time.Instant;

import com.am.market_hub.user.domain.User;

/** Deliberately never includes {@code passwordHash}. */
public record AdminUserResponse(Long id, String email, String role, boolean blocked, Instant createdAt) {

    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getId(), user.getEmail(), user.getRole().name(), user.isBlocked(), user.getCreatedAt());
    }
}
