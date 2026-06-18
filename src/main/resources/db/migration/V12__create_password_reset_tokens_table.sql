-- ─────────────────────────────────────────────────────────────────────────────
-- V12: Password reset / set-password tokens.
--      Issued when a tenant admin is invited (PENDING_VERIFICATION) so they can
--      set their initial password, and reusable for password resets. A token is
--      single-use (used_at) and time-bound (expires_at).
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE password_reset_tokens (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL,
    token       VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,

    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),

    CONSTRAINT pk_password_reset_tokens        PRIMARY KEY (id),
    CONSTRAINT uq_password_reset_tokens_token  UNIQUE (token),
    CONSTRAINT fk_password_reset_tokens_user   FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens (user_id);
CREATE INDEX idx_password_reset_tokens_token   ON password_reset_tokens (token);

COMMENT ON TABLE  password_reset_tokens         IS 'Single-use, time-bound tokens for set-password / password-reset flows';
COMMENT ON COLUMN password_reset_tokens.used_at IS 'When the token was consumed; NULL means still valid';
