package com.am.market_hub.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.am.market_hub.auth.dto.AuthResponse;
import com.am.market_hub.support.TestcontainersConfig;
import com.am.market_hub.user.domain.User;
import com.am.market_hub.user.repository.UserRepository;

/**
 * Consecutive-failed-attempt lockout and administrative-block enforcement
 * (PRD F-004 FR-006/007/008/009), against a real Postgres. The threshold is
 * lowered to 3 here purely to keep the failure sequences short — the default
 * (5) is exercised nowhere else, so this doesn't weaken coverage of the real
 * configured value.
 *
 * <p>The elapsed-lock / lazy-expiry case lives in its own class
 * ({@link AccountLockoutExpiryIT}) with a short lockout duration, for the
 * same flakiness reason {@code AuthTokenExpiryIT} was split out of
 * {@code AuthControllerIT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = "app.auth.max-failed-attempts=3")
class AccountLockoutIT {

    private static final String PASSWORD = "password123";

    @LocalServerPort
    private int port;
    @Autowired
    private UserRepository userRepository;

    private RestClient client;

    private RestClient client() {
        if (client == null) {
            client = RestClient.create("http://localhost:" + port + "/api");
        }
        return client;
    }

    private AuthResponse register(String email) {
        return client().post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", PASSWORD))
                .retrieve().body(AuthResponse.class);
    }

    private void attemptLogin(String email, String password) {
        client().post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", password))
                .retrieve().body(AuthResponse.class);
    }

    private void expectStatusAndMessage(Runnable call, int expectedStatus, String expectedMessageFragment) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(HttpClientErrorException.class, ex -> {
            assertThat(ex.getStatusCode().value()).isEqualTo(expectedStatus);
            assertThat(ex.getResponseBodyAsString()).contains(expectedMessageFragment);
        });
    }

    @Test
    void failuresBelowTheThresholdDoNotLockTheAccount() {
        String email = "below-threshold-" + System.nanoTime() + "@example.com";
        register(email);

        expectStatusAndMessage(() -> attemptLogin(email, "wrong-password"), 401, "Invalid email or password");
        expectStatusAndMessage(() -> attemptLogin(email, "wrong-password"), 401, "Invalid email or password");

        AuthResponse response = client().post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", PASSWORD))
                .retrieve().body(AuthResponse.class);
        assertThat(response.token()).isNotBlank();
    }

    @Test
    void theThresholdFailureLocksTheAccountAndRejectsEvenTheCorrectPasswordNext() {
        String email = "at-threshold-" + System.nanoTime() + "@example.com";
        register(email);

        expectStatusAndMessage(() -> attemptLogin(email, "wrong-password"), 401, "Invalid email or password");
        expectStatusAndMessage(() -> attemptLogin(email, "wrong-password"), 401, "Invalid email or password");
        expectStatusAndMessage(() -> attemptLogin(email, "wrong-password"), 401, "Invalid email or password");

        expectStatusAndMessage(() -> attemptLogin(email, PASSWORD), 403, "temporarily locked");
    }

    @Test
    void aSuccessfulLoginMidSequenceResetsTheCounter() {
        String email = "mid-sequence-reset-" + System.nanoTime() + "@example.com";
        register(email);

        expectStatusAndMessage(() -> attemptLogin(email, "wrong-password"), 401, "Invalid email or password");
        attemptLogin(email, PASSWORD); // successful login resets the counter to 0
        expectStatusAndMessage(() -> attemptLogin(email, "wrong-password"), 401, "Invalid email or password");
        expectStatusAndMessage(() -> attemptLogin(email, "wrong-password"), 401, "Invalid email or password");

        // Two failures since the reset is still below the threshold of three.
        AuthResponse response = client().post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", PASSWORD))
                .retrieve().body(AuthResponse.class);
        assertThat(response.token()).isNotBlank();
    }

    @Test
    void aBlockedAccountIsRejectedAtLoginWithADistinctMessage() {
        String email = "blocked-login-" + System.nanoTime() + "@example.com";
        register(email);
        blockDirectly(email);

        expectStatusAndMessage(() -> attemptLogin(email, PASSWORD), 403, "Account is blocked");
    }

    @Test
    void aBlockedAccountWithAPreIssuedTokenIsRejectedOnAProtectedRoute() {
        String email = "blocked-mid-session-" + System.nanoTime() + "@example.com";
        AuthResponse auth = register(email);

        blockDirectly(email);

        assertThatThrownBy(() -> client().get().uri("/test/protected")
                        .header("Authorization", "Bearer " + auth.token())
                        .retrieve().body(String.class))
                .isInstanceOfSatisfying(HttpClientErrorException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(401));
    }

    @Test
    void lockingViaFailedAttemptsNeverSetsBlocked() {
        String email = "lock-not-block-" + System.nanoTime() + "@example.com";
        register(email);

        expectStatusAndMessage(() -> attemptLogin(email, "wrong-password"), 401, "Invalid email or password");
        expectStatusAndMessage(() -> attemptLogin(email, "wrong-password"), 401, "Invalid email or password");
        expectStatusAndMessage(() -> attemptLogin(email, "wrong-password"), 401, "Invalid email or password");

        User locked = userRepository.findByEmail(email).orElseThrow();
        assertThat(locked.isBlocked()).isFalse();
        assertThat(locked.getLockedUntil()).isAfter(Instant.now());
    }

    @Test
    void flippingBlockedBackToFalseDoesNotClearAnActiveLock() {
        String email = "unblock-keeps-lock-" + System.nanoTime() + "@example.com";
        register(email);

        expectStatusAndMessage(() -> attemptLogin(email, "wrong-password"), 401, "Invalid email or password");
        expectStatusAndMessage(() -> attemptLogin(email, "wrong-password"), 401, "Invalid email or password");
        expectStatusAndMessage(() -> attemptLogin(email, "wrong-password"), 401, "Invalid email or password");

        blockDirectly(email);
        unblockDirectly(email);

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.getLockedUntil()).isAfter(Instant.now());
        expectStatusAndMessage(() -> attemptLogin(email, PASSWORD), 403, "temporarily locked");
    }

    private void blockDirectly(String email) {
        setBlocked(email, true);
    }

    private void unblockDirectly(String email) {
        setBlocked(email, false);
    }

    /**
     * Simulates the S11 admin block/unblock action, which doesn't exist yet —
     * writes directly through the repository, per the S6 spec's explicit
     * "out of scope" note.
     */
    private void setBlocked(String email, boolean blocked) {
        User user = userRepository.findByEmail(email).orElseThrow();
        if (blocked) {
            user.block();
        } else {
            user.unblock();
        }
        userRepository.save(user);
    }
}
