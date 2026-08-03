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

import com.am.market_hub.support.RecordingEmailSenderConfig;
import com.am.market_hub.support.TestcontainersConfig;

/**
 * The expired-token case, isolated in its own short-token-lifetime context so
 * a slow run can never make it flake a *different* test that expects a token
 * to still be valid — see {@code PasswordResetControllerIT}'s note. Same
 * reasoning that split {@code AuthTokenExpiryIT} out of {@code AuthControllerIT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfig.class, RecordingEmailSenderConfig.class})
@TestPropertySource(properties = "app.auth.password-reset-token-lifetime-minutes=0.01")
class PasswordResetTokenExpiryIT {

    @LocalServerPort
    private int port;
    @Autowired
    private RecordingEmailSenderConfig.RecordingEmailSender recordingEmailSender;

    private RestClient client() {
        return RestClient.create("http://localhost:" + port + "/api");
    }

    @Test
    void anExpiredTokenIsRejected() throws InterruptedException {
        String email = "reset-expiry-" + System.nanoTime() + "@example.com";
        client().post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "password123"))
                .retrieve().body(String.class);

        client().post().uri("/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email))
                .retrieve().body(String.class);
        String token = recordingEmailSender.lastToken();

        Thread.sleep(1000); // past the ~600ms token lifetime

        assertThatThrownBy(() -> client().post().uri("/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("token", token, "newPassword", "new-password1"))
                        .retrieve().body(String.class))
                .isInstanceOfSatisfying(HttpClientErrorException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }
}
