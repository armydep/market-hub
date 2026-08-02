package com.am.market_hub.auth.dto;

public record AuthResponse(String token, Long userId, String email, String role) {
}
