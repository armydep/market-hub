package com.am.market_hub.alert.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
import org.springframework.web.client.RestClient;

import com.am.market_hub.alert.domain.PriceAlert;
import com.am.market_hub.alert.dto.AlertResponse;
import com.am.market_hub.alert.repository.AlertRepository;
import com.am.market_hub.auth.dto.AuthResponse;
import com.am.market_hub.market.service.CryptoPoller;
import com.am.market_hub.support.StubPriceProvider;
import com.am.market_hub.support.StubProviderConfig;
import com.am.market_hub.support.TestcontainersConfig;

/**
 * Drives real evaluation through {@code poller.pollOnce()}, exactly the way
 * {@code MarketControllerIT} seeds its fixture data — this is the real
 * {@code PollCompletedEvent}, not a test-only substitute.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfig.class, StubProviderConfig.class})
@TestPropertySource(properties = "app.poller.enabled=false")
class AlertEvaluationServiceIT {

    @LocalServerPort
    private int port;
    @Autowired
    private StubPriceProvider stub;
    @Autowired
    private CryptoPoller poller;
    @Autowired
    private AlertRepository alertRepository;

    private RestClient client;

    private RestClient client() {
        if (client == null) {
            client = RestClient.create("http://localhost:" + port + "/api");
        }
        return client;
    }

    @BeforeEach
    void seedUniverse() {
        stub.setQuotes(List.of(
                StubPriceProvider.quote(1, "BTC", 1, "100"),
                StubPriceProvider.quote(2, "ETH", 2, "50")));
        poller.pollOnce();
    }

    private String registerAndGetToken(String email) {
        return client().post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "password123"))
                .retrieve().body(AuthResponse.class).token();
    }

    private AlertResponse createAlert(String token, String symbol, String condition, String targetPrice) {
        return client().post().uri("/alerts")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("symbol", symbol, "condition", condition, "targetPrice", targetPrice))
                .retrieve().body(AlertResponse.class);
    }

    @Test
    void anAlertFiresExactlyOnceAndStaysFiredOnALaterCycle() {
        String token = registerAndGetToken("eval-fire-once-" + System.nanoTime() + "@example.com");
        AlertResponse created = createAlert(token, "BTC", "ABOVE_OR_EQUAL", "150");

        stub.setQuotes(List.of(
                StubPriceProvider.quote(1, "BTC", 1, "200"),
                StubPriceProvider.quote(2, "ETH", 2, "50")));
        poller.pollOnce();

        PriceAlert afterFirstFire = alertRepository.findById(created.id()).orElseThrow();
        assertThat(afterFirstFire.isActive()).isFalse();
        assertThat(afterFirstFire.getTriggeredPrice()).isEqualByComparingTo("200");

        // A second cycle, still satisfying the condition, must not refire or
        // otherwise change the already-triggered alert.
        stub.setQuotes(List.of(
                StubPriceProvider.quote(1, "BTC", 1, "250"),
                StubPriceProvider.quote(2, "ETH", 2, "50")));
        poller.pollOnce();

        PriceAlert afterSecondCycle = alertRepository.findById(created.id()).orElseThrow();
        assertThat(afterSecondCycle.getTriggeredPrice()).isEqualByComparingTo("200");
    }

    @Test
    void anAlertWhoseSymbolLeavesTheUniverseIsLeftUntouched() {
        String token = registerAndGetToken("eval-not-evaluable-" + System.nanoTime() + "@example.com");
        AlertResponse created = createAlert(token, "ETH", "ABOVE_OR_EQUAL", "60");

        // Next cycle no longer includes ETH at all.
        stub.setQuotes(List.of(StubPriceProvider.quote(1, "BTC", 1, "100")));
        poller.pollOnce();

        PriceAlert afterCycle = alertRepository.findById(created.id()).orElseThrow();
        assertThat(afterCycle.isActive()).isTrue();
        assertThat(afterCycle.getTriggeredAt()).isNull();
    }

    @Test
    void multipleAlertsOnTheSameSymbolAllEvaluateInOneCycle() {
        String token = registerAndGetToken("eval-multi-" + System.nanoTime() + "@example.com");
        AlertResponse first = createAlert(token, "BTC", "ABOVE_OR_EQUAL", "150");
        // Different threshold, same direction: both unsatisfied at the seeded
        // price (100) and both satisfied once it moves to 200.
        AlertResponse second = createAlert(token, "BTC", "ABOVE_OR_EQUAL", "180");

        stub.setQuotes(List.of(
                StubPriceProvider.quote(1, "BTC", 1, "200"),
                StubPriceProvider.quote(2, "ETH", 2, "50")));
        poller.pollOnce();

        assertThat(alertRepository.findById(first.id()).orElseThrow().isActive()).isFalse();
        assertThat(alertRepository.findById(second.id()).orElseThrow().isActive()).isFalse();
    }
}
