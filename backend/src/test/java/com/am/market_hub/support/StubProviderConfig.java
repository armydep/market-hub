package com.am.market_hub.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Makes the stub the primary {@code PriceProvider} so the poller uses it, not CMC. */
@TestConfiguration(proxyBeanMethods = false)
public class StubProviderConfig {

    @Bean
    @Primary
    StubPriceProvider stubPriceProvider() {
        return new StubPriceProvider();
    }
}
