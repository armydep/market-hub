package com.am.market_hub.market.domain;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Server-defined column catalog: the quote fields exposed to the UI and usable
 * for sorting. In Phase 1 the column key equals the JPA property name, so it can
 * be used directly as a Spring Data {@code Sort} property. Reused by S3 to
 * validate board {@code columnsJson} / {@code sortField}.
 */
public enum CoinColumn {

    SYMBOL("symbol"),
    NAME("name"),
    MARKET_CAP_RANK("marketCapRank"),
    PRICE("price"),
    PCT_CHANGE_1H("pctChange1h"),
    PCT_CHANGE_24H("pctChange24h"),
    PCT_CHANGE_7D("pctChange7d"),
    MARKET_CAP("marketCap"),
    VOLUME_24H("volume24h"),
    CIRCULATING_SUPPLY("circulatingSupply");

    private final String key;

    CoinColumn(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<CoinColumn> byKey(String key) {
        return Arrays.stream(values()).filter(c -> c.key.equals(key)).findFirst();
    }

    public static Set<String> keys() {
        return Arrays.stream(values()).map(CoinColumn::key)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
