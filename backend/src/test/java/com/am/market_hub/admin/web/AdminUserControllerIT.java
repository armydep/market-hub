package com.am.market_hub.admin.web;

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

import com.am.market_hub.admin.dto.AdminUserPageResponse;
import com.am.market_hub.admin.dto.AdminUserResponse;
import com.am.market_hub.admin.repository.AdminAuditLogRepository;
import com.am.market_hub.auth.dto.AuthResponse;
import com.am.market_hub.support.TestcontainersConfig;

/**
 * Admin user list + block/unblock, end-to-end against a real Postgres. Mirrors
 * {@code AuthControllerIT}/{@code AccountLockoutIT}'s style.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = {
        "app.admin.email=admin-users-it@example.com",
        "app.admin.password=admin-password-123"
})
class AdminUserControllerIT {

    private static final String ADMIN_EMAIL = "admin-users-it@example.com";
    private static final String ADMIN_PASSWORD = "admin-password-123";

    @LocalServerPort
    private int port;
    @Autowired
    private AdminAuditLogRepository auditLogRepository;

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
                .body(Map.of("email", email, "password", "password123"))
                .retrieve().body(AuthResponse.class);
    }

    private String adminToken() {
        return client().post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD))
                .retrieve().body(AuthResponse.class).token();
    }

    private AdminUserResponse block(String adminToken, Long id) {
        return client().post().uri("/admin/users/{id}/block", id)
                .header("Authorization", "Bearer " + adminToken)
                .retrieve().body(AdminUserResponse.class);
    }

    private AdminUserResponse unblock(String adminToken, Long id) {
        return client().post().uri("/admin/users/{id}/unblock", id)
                .header("Authorization", "Bearer " + adminToken)
                .retrieve().body(AdminUserResponse.class);
    }

    private void assertStatus(Runnable call, int expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(HttpClientErrorException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(expected));
    }

    @Test
    void aNonAdminGets403OnEveryAdminRoute() {
        String email = "non-admin-list-" + System.nanoTime() + "@example.com";
        AuthResponse trader = register(email);

        assertStatus(() -> client().get().uri("/admin/users")
                .header("Authorization", "Bearer " + trader.token())
                .retrieve().body(AdminUserPageResponse.class), 403);
        assertStatus(() -> client().post().uri("/admin/users/1/block")
                .header("Authorization", "Bearer " + trader.token())
                .retrieve().body(AdminUserResponse.class), 403);
        assertStatus(() -> client().post().uri("/admin/users/1/unblock")
                .header("Authorization", "Bearer " + trader.token())
                .retrieve().body(AdminUserResponse.class), 403);
    }

    @Test
    void noTokenAtAllGets401OnEveryAdminRoute() {
        assertStatus(() -> client().get().uri("/admin/users")
                .retrieve().body(AdminUserPageResponse.class), 401);
        assertStatus(() -> client().post().uri("/admin/users/1/block")
                .retrieve().body(AdminUserResponse.class), 401);
    }

    @Test
    void anAdminListsUsersAndSeesANewlyRegisteredOne() {
        String email = "list-target-" + System.nanoTime() + "@example.com";
        register(email);
        String admin = adminToken();

        // Page through everything: the new user could land on any page once
        // enough accounts accumulate across this class's other tests.
        boolean found = false;
        int page = 0;
        AdminUserPageResponse response;
        do {
            response = client().get().uri("/admin/users?page=" + page)
                    .header("Authorization", "Bearer " + admin)
                    .retrieve().body(AdminUserPageResponse.class);
            found = response.content().stream().anyMatch(u -> u.email().equals(email));
            page++;
        } while (!found && page < response.totalPages());

        assertThat(found).isTrue();
    }

    @Test
    void blockRoundTripRejectsTheTargetAtLoginAndOnAProtectedRoute() {
        String email = "block-target-" + System.nanoTime() + "@example.com";
        AuthResponse target = register(email);
        String admin = adminToken();

        AdminUserResponse blocked = block(admin, target.userId());
        assertThat(blocked.blocked()).isTrue();

        assertThatThrownBy(() -> client().post().uri("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", email, "password", "password123"))
                        .retrieve().body(AuthResponse.class))
                .isInstanceOfSatisfying(HttpClientErrorException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(403);
                    assertThat(ex.getResponseBodyAsString()).contains("Account is blocked");
                });

        assertThatThrownBy(() -> client().get().uri("/test/protected")
                        .header("Authorization", "Bearer " + target.token())
                        .retrieve().body(String.class))
                .isInstanceOfSatisfying(HttpClientErrorException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(401));
    }

    @Test
    void unblockRoundTripLetsTheTargetSignInAgain() {
        String email = "unblock-target-" + System.nanoTime() + "@example.com";
        AuthResponse target = register(email);
        String admin = adminToken();

        block(admin, target.userId());
        AdminUserResponse unblocked = unblock(admin, target.userId());
        assertThat(unblocked.blocked()).isFalse();

        AuthResponse loggedIn = client().post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "password123"))
                .retrieve().body(AuthResponse.class);
        assertThat(loggedIn.token()).isNotBlank();
    }

    @Test
    void blockingTheSameUserTwiceWritesExactlyOneAuditRow() {
        String email = "double-block-" + System.nanoTime() + "@example.com";
        AuthResponse target = register(email);
        String admin = adminToken();
        long before = auditLogRepository.count();

        block(admin, target.userId());
        block(admin, target.userId());

        assertThat(auditLogRepository.count()).isEqualTo(before + 1);
    }

    @Test
    void unblockingAnAlreadyUnblockedUserWritesNoAuditRow() {
        String email = "noop-unblock-" + System.nanoTime() + "@example.com";
        AuthResponse target = register(email);
        String admin = adminToken();
        long before = auditLogRepository.count();

        unblock(admin, target.userId());

        assertThat(auditLogRepository.count()).isEqualTo(before);
    }

    @Test
    void anUnknownTargetIdReturns404() {
        String admin = adminToken();

        assertStatus(() -> block(admin, 999_999_999L), 404);
    }
}
