package com.am.market_hub.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Shared Testcontainers Postgres wired to Spring via {@link ServiceConnection},
 * so integration tests run against a real Postgres (no H2). Import into a
 * {@code @SpringBootTest} to get a fully configured datasource + Flyway.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:16-alpine");
    }
}
