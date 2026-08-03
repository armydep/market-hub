package com.am.market_hub.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.am.market_hub.auth.dto.PasswordResetResponse;
import com.am.market_hub.support.RecordingEmailSenderConfig;
import com.am.market_hub.support.TestcontainersConfig;

/**
 * Password reset request/confirm, end-to-end against a real Postgres. Mirrors
 * {@code AuthControllerIT}/{@code AccountLockoutIT}'s style. The expired-token
 * case lives in its own class ({@link PasswordResetTokenExpiryIT}) with a
 * short token lifetime, same reasoning as {@code AuthTokenExpiryIT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfig.class, RecordingEmailSenderConfig.class})
@TestPropertySource(properties = "app.auth.max-failed-attempts=3")
class PasswordResetControllerIT {

    @LocalServerPort
    private int port;
    @Autowired
    private RecordingEmailSenderConfig.RecordingEmailSender recordingEmailSender;

    private RestClient client;

    private RestClient client() {
        if (client == null) {
            client = RestClient.create("http://localhost:" + port + "/api");
        }
        return client;
    }

    private void register(String email, String password) {
        client().post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", password))
                .retrieve().body(AuthResponse.class);
    }

    private PasswordResetResponse requestReset(String email) {
        return client().post().uri("/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email))
                .retrieve().body(PasswordResetResponse.class);
    }

    private PasswordResetResponse confirmReset(String token, String newPassword) {
        return client().post().uri("/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("token", token, "newPassword", newPassword))
                .retrieve().body(PasswordResetResponse.class);
    }

    private AuthResponse login(String email, String password) {
        return client().post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", password))
                .retrieve().body(AuthResponse.class);
    }

    private void assertStatus(Runnable call, int expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(HttpClientErrorException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(expected));
    }

    @Test
    void happyPathRequestConfirmAndSignInWithTheNewPassword() {
        String email = "reset-happy-" + System.nanoTime() + "@example.com";
        register(email, "old-password1");

        requestReset(email);
        String token = recordingEmailSender.lastToken();
        assertThat(token).isNotBlank();

        PasswordResetResponse confirmResponse = confirmReset(token, "new-password1");
        assertThat(confirmResponse.message()).contains("updated");

        AuthResponse loggedIn = login(email, "new-password1");
        assertThat(loggedIn.token()).isNotBlank();

        assertStatus(() -> login(email, "old-password1"), 401);
    }

    @Test
    void requestForAnUnknownEmailReturnsTheSameResponseAsAKnownOne() {
        String knownEmail = "reset-known-" + System.nanoTime() + "@example.com";
        register(knownEmail, "password123");

        PasswordResetResponse knownResponse = requestReset(knownEmail);
        PasswordResetResponse unknownResponse = requestReset("nobody-" + System.nanoTime() + "@example.com");

        assertThat(unknownResponse.message()).isEqualTo(knownResponse.message());
    }

    @Test
    void confirmWithAGarbageTokenReturns400() {
        assertStatus(() -> confirmReset("not-a-real-token", "new-password1"), 400);
    }

    @Test
    void confirmWithAnAlreadyUsedTokenReturns400() {
        String email = "reset-reuse-" + System.nanoTime() + "@example.com";
        register(email, "password123");
        requestReset(email);
        String token = recordingEmailSender.lastToken();

        confirmReset(token, "new-password1");

        assertStatus(() -> confirmReset(token, "another-password1"), 400);
    }

    @Test
    void aSecondRequestInvalidatesTheFirstToken() {
        String email = "reset-supersede-" + System.nanoTime() + "@example.com";
        register(email, "password123");

        requestReset(email);
        String firstToken = recordingEmailSender.lastToken();
        requestReset(email);
        String secondToken = recordingEmailSender.lastToken();
        assertThat(secondToken).isNotEqualTo(firstToken);

        assertStatus(() -> confirmReset(firstToken, "new-password1"), 400);

        PasswordResetResponse response = confirmReset(secondToken, "new-password1");
        assertThat(response.message()).contains("updated");
    }

    @Test
    void aSuccessfulResetClearsAnExistingLockout() {
        String email = "reset-clears-lock-" + System.nanoTime() + "@example.com";
        register(email, "password123");

        assertStatus(() -> login(email, "wrong-password"), 401);
        assertStatus(() -> login(email, "wrong-password"), 401);
        assertStatus(() -> login(email, "wrong-password"), 401);
        // Locked: even the correct old password is now rejected as temporarily locked.
        assertStatus(() -> login(email, "password123"), 403);

        requestReset(email);
        String token = recordingEmailSender.lastToken();
        confirmReset(token, "new-password1");

        // If the lock hadn't cleared, this would still be 403 "temporarily locked" instead of succeeding.
        AuthResponse loggedIn = login(email, "new-password1");
        assertThat(loggedIn.token()).isNotBlank();
    }
}
