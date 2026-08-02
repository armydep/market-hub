package com.am.market_hub.common.exception;

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
import com.am.market_hub.support.StubProviderConfig;
import com.am.market_hub.support.TestcontainersConfig;

/**
 * Spring MVC's own exceptions (unknown route, wrong HTTP method) must keep
 * their real status code instead of being swallowed by the catch-all
 * {@code @ExceptionHandler(Exception.class)} into a 500.
 *
 * <p>Since S5, every request needs to clear Spring Security's authorization
 * check before it ever reaches {@code DispatcherServlet} — an anonymous
 * request to any of these paths now correctly gets 401 from
 * {@code JwtAuthenticationEntryPoint} before MVC can determine "no handler"
 * or "wrong method". These tests are about MVC's own exception mapping, not
 * the auth layer, so they authenticate first to reach the code path they
 * actually intend to exercise.
 *
 * <p>The token comes from a real registered user, not a bare minted claim:
 * since S6, {@code JwtAuthFilter} looks the claimed user id up in the
 * database, so a token for a nonexistent id would leave the request
 * unauthenticated and turn these into (unrelated) 401 failures.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfig.class, StubProviderConfig.class})
@TestPropertySource(properties = "app.poller.enabled=false")
class GlobalExceptionHandlerIT {

    @LocalServerPort
    private int port;

    private RestClient client() {
        return RestClient.create("http://localhost:" + port + "/api");
    }

    private String authHeader() {
        AuthResponse auth = client().post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", "mvc-error-" + System.nanoTime() + "@example.com", "password", "password123"))
                .retrieve().body(AuthResponse.class);
        return "Bearer " + auth.token();
    }

    @Test
    void unknownRouteReturns404NotServerError() {
        assertThatThrownBy(() -> client().get().uri("/does-not-exist")
                        .header("Authorization", authHeader())
                        .retrieve().body(String.class))
                .isInstanceOfSatisfying(HttpClientErrorException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void wrongHttpMethodReturns405NotServerError() {
        assertThatThrownBy(() -> client().post().uri("/market/coins")
                        .header("Authorization", authHeader())
                        .retrieve().body(String.class))
                .isInstanceOfSatisfying(HttpClientErrorException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(405));
    }
}
