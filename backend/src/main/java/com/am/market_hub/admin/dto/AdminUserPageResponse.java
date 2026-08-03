package com.am.market_hub.admin.dto;

import java.util.List;

import com.am.market_hub.user.domain.User;
import org.springframework.data.domain.Page;

/** Page envelope for the admin user list, mirroring {@code CoinPageResponse}'s shape. */
public record AdminUserPageResponse(
        List<AdminUserResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static AdminUserPageResponse from(Page<User> page) {
        return new AdminUserPageResponse(
                page.getContent().stream().map(AdminUserResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
