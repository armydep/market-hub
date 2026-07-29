package com.am.market_hub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class MarketHubApplication {

	public static void main(String[] args) {
		SpringApplication.run(MarketHubApplication.class, args);
	}

}
