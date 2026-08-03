-- In-app notification created when a price alert fires (PRD F-007). One row
-- per alert trigger; UNIQUE(alert_id) is the actual duplicate-suppression
-- guarantee, not merely a code-path check (domain-model.md).
CREATE TABLE notifications (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    alert_id        BIGINT         NOT NULL REFERENCES price_alerts(id) ON DELETE CASCADE UNIQUE,
    symbol          VARCHAR(32)    NOT NULL,
    condition       VARCHAR(20)    NOT NULL,
    target_price    NUMERIC(30,10) NOT NULL,
    triggered_price NUMERIC(30,10) NOT NULL,
    triggered_at    TIMESTAMPTZ    NOT NULL,
    cleared_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);
