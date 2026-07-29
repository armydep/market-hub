package com.am.market_hub;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.am.market_hub.support.TestcontainersConfig;

@SpringBootTest
@Import(TestcontainersConfig.class)
class MarketHubApplicationTests {

    @Test
    void contextLoads() {
    }
}
