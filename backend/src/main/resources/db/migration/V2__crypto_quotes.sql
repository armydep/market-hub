-- Cached top-N crypto universe, upserted by the poller each cycle.
-- Poller-owned read-model (not user data); may be rebuilt at will.
CREATE TABLE crypto_quotes (
    cmc_id             INTEGER       PRIMARY KEY,   -- CoinMarketCap id (provider identity)
    symbol             VARCHAR(32)   NOT NULL,
    name               VARCHAR(128)  NOT NULL,
    slug               VARCHAR(128),
    asset_type         VARCHAR(16)   NOT NULL DEFAULT 'CRYPTO',
    market_cap_rank    INTEGER,
    price              NUMERIC(30, 10),
    pct_change_1h      NUMERIC(18, 6),
    pct_change_24h     NUMERIC(18, 6),
    pct_change_7d      NUMERIC(18, 6),
    market_cap         NUMERIC(30, 2),
    volume_24h         NUMERIC(30, 2),
    circulating_supply NUMERIC(30, 4),
    convert_currency   VARCHAR(16)   NOT NULL DEFAULT 'USD',
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_crypto_quotes_symbol ON crypto_quotes (symbol);
CREATE INDEX idx_crypto_quotes_rank ON crypto_quotes (market_cap_rank);
