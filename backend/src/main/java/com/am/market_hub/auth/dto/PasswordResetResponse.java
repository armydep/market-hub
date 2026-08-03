package com.am.market_hub.auth.dto;

/**
 * A real (small) body rather than void/204: {@code request}'s message is
 * deliberately identical whether or not the email matches an account (the
 * no-enumeration guarantee), and giving {@code confirm} a body too keeps
 * both endpoints uniform for the client.
 */
public record PasswordResetResponse(String message) {
}
