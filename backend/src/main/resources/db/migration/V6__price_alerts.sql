-- One-time above/below price alerts (PRD F-006). No FK to crypto_quotes:
-- symbol is a plain string column, since the top-N universe churns and
-- provider ids are provider-specific (domain-model.md). A symbol that
-- transiently references a coin not currently cached is "not evaluable
-- this cycle", never an integrity error.
CREATE TABLE price_alerts (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    symbol          VARCHAR(32)    NOT NULL,
    condition       VARCHAR(20)    NOT NULL,
    target_price    NUMERIC(30,10) NOT NULL,
    active          BOOLEAN        NOT NULL DEFAULT true,
    triggered_at    TIMESTAMPTZ,
    triggered_price NUMERIC(30,10),
    cleared_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);
