package com.am.market_hub.notification.web;

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
import com.am.market_hub.notification.dto.NotificationResponse;
import com.am.market_hub.support.StubPriceProvider;
import com.am.market_hub.support.StubProviderConfig;
import com.am.market_hub.support.TestcontainersConfig;

/**
 * Alert-trigger notifications, end-to-end against a real Postgres and a
 * seeded stub universe. Mirrors {@code AlertControllerIT}/
 * {@code AlertEvaluationServiceIT}'s style: a real {@code poller.pollOnce()}
 * call is what actually fires the trigger-and-notification path.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfig.class, StubProviderConfig.class})
@TestPropertySource(properties = "app.poller.enabled=false")
class NotificationControllerIT {

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

    private List<NotificationResponse> listNotifications(String token) {
        return List.of(client().get().uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .retrieve().body(NotificationResponse[].class));
    }

    private void assertStatus(Runnable call, int expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(HttpClientErrorException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(expected));
    }

    @Test
    void triggeringAnAlertCreatesExactlyOneNotificationWithTheCorrectContent() {
        String token = registerAndGetToken("notif-trigger-" + System.nanoTime() + "@example.com");
        createAlert(token, "BTC", "ABOVE_OR_EQUAL", "150");

        stub.setQuotes(List.of(StubPriceProvider.quote(1, "BTC", 1, "200")));
        poller.pollOnce();

        List<NotificationResponse> notifications = listNotifications(token);
        assertThat(notifications).hasSize(1);
        NotificationResponse notification = notifications.get(0);
        assertThat(notification.symbol()).isEqualTo("BTC");
        assertThat(notification.condition()).isEqualTo("ABOVE_OR_EQUAL");
        assertThat(notification.targetPrice()).isEqualByComparingTo("150");
        assertThat(notification.triggeredPrice()).isEqualByComparingTo("200");
        assertThat(notification.triggeredAt()).isNotNull();
    }

    @Test
    void aLaterPollCycleNeverCreatesASecondNotificationForAnAlreadyTriggeredAlert() {
        String token = registerAndGetToken("notif-no-dup-" + System.nanoTime() + "@example.com");
        createAlert(token, "BTC", "ABOVE_OR_EQUAL", "150");

        stub.setQuotes(List.of(StubPriceProvider.quote(1, "BTC", 1, "200")));
        poller.pollOnce();
        stub.setQuotes(List.of(StubPriceProvider.quote(1, "BTC", 1, "250")));
        poller.pollOnce();

        assertThat(listNotifications(token)).hasSize(1);
    }

    @Test
    void listsOnlyUnclearedNotificationsForTheOwner() {
        String token = registerAndGetToken("notif-list-" + System.nanoTime() + "@example.com");
        createAlert(token, "BTC", "ABOVE_OR_EQUAL", "150");
        stub.setQuotes(List.of(StubPriceProvider.quote(1, "BTC", 1, "200")));
        poller.pollOnce();

        assertThat(listNotifications(token)).hasSize(1);
    }

    @Test
    void clearingANotificationRemovesItFromTheVisibleList() {
        String token = registerAndGetToken("notif-clear-" + System.nanoTime() + "@example.com");
        createAlert(token, "BTC", "ABOVE_OR_EQUAL", "150");
        stub.setQuotes(List.of(StubPriceProvider.quote(1, "BTC", 1, "200")));
        poller.pollOnce();
        Long notificationId = listNotifications(token).get(0).id();

        client().post().uri("/notifications/" + notificationId + "/clear")
                .header("Authorization", "Bearer " + token)
                .retrieve().body(NotificationResponse.class);

        assertThat(listNotifications(token)).isEmpty();
    }

    @Test
    void clearingAnAlreadyClearedNotificationReturns404() {
        String token = registerAndGetToken("notif-clear-twice-" + System.nanoTime() + "@example.com");
        createAlert(token, "BTC", "ABOVE_OR_EQUAL", "150");
        stub.setQuotes(List.of(StubPriceProvider.quote(1, "BTC", 1, "200")));
        poller.pollOnce();
        Long notificationId = listNotifications(token).get(0).id();
        client().post().uri("/notifications/" + notificationId + "/clear")
                .header("Authorization", "Bearer " + token)
                .retrieve().body(NotificationResponse.class);

        assertStatus(() -> client().post().uri("/notifications/" + notificationId + "/clear")
                .header("Authorization", "Bearer " + token)
                .retrieve().body(NotificationResponse.class), 404);
    }

    @Test
    void ownershipIsolationReturns404ForAnIntrudersClearAttempt() {
        String owner = registerAndGetToken("notif-owner-" + System.nanoTime() + "@example.com");
        String intruder = registerAndGetToken("notif-intruder-" + System.nanoTime() + "@example.com");
        createAlert(owner, "BTC", "ABOVE_OR_EQUAL", "150");
        stub.setQuotes(List.of(StubPriceProvider.quote(1, "BTC", 1, "200")));
        poller.pollOnce();
        Long notificationId = listNotifications(owner).get(0).id();

        assertStatus(() -> client().post().uri("/notifications/" + notificationId + "/clear")
                .header("Authorization", "Bearer " + intruder)
                .retrieve().body(NotificationResponse.class), 404);
        assertThat(listNotifications(intruder)).isEmpty();
        assertThat(listNotifications(owner)).hasSize(1);
    }

    @Test
    void clearingANotificationDoesNotAffectItsAlertsOwnState() {
        String token = registerAndGetToken("notif-independent-" + System.nanoTime() + "@example.com");
        AlertResponse alert = createAlert(token, "BTC", "ABOVE_OR_EQUAL", "150");
        stub.setQuotes(List.of(StubPriceProvider.quote(1, "BTC", 1, "200")));
        poller.pollOnce();
        Long notificationId = listNotifications(token).get(0).id();

        client().post().uri("/notifications/" + notificationId + "/clear")
                .header("Authorization", "Bearer " + token)
                .retrieve().body(NotificationResponse.class);

        List<AlertResponse> triggeredAlerts = List.of(client().get().uri("/alerts/triggered")
                .header("Authorization", "Bearer " + token)
                .retrieve().body(AlertResponse[].class));
        assertThat(triggeredAlerts).hasSize(1);
        assertThat(triggeredAlerts.get(0).id()).isEqualTo(alert.id());
        assertThat(triggeredAlerts.get(0).triggeredPrice()).isEqualByComparingTo("200");
    }
}
