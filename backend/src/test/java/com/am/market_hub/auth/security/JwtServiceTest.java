package com.am.market_hub.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.am.market_hub.user.domain.Role;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-must-be-at-least-32-bytes-long";

    @Test
    void issueAndParseRoundTripsTheRightClaims() {
        JwtService jwtService = new JwtService(SECRET, 60_000);

        String token = jwtService.issue(42L, "trader@example.com", Role.TRADER);
        var claims = jwtService.parse(token);

        assertThat(claims.getSubject()).isEqualTo("trader@example.com");
        assertThat(claims.get("userId", Long.class)).isEqualTo(42L);
        assertThat(claims.get("role", String.class)).isEqualTo("TRADER");
    }

    @Test
    void rejectsATokenSignedWithADifferentKey() {
        JwtService issuer = new JwtService(SECRET, 60_000);
        JwtService verifier = new JwtService("a-completely-different-secret-of-32-plus-bytes", 60_000);

        String token = issuer.issue(1L, "user@example.com", Role.TRADER);

        assertThatThrownBy(() -> verifier.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsAnExpiredToken() throws InterruptedException {
        JwtService jwtService = new JwtService(SECRET, 1);

        String token = jwtService.issue(1L, "user@example.com", Role.TRADER);
        Thread.sleep(10);

        assertThatThrownBy(() -> jwtService.parse(token)).isInstanceOf(ExpiredJwtException.class);
    }
}
