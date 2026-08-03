-- Administrator action audit trail (F010-FR-005, PRD §3.7). No ON DELETE
-- CASCADE in either direction: an audit trail that disappears with its
-- subject is not an audit trail (domain-model.md). Phase 1 has no user-
-- delete feature, but the schema holds this invariant regardless.
CREATE TABLE admin_audit_log (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_user_id  BIGINT      NOT NULL REFERENCES users(id),
    action         VARCHAR(32) NOT NULL,
    target_user_id BIGINT      NOT NULL REFERENCES users(id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
