package com.am.market_hub.market.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.am.market_hub.market.dto.CoinResponse;
import com.am.market_hub.market.service.MarketService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Public market reads (no auth). Serves the cached universe; never calls a provider. */
@Tag(name = "Market", description = "Public crypto market reads (no auth)")
@RestController
@RequestMapping("/market/coins")
public class MarketController {

    private final MarketService marketService;

    public MarketController(MarketService marketService) {
        this.marketService = marketService;
    }

    @Operation(summary = "List cached coins",
            description = "Returns the cached top-N universe. Sort by a catalog field "
                    + "(symbol, name, marketCapRank, price, pctChange1h/24h/7d, marketCap, "
                    + "volume24h, circulatingSupply); order is asc or desc.")
    @GetMapping
    public List<CoinResponse> list(
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String order) {
        return marketService.list(sort, order);
    }

    @Operation(summary = "Get a coin by symbol", description = "404 if the symbol is not in the current universe.")
    @GetMapping("/{symbol}")
    public CoinResponse get(@PathVariable String symbol) {
        return marketService.getBySymbol(symbol);
    }
}
