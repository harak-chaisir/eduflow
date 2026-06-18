CREATE TABLE users (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    email          VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    first_name     VARCHAR(100),
    last_name      VARCHAR(100),
    tenant_id      UUID         NOT NULL,
    status         VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     VARCHAR(255),
    updated_by     VARCHAR(255),

    CONSTRAINT pk_users              PRIMARY KEY (id),
    CONSTRAINT uq_users_email_tenant UNIQUE (email, tenant_id),
    CONSTRAINT fk_users_tenant       FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT chk_users_status      CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED', 'PENDING_VERIFICATION'))
);

CREATE INDEX idx_users_tenant_id ON users (tenant_id);
CREATE INDEX idx_users_email     ON users (email);
CREATE INDEX idx_users_status    ON users (status);

-- Junction table: many users ↔ many roles
CREATE TABLE user_roles (
    user_id    UUID NOT NULL,
    role_id    UUID NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    granted_by VARCHAR(255),

    CONSTRAINT pk_user_roles      PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id)
        REFERENCES roles (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_roles_role_id ON user_roles (role_id);

COMMENT ON TABLE  users                 IS 'Platform users; email is unique per tenant';
COMMENT ON COLUMN users.status          IS 'ACTIVE | INACTIVE | LOCKED | PENDING_VERIFICATION';
COMMENT ON COLUMN users.email_verified  IS 'Whether the user has confirmed their e-mail address';
COMMENT ON TABLE  user_roles            IS 'Many-to-many mapping between users and roles';

