-- ─────────────────────────────────────────────────────────────────────────────
-- V4: Seed EduFlow spec roles, create the system tenant, and bootstrap a
--     dev super-admin account.
--
-- ⚠️ DEV ONLY — the admin@eduflow.com account uses a weak password.
--    Change or delete this seed in a production environment.
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. Add the EduFlow CRM domain roles that were not present in V2.
--    Existing roles (ROLE_SUPER_ADMIN, ROLE_ADMIN, ROLE_INSTRUCTOR, ROLE_STUDENT)
--    are left in place and the missing spec roles are inserted.

INSERT INTO roles (id, name, description, created_by)
VALUES
    (gen_random_uuid(), 'ROLE_TENANT_ADMIN', 'Full access within a tenant',             'system'),
    (gen_random_uuid(), 'ROLE_COUNSELOR',    'Register students and manage applications','system'),
    (gen_random_uuid(), 'ROLE_DOC_OFFICER',  'Verify and manage documents',             'system'),
    (gen_random_uuid(), 'ROLE_VISA_OFFICER', 'Manage visa applications',                'system')
ON CONFLICT DO NOTHING;

-- 2. Create the platform system tenant.
--    A fixed UUID is used so downstream seeds can reference it deterministically.

INSERT INTO tenants (id, name, slug, status, created_by)
VALUES (
    'ffffffff-ffff-ffff-ffff-ffffffffffff',
    'EduFlow Platform',
    'eduflow-platform',
    'ACTIVE',
    'system'
)
ON CONFLICT (id) DO NOTHING;

-- 3. Create the dev super-admin user inside the system tenant.
--    Password is hashed using pgcrypto's bcrypt implementation (strength 10).
--    Default credentials: admin@eduflow.com / Admin@1234

INSERT INTO users (id, email, password_hash, first_name, last_name, tenant_id, status, email_verified, created_by)
VALUES (
    gen_random_uuid(),
    'admin@eduflow.com',
    crypt('Admin@1234', gen_salt('bf', 10)),
    'Super',
    'Admin',
    'ffffffff-ffff-ffff-ffff-ffffffffffff',
    'ACTIVE',
    TRUE,
    'system'
)
ON CONFLICT DO NOTHING;

-- 4. Grant ROLE_SUPER_ADMIN to the newly created dev admin.

INSERT INTO user_roles (user_id, role_id, granted_by)
SELECT u.id, r.id, 'system'
FROM   users u
JOIN   roles r ON r.name = 'ROLE_SUPER_ADMIN'
WHERE  u.email = 'admin@eduflow.com'
ON CONFLICT DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────

COMMENT ON TABLE roles IS 'Application roles; tenant_id = NULL denotes a platform-level role';

