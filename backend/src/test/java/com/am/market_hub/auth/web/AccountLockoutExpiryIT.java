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
 * The lazy-expiry case, isolated in its own short-lockout-duration context so
 * a slow run can never make it flake a *different* test that expects a lock
 * to still be active — see {@link AccountLockoutIT}'s note. Same reasoning
 * that split {@code AuthTokenExpiryIT} out of {@code AuthControllerIT} in S5.
 *
 * <p>The threshold here is 2, not 1: with a threshold of 1, a single failure
 * always re-locks regardless of whether the counter reset to 0 or stayed at
 * the threshold on expiry, so it can't actually distinguish the two designs.
 * With threshold 2, one failure after expiry is only safe (account stays
 * unlocked) if the counter genuinely reset — otherwise it would immediately
 * exceed the threshold again. That's the real proof of the S6 spec's
 * resolved open question 3 (lazy expiry is a fresh start, not "still
 * guilty").
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = {
        "app.auth.max-failed-attempts=2",
        "app.auth.lockout-duration-minutes=0.01"
})
class AccountLockoutExpiryIT {

    @LocalServerPort
    private int port;

    private RestClient client;

    private RestClient client() {
        if (client == null) {
            client = RestClient.create("http://localhost:" + port + "/api");
        }
        return client;
    }

    private void attemptLogin(String email, String password) {
        client().post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", password))
                .retrieve().body(AuthResponse.class);
    }

    private void expect401(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(HttpClientErrorException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(401));
    }

    @Test
    void anExpiredLockIsEvaluatedFreshRatherThanImmediatelyReLocking() throws InterruptedException {
        String email = "expiry-" + System.nanoTime() + "@example.com";
        client().post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "password123"))
                .retrieve().body(AuthResponse.class);

        // Two failures reach the threshold and lock the account.
        expect401(() -> attemptLogin(email, "wrong-password"));
        expect401(() -> attemptLogin(email, "wrong-password"));

        assertThatThrownBy(() -> attemptLogin(email, "password123"))
                .isInstanceOfSatisfying(HttpClientErrorException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(403);
                    assertThat(ex.getResponseBodyAsString()).contains("temporarily locked");
                });

        Thread.sleep(1000); // past the ~600ms lockout duration

        // A single failure right after expiry must NOT immediately re-lock —
        // if the counter had stayed at the threshold instead of resetting,
        // this one failure alone would push it straight back over the limit.
        expect401(() -> attemptLogin(email, "still-wrong"));

        AuthResponse response = client().post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "password123"))
                .retrieve().body(AuthResponse.class);
        assertThat(response.token()).isNotBlank();
    }
}
