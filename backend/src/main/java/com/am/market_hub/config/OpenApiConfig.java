package com.am.market_hub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

/**
 * OpenAPI document metadata. Swagger UI is served at {@code /api/swagger-ui.html}
 * and the raw spec at {@code /api/v3/api-docs} (both under the {@code /api}
 * context path).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI marketHubOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("market-hub API")
                .description("Crypto price dashboard backend: public market reads, "
                        + "user boards, and price alerts.")
                .version("v1")
                .license(new License().name("Proprietary")));
    }
}
