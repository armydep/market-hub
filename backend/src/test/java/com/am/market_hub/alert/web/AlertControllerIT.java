package com.am.market_hub.alert.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.am.market_hub.alert.dto.AlertResponse;
import com.am.market_hub.auth.dto.AuthResponse;
import com.am.market_hub.market.service.CryptoPoller;
import com.am.market_hub.support.StubPriceProvider;
import com.am.market_hub.support.StubProviderConfig;
import com.am.market_hub.support.TestcontainersConfig;

/**
 * Price alert CRUD, end-to-end against a real Postgres and a seeded stub
 * universe. Mirrors {@code MarketControllerIT}/{@code AdminUserControllerIT}'s
 * style. The fire-through-a-real-poll case lives in
 * {@code AlertEvaluationServiceIT}; this class covers everything reachable
 * without ever triggering an alert (plus the ownership/state-scoping around
 * clear, which needs one triggered alert to test against).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfig.class, StubProviderConfig.class})
@TestPropertySource(properties = "app.poller.enabled=false")
class AlertControllerIT {

    @LocalServerPort
    private int port;
    @Autowired
    private StubPriceProvider stub;
    @Autowired
    private CryptoPoller poller;

    private RestClient client;

    private RestClient client() {
        if (client == null) {
            client = RestClient.create("http://localhost:" + port + "/api");
        }
        return client;
    }

    @BeforeEach
    void seedUniverse() {
        stub.setQuotes(List.of(StubPriceProvider.quote(1, "BTC", 1, "100")));
        poller.pollOnce();
    }

    private String registerAndGetToken(String email) {
        AuthResponse response = client().post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "password123"))
                .retrieve().body(AuthResponse.class);
        return response.token();
    }

    private AlertResponse createAlert(String token, String symbol, String condition, String targetPrice) {
        return client().post().uri("/alerts")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("symbol", symbol, "condition", condition, "targetPrice", targetPrice))
                .retrieve().body(AlertResponse.class);
    }

    private void assertStatus(Runnable call, int expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(HttpClientErrorException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(expected));
    }

    @Test
    void createsAValidAlertBelowTheCurrentPrice() {
        String token = registerAndGetToken("alert-create-" + System.nanoTime() + "@example.com");

        AlertResponse response = createAlert(token, "BTC", "BELOW_OR_EQUAL", "50");

        assertThat(response.symbol()).isEqualTo("BTC");
        assertThat(response.active()).isTrue();
        assertThat(response.triggeredAt()).isNull();
    }

    @Test
    void rejectsCreationForAnUnknownSymbol() {
        String token = registerAndGetToken("alert-unknown-" + System.nanoTime() + "@example.com");

        assertStatus(() -> createAlert(token, "NOPE", "ABOVE_OR_EQUAL", "1"), 400);
    }

    @Test
    void rejectsCreationWhenTheConditionIsAlreadySatisfied() {
        String token = registerAndGetToken("alert-satisfied-" + System.nanoTime() + "@example.com");

        // Price is 100; ABOVE_OR_EQUAL 50 is already true.
        assertStatus(() -> createAlert(token, "BTC", "ABOVE_OR_EQUAL", "50"), 400);
    }

    @Test
    void listsOnlyActiveAlertsForTheOwner() {
        String token = registerAndGetToken("alert-list-" + System.nanoTime() + "@example.com");
        createAlert(token, "BTC", "BELOW_OR_EQUAL", "50");

        List<AlertResponse> active = List.of(client().get().uri("/alerts")
                .header("Authorization", "Bearer " + token)
                .retrieve().body(AlertResponse[].class));
        assertThat(active).hasSize(1);
        assertThat(active.get(0).symbol()).isEqualTo("BTC");

        List<AlertResponse> triggered = List.of(client().get().uri("/alerts/triggered")
                .header("Authorization", "Bearer " + token)
                .retrieve().body(AlertResponse[].class));
        assertThat(triggered).isEmpty();
    }

    @Test
    void updatesAnActiveAlertsConditionAndTargetPrice() {
        String token = registerAndGetToken("alert-update-" + System.nanoTime() + "@example.com");
        AlertResponse created = createAlert(token, "BTC", "BELOW_OR_EQUAL", "50");

        AlertResponse updated = client().patch().uri("/alerts/" + created.id())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("condition", "BELOW_OR_EQUAL", "targetPrice", "60"))
                .retrieve().body(AlertResponse.class);

        assertThat(updated.targetPrice()).isEqualByComparingTo("60");
        assertThat(updated.symbol()).isEqualTo("BTC");
    }

    @Test
    void updateIgnoresASymbolFieldSentInTheRequestBody() {
        String token = registerAndGetToken("alert-update-symbol-" + System.nanoTime() + "@example.com");
        AlertResponse created = createAlert(token, "BTC", "BELOW_OR_EQUAL", "50");

        AlertResponse updated = client().patch().uri("/alerts/" + created.id())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("symbol", "ETH", "condition", "BELOW_OR_EQUAL", "targetPrice", "60"))
                .retrieve().body(AlertResponse.class);

        assertThat(updated.symbol()).isEqualTo("BTC");
    }

    @Test
    void rejectsAnUpdateThatWouldAlreadyBeSatisfied() {
        String token = registerAndGetToken("alert-update-satisfied-" + System.nanoTime() + "@example.com");
        AlertResponse created = createAlert(token, "BTC", "BELOW_OR_EQUAL", "50");

        assertStatus(() -> client().patch().uri("/alerts/" + created.id())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("condition", "ABOVE_OR_EQUAL", "targetPrice", "50"))
                .retrieve().body(AlertResponse.class), 400);
    }

    @Test
    void deletesAnActiveAlert() {
        String token = registerAndGetToken("alert-delete-" + System.nanoTime() + "@example.com");
        AlertResponse created = createAlert(token, "BTC", "BELOW_OR_EQUAL", "50");

        client().delete().uri("/alerts/" + created.id())
                .header("Authorization", "Bearer " + token)
                .retrieve().toBodilessEntity();

        List<AlertResponse> active = List.of(client().get().uri("/alerts")
                .header("Authorization", "Bearer " + token)
                .retrieve().body(AlertResponse[].class));
        assertThat(active).isEmpty();
    }

    @Test
    void ownershipIsolationReturns404ForReadUpdateDeleteAndClear() {
        String owner = registerAndGetToken("alert-owner-" + System.nanoTime() + "@example.com");
        String intruder = registerAndGetToken("alert-intruder-" + System.nanoTime() + "@example.com");
        AlertResponse created = createAlert(owner, "BTC", "BELOW_OR_EQUAL", "50");

        assertStatus(() -> client().patch().uri("/alerts/" + created.id())
                .header("Authorization", "Bearer " + intruder)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("condition", "BELOW_OR_EQUAL", "targetPrice", "60"))
                .retrieve().body(AlertResponse.class), 404);

        assertStatus(() -> client().delete().uri("/alerts/" + created.id())
                .header("Authorization", "Bearer " + intruder)
                .retrieve().toBodilessEntity(), 404);

        assertStatus(() -> client().post().uri("/alerts/" + created.id() + "/clear")
                .header("Authorization", "Bearer " + intruder)
                .retrieve().body(AlertResponse.class), 404);
    }

    @Test
    void cannotClearAnActiveAlert() {
        String token = registerAndGetToken("alert-clear-active-" + System.nanoTime() + "@example.com");
        AlertResponse created = createAlert(token, "BTC", "BELOW_OR_EQUAL", "50");

        assertStatus(() -> client().post().uri("/alerts/" + created.id() + "/clear")
                .header("Authorization", "Bearer " + token)
                .retrieve().body(AlertResponse.class), 404);
    }

    @Test
    void clearingATriggeredAlertRemovesItFromTheVisibleTriggeredList() {
        String token = registerAndGetToken("alert-clear-" + System.nanoTime() + "@example.com");
        AlertResponse created = createAlert(token, "BTC", "ABOVE_OR_EQUAL", "150");

        // Push the price up so the alert fires on the next real poll cycle.
        stub.setQuotes(List.of(StubPriceProvider.quote(1, "BTC", 1, "200")));
        poller.pollOnce();

        List<AlertResponse> triggered = List.of(client().get().uri("/alerts/triggered")
                .header("Authorization", "Bearer " + token)
                .retrieve().body(AlertResponse[].class));
        assertThat(triggered).hasSize(1);
        assertThat(triggered.get(0).triggeredPrice()).isEqualByComparingTo("200");

        client().post().uri("/alerts/" + created.id() + "/clear")
                .header("Authorization", "Bearer " + token)
                .retrieve().body(AlertResponse.class);

        List<AlertResponse> afterClear = List.of(client().get().uri("/alerts/triggered")
                .header("Authorization", "Bearer " + token)
                .retrieve().body(AlertResponse[].class));
        assertThat(afterClear).isEmpty();
    }
}
