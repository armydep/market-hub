package com.am.market_hub.market.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.am.market_hub.market.provider.ProviderQuote;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One coin in the cached top-N universe. Poller-owned read-model, keyed by the
 * CoinMarketCap id. Fully re-upserted each poll cycle; not user data.
 */
@Entity
@Table(name = "crypto_quotes")
public class CryptoQuote {

    @Id
    @Column(name = "cmc_id")
    private Integer cmcId;

    private String symbol;
    private String name;
    private String slug;

    @Column(name = "asset_type")
    private String assetType;

    @Column(name = "market_cap_rank")
    private Integer marketCapRank;

    private BigDecimal price;

    @Column(name = "pct_change_1h")
    private BigDecimal pctChange1h;

    @Column(name = "pct_change_24h")
    private BigDecimal pctChange24h;

    @Column(name = "pct_change_7d")
    private BigDecimal pctChange7d;

    @Column(name = "market_cap")
    private BigDecimal marketCap;

    @Column(name = "volume_24h")
    private BigDecimal volume24h;

    @Column(name = "circulating_supply")
    private BigDecimal circulatingSupply;

    @Column(name = "convert_currency")
    private String convertCurrency;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected CryptoQuote() {
    }

    public static CryptoQuote fromProvider(ProviderQuote q) {
        CryptoQuote quote = new CryptoQuote();
        quote.cmcId = q.cmcId();
        quote.applyProvider(q);
        return quote;
    }

    /** Overwrite the mutable quote fields from a fresh provider reading. */
    public void applyProvider(ProviderQuote q) {
        this.symbol = q.symbol();
        this.name = q.name();
        this.slug = q.slug();
        this.assetType = q.assetType() == null ? "CRYPTO" : q.assetType();
        this.marketCapRank = q.marketCapRank();
        this.price = q.price();
        this.pctChange1h = q.pctChange1h();
        this.pctChange24h = q.pctChange24h();
        this.pctChange7d = q.pctChange7d();
        this.marketCap = q.marketCap();
        this.volume24h = q.volume24h();
        this.circulatingSupply = q.circulatingSupply();
        this.convertCurrency = q.convertCurrency() == null ? "USD" : q.convertCurrency();
        this.updatedAt = Instant.now();
    }

    public Integer getCmcId() {
        return cmcId;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getAssetType() {
        return assetType;
    }

    public Integer getMarketCapRank() {
        return marketCapRank;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getPctChange1h() {
        return pctChange1h;
    }

    public BigDecimal getPctChange24h() {
        return pctChange24h;
    }

    public BigDecimal getPctChange7d() {
        return pctChange7d;
    }

    public BigDecimal getMarketCap() {
        return marketCap;
    }

    public BigDecimal getVolume24h() {
        return volume24h;
    }

    public BigDecimal getCirculatingSupply() {
        return circulatingSupply;
    }

    public String getConvertCurrency() {
        return convertCurrency;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
