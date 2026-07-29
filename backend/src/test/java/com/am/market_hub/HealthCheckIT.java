package com.am.market_hub;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import com.am.market_hub.support.TestcontainersConfig;

/**
 * S0 ship criterion: the app boots against a real (Testcontainers) Postgres and
 * {@code GET /api/actuator/health} reports UP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class HealthCheckIT {

    @LocalServerPort
    private int port;

    @Test
    void healthIsUp() {
        RestClient client = RestClient.create();

        ResponseEntity<String> response = client.get()
                .uri("http://localhost:" + port + "/api/actuator/health")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}
