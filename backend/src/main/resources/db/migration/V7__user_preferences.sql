-- Persisted registered-user display preferences (PRD F-009, F001-FR-007).
-- 1:1 with users; user_id is both the PK and the FK, and cascades on delete
-- since this is live per-user state, not an audit trail.
CREATE TABLE user_preferences (
    user_id              BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE PRIMARY KEY,
    visible_columns_json TEXT NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
