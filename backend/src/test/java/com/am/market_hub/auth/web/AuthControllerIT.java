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
import com.am.market_hub.support.TestcontainersConfig;
import com.am.market_hub.user.domain.Role;
import com.am.market_hub.user.repository.UserRepository;

/**
 * Register/login/protected-access, end-to-end against a real Postgres. Uses
 * the real (long) JWT expiration — the expired-token case lives in its own
 * class ({@link AuthTokenExpiryIT}) with a short expiration scoped to just
 * that test, so a slow CI run here can never flake a "valid token" test by
 * outliving a short-lived one shared across the whole class.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = {
        "app.admin.email=admin@example.com",
        "app.admin.password=admin-password-123"
})
class AuthControllerIT {

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

    private AuthResponse register(String email, String password) {
        return client().post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", password))
                .retrieve().body(AuthResponse.class);
    }

    private static void assertStatus(Runnable call, int expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(HttpClientErrorException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(expected));
    }

    @Test
    void registerCreatesAnAccountAndReturnsAToken() {
        String email = "new-user-" + System.nanoTime() + "@example.com";

        AuthResponse response = register(email, "password123");

        assertThat(response.token()).isNotBlank();
        assertThat(response.userId()).isNotNull();
        assertThat(response.email()).isEqualTo(email);
        assertThat(response.role()).isEqualTo("TRADER");
    }

    @Test
    void registeringADuplicateEmailReturns409EvenWithDifferentCasing() {
        String email = "dup-" + System.nanoTime() + "@example.com";
        register(email, "password123");

        assertStatus(() -> register(email.toUpperCase(), "password123"), 409);
    }

    @Test
    void aRoleFieldSentOnRegisterIsIgnored() {
        String email = "self-elevate-" + System.nanoTime() + "@example.com";

        AuthResponse response = client().post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "password123", "role", "ADMIN"))
                .retrieve().body(AuthResponse.class);

        assertThat(response.role()).isEqualTo("TRADER");
        assertThat(userRepository.findByEmail(email).orElseThrow().getRole()).isEqualTo(Role.TRADER);
    }

    @Test
    void loginWithCorrectCredentialsReturnsAToken() {
        String email = "login-happy-" + System.nanoTime() + "@example.com";
        register(email, "password123");

        AuthResponse response = client().post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "password123"))
                .retrieve().body(AuthResponse.class);

        assertThat(response.token()).isNotBlank();
        assertThat(response.email()).isEqualTo(email);
    }

    @Test
    void loginWithWrongPasswordReturns401() {
        String email = "login-bad-pw-" + System.nanoTime() + "@example.com";
        register(email, "password123");

        assertStatus(() -> client().post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "wrong-password"))
                .retrieve().body(AuthResponse.class), 401);
    }

    @Test
    void loginWithUnknownEmailReturns401() {
        assertStatus(() -> client().post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", "nobody-" + System.nanoTime() + "@example.com", "password", "whatever1"))
                .retrieve().body(AuthResponse.class), 401);
    }

    @Test
    void protectedRouteWithNoTokenReturns401WithTheStandardEnvelope() {
        assertThatThrownBy(() -> client().get().uri("/test/protected").retrieve().body(String.class))
                .isInstanceOfSatisfying(HttpClientErrorException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(401);
                    // The new AuthenticationEntryPoint must produce the exact envelope
                    // GlobalExceptionHandler does — proof this never collapses to a
                    // bare 401 or, worse, the pre-S5 500-on-AccessDeniedException trap.
                    String body = ex.getResponseBodyAsString();
                    assertThat(body).contains("\"timestamp\"", "\"status\":401", "\"error\"", "\"message\"");
                });
    }

    @Test
    void protectedRouteWithAValidTokenReturns200() {
        String email = "protected-ok-" + System.nanoTime() + "@example.com";
        AuthResponse auth = register(email, "password123");

        String result = client().get().uri("/test/protected")
                .header("Authorization", "Bearer " + auth.token())
                .retrieve().body(String.class);

        assertThat(result).isEqualTo("ok");
    }

    @Test
    void aTamperedTokenIsRejected() {
        String email = "tampered-" + System.nanoTime() + "@example.com";
        AuthResponse auth = register(email, "password123");
        String tampered = auth.token().substring(0, auth.token().length() - 4) + "abcd";

        assertStatus(() -> client().get().uri("/test/protected")
                .header("Authorization", "Bearer " + tampered)
                .retrieve().body(String.class), 401);
    }

    @Test
    void aTraderTokenCannotReachAnAdminOnlyEndpoint() {
        String email = "trader-not-admin-" + System.nanoTime() + "@example.com";
        AuthResponse auth = register(email, "password123");

        assertThatThrownBy(() -> client().get().uri("/admin/users")
                        .header("Authorization", "Bearer " + auth.token())
                        .retrieve().body(String.class))
                .isInstanceOfSatisfying(HttpClientErrorException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(403);
                    // Same envelope-shape proof as the 401 test above: the
                    // AccessDeniedException handler must produce the standard
                    // body, not just the right status code.
                    String body = ex.getResponseBodyAsString();
                    assertThat(body).contains("\"timestamp\"", "\"status\":403", "\"error\"", "\"message\"");
                });
    }

    @Test
    void theSeededAdminCanSignInAndReachAnAdminOnlyEndpoint() {
        AuthResponse admin = client().post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", "admin@example.com", "password", "admin-password-123"))
                .retrieve().body(AuthResponse.class);

        assertThat(admin.role()).isEqualTo("ADMIN");

        String result = client().get().uri("/admin/users")
                .header("Authorization", "Bearer " + admin.token())
                .retrieve().body(String.class);

        assertThat(result).contains("\"content\"");
    }
}
