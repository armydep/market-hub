package com.am.market_hub.user.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.am.market_hub.auth.dto.AuthResponse;
import com.am.market_hub.market.dto.ColumnCatalogResponse;
import com.am.market_hub.support.TestcontainersConfig;
import com.am.market_hub.user.dto.AccountResponse;
import com.am.market_hub.user.dto.PreferencesResponse;

/**
 * Account view, email/password change, and display preferences, end-to-end
 * against a real Postgres. Mirrors {@code AuthControllerIT}/
 * {@code AlertControllerIT}'s style.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class AccountControllerIT {

    @LocalServerPort
    private int port;

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

    private static void assertStatus(Runnable call, int expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(HttpClientErrorException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(expected));
    }

    @Test
    void viewsTheCallersOwnAccount() {
        String email = "account-view-" + System.nanoTime() + "@example.com";
        AuthResponse auth = register(email);

        AccountResponse account = client().get().uri("/account")
                .header("Authorization", "Bearer " + auth.token())
                .retrieve().body(AccountResponse.class);

        assertThat(account.email()).isEqualTo(email);
        assertThat(account.role()).isEqualTo("TRADER");
    }

    @Test
    void changesEmailWithTheCorrectCurrentPassword() {
        AuthResponse auth = register("account-email-" + System.nanoTime() + "@example.com");
        String newEmail = "account-email-new-" + System.nanoTime() + "@example.com";

        AccountResponse updated = client().patch().uri("/account")
                .header("Authorization", "Bearer " + auth.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", newEmail, "currentPassword", "password123"))
                .retrieve().body(AccountResponse.class);

        assertThat(updated.email()).isEqualTo(newEmail);

        AuthResponse loginWithNewEmail = client().post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", newEmail, "password", "password123"))
                .retrieve().body(AuthResponse.class);
        assertThat(loginWithNewEmail.token()).isNotBlank();
    }

    @Test
    void changingEmailToAnAlreadyRegisteredAddressReturns409() {
        String takenEmail = "account-taken-" + System.nanoTime() + "@example.com";
        register(takenEmail);
        AuthResponse auth = register("account-conflict-" + System.nanoTime() + "@example.com");

        assertStatus(() -> client().patch().uri("/account")
                .header("Authorization", "Bearer " + auth.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", takenEmail, "currentPassword", "password123"))
                .retrieve().body(AccountResponse.class), 409);
    }

    @Test
    void changingEmailWithTheWrongCurrentPasswordReturns400() {
        AuthResponse auth = register("account-wrong-pw-" + System.nanoTime() + "@example.com");

        assertStatus(() -> client().patch().uri("/account")
                .header("Authorization", "Bearer " + auth.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", "irrelevant-" + System.nanoTime() + "@example.com",
                        "currentPassword", "wrong-password"))
                .retrieve().body(AccountResponse.class), 400);
    }

    @Test
    void changesPasswordAndTheOldOneStopsWorking() {
        String email = "account-pw-" + System.nanoTime() + "@example.com";
        AuthResponse auth = register(email);

        client().post().uri("/account/password")
                .header("Authorization", "Bearer " + auth.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("currentPassword", "password123", "newPassword", "new-password-456"))
                .retrieve().toBodilessEntity();

        assertStatus(() -> client().post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "password123"))
                .retrieve().body(AuthResponse.class), 401);

        AuthResponse loginWithNewPassword = client().post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "new-password-456"))
                .retrieve().body(AuthResponse.class);
        assertThat(loginWithNewPassword.token()).isNotBlank();
    }

    @Test
    void changingPasswordWithTheWrongCurrentPasswordReturns400() {
        AuthResponse auth = register("account-pw-wrong-" + System.nanoTime() + "@example.com");

        assertStatus(() -> client().post().uri("/account/password")
                .header("Authorization", "Bearer " + auth.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("currentPassword", "wrong-password", "newPassword", "new-password-456"))
                .retrieve().toBodilessEntity(), 400);
    }

    @Test
    void preferencesDefaultToTheApplicationDefaultVisibleSetBeforeEverSaved() {
        AuthResponse auth = register("account-prefs-default-" + System.nanoTime() + "@example.com");

        ColumnCatalogResponse catalog = client().get().uri("/market/columns")
                .retrieve().body(ColumnCatalogResponse.class);
        PreferencesResponse preferences = client().get().uri("/account/preferences")
                .header("Authorization", "Bearer " + auth.token())
                .retrieve().body(PreferencesResponse.class);

        assertThat(preferences.visibleColumns()).isEqualTo(catalog.defaultVisible());
    }

    @Test
    void preferencesRoundTripThroughSaveAndReadBack() {
        AuthResponse auth = register("account-prefs-roundtrip-" + System.nanoTime() + "@example.com");
        List<String> chosen = List.of("symbol", "name", "price");

        PreferencesResponse saved = client().put().uri("/account/preferences")
                .header("Authorization", "Bearer " + auth.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("visibleColumns", chosen))
                .retrieve().body(PreferencesResponse.class);
        assertThat(saved.visibleColumns()).isEqualTo(chosen);

        PreferencesResponse reread = client().get().uri("/account/preferences")
                .header("Authorization", "Bearer " + auth.token())
                .retrieve().body(PreferencesResponse.class);
        assertThat(reread.visibleColumns()).isEqualTo(chosen);
    }

    @Test
    void savingAnUnknownColumnKeyReturns400() {
        AuthResponse auth = register("account-prefs-unknown-" + System.nanoTime() + "@example.com");

        assertStatus(() -> client().put().uri("/account/preferences")
                .header("Authorization", "Bearer " + auth.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("visibleColumns", List.of("symbol", "notARealColumn")))
                .retrieve().body(PreferencesResponse.class), 400);
    }

    @Test
    void eachUserOnlySeesTheirOwnAccountAndPreferences() {
        AuthResponse first = register("account-isolation-1-" + System.nanoTime() + "@example.com");
        AuthResponse second = register("account-isolation-2-" + System.nanoTime() + "@example.com");

        client().put().uri("/account/preferences")
                .header("Authorization", "Bearer " + first.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("visibleColumns", List.of("symbol")))
                .retrieve().toBodilessEntity();

        AccountResponse secondAccount = client().get().uri("/account")
                .header("Authorization", "Bearer " + second.token())
                .retrieve().body(AccountResponse.class);
        assertThat(secondAccount.email()).isEqualTo(second.email());

        PreferencesResponse secondPreferences = client().get().uri("/account/preferences")
                .header("Authorization", "Bearer " + second.token())
                .retrieve().body(PreferencesResponse.class);
        // Second user never saved preferences, so they must still see the
        // application default (a multi-column list), not the first user's
        // single-column choice — proof the two never share a preference row.
        assertThat(secondPreferences.visibleColumns()).isNotEqualTo(List.of("symbol"));
    }
}
