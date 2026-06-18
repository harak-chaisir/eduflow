CREATE TABLE roles (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    tenant_id   UUID,                          -- NULL = system-wide role
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),

    CONSTRAINT pk_roles              PRIMARY KEY (id),
    CONSTRAINT uq_roles_name_tenant  UNIQUE (name, tenant_id),
    CONSTRAINT fk_roles_tenant       FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE CASCADE
);

CREATE INDEX idx_roles_tenant_id ON roles (tenant_id);

-- Seed default system-wide roles (tenant_id = NULL → applies to all tenants)
INSERT INTO roles (id, name, description, created_by)
VALUES
    (gen_random_uuid(), 'ROLE_SUPER_ADMIN', 'Full platform access across all tenants', 'system'),
    (gen_random_uuid(), 'ROLE_ADMIN',       'Full access within a tenant',              'system'),
    (gen_random_uuid(), 'ROLE_INSTRUCTOR',  'Can create and manage courses',            'system'),
    (gen_random_uuid(), 'ROLE_STUDENT',     'Can enroll in and attend courses',         'system');

COMMENT ON TABLE  roles           IS 'Application roles; tenant_id = NULL denotes a platform-level role';
COMMENT ON COLUMN roles.tenant_id IS 'NULL for system roles; references tenants.id for tenant-scoped roles';

