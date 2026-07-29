package com.am.market_hub.market;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.am.market_hub.market.dto.CoinResponse;

/** Public market reads (no auth). Serves the cached universe; never calls a provider. */
@RestController
@RequestMapping("/market/coins")
public class MarketController {

    private final MarketService marketService;

    public MarketController(MarketService marketService) {
        this.marketService = marketService;
    }

    @GetMapping
    public List<CoinResponse> list(
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String order) {
        return marketService.list(sort, order);
    }

    @GetMapping("/{symbol}")
    public CoinResponse get(@PathVariable String symbol) {
        return marketService.getBySymbol(symbol);
    }
}
