package com.am.market_hub.market.dto;

import java.util.List;

/**
 * Server-owned dashboard presentation contract (F001-FR-005/006/015), so the
 * client never hardcodes field names or page sizes.
 *
 * <p>{@code supported} and {@code defaultVisible} are separate fields even though
 * they currently hold the same set: S8 gives each user a saved subset, and that
 * subset has to be distinguishable from the catalog it validates against.
 *
 * @param supported          every column key usable for display and sorting
 * @param defaultVisible     the application default visible set, in display order
 * @param supportedPageSizes selectable page sizes
 * @param defaultPageSize    the default page size (PRD fixes this at 20)
 */
public record ColumnCatalogResponse(
        List<String> supported,
        List<String> defaultVisible,
        List<Integer> supportedPageSizes,
        int defaultPageSize) {
}
