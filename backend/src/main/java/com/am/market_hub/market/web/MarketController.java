package com.am.market_hub.market.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.am.market_hub.market.dto.CoinPageResponse;
import com.am.market_hub.market.dto.CoinResponse;
import com.am.market_hub.market.dto.ColumnCatalogResponse;
import com.am.market_hub.market.service.MarketService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Public market reads (no auth). Serves the cached universe; never calls a provider. */
@Tag(name = "Market", description = "Public crypto market reads (no auth)")
@RestController
@RequestMapping("/market")
public class MarketController {

    private final MarketService marketService;

    public MarketController(MarketService marketService) {
        this.marketService = marketService;
    }

    @Operation(summary = "List cached coins",
            description = "Paginated, sorted, optionally searched view of the cached top-N universe. "
                    + "Sort by a catalog field (see /market/columns); order is asc or desc. "
                    + "`q` matches a name or symbol substring, case-insensitively. Sorting and "
                    + "searching apply to the complete matching dataset before pagination. "
                    + "An unsupported page size or unknown sort field is a 400; a search with no "
                    + "matches is a 200 with an empty page.")
    @GetMapping("/coins")
    public CoinPageResponse list(
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String order,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String q) {
        return marketService.list(sort, order, page, size, q);
    }

    @Operation(summary = "Get a coin by symbol", description = "404 if the symbol is not in the current universe.")
    @GetMapping("/coins/{symbol}")
    public CoinResponse get(@PathVariable String symbol) {
        return marketService.getBySymbol(symbol);
    }

    @Operation(summary = "Dashboard column catalog",
            description = "Supported columns, the application default visible set, and the "
                    + "selectable page sizes, so the client never hardcodes them.")
    @GetMapping("/columns")
    public ColumnCatalogResponse columns() {
        return marketService.columnCatalog();
    }
}
