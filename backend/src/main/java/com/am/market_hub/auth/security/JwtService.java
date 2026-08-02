package com.am.market_hub.auth.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import com.am.market_hub.user.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

/**
 * Issues and parses JWTs. Role travels as a claim so authorization is
 * stateless (constraints.md) — no per-request role lookup. Takes plain
 * primitives, not a {@code User} entity, so it has no dependency on the
 * {@code user} package and is trivially unit-testable without persistence.
 *
 * <p>Parse failures (expired, tampered, malformed) are left to propagate as
 * the {@code io.jsonwebtoken} exceptions they already are; {@link JwtAuthFilter}
 * decides what to do with them, this class does no error handling of its own.
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String issue(Long userId, String email, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key)
                .compact();
    }

    /** Throws an {@code io.jsonwebtoken} exception (expired/malformed/tampered) on any invalid token. */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
