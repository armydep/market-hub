package com.am.market_hub.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.am.market_hub.auth.dto.AuthResponse;
import com.am.market_hub.support.TestcontainersConfig;

/**
 * The expired-token case, isolated in its own short-expiration Spring
 * context so a slow run can never make it flake a *different* test that
 * expects a token to still be valid (see AuthControllerIT's note). Only one
 * test needs this property, so only one test pays for a separate context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = "app.jwt.expiration-ms=200")
class AuthTokenExpiryIT {

    @LocalServerPort
    private int port;

    private RestClient client() {
        return RestClient.create("http://localhost:" + port + "/api");
    }

    @Test
    void anExpiredTokenIsRejected() throws InterruptedException {
        AuthResponse auth = client().post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", "expiry-" + System.nanoTime() + "@example.com", "password", "password123"))
                .retrieve().body(AuthResponse.class);

        Thread.sleep(500);

        assertThatThrownBy(() -> client().get().uri("/test/protected")
                        .header("Authorization", "Bearer " + auth.token())
                        .retrieve().body(String.class))
                .isInstanceOfSatisfying(HttpClientErrorException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(401));
    }
}
