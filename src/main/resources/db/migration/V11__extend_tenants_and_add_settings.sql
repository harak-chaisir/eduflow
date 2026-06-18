-- ─────────────────────────────────────────────────────────────────────────────
-- V11: Tenant Management module — extend the tenants table with lifecycle,
--      commercial (plan/limits), contact, locale and storage columns, and add
--      the 1:1 tenant_settings table for operator-tunable configuration.
--
--      Additive only — V1 (tenants) is never modified. chk_tenants_status and
--      uq_tenants_slug already exist in V1 and are not re-declared here.
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. New columns on tenants. Defaults are applied to existing rows by Postgres.
ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS plan                 VARCHAR(50)  NOT NULL DEFAULT 'STARTER',
    ADD COLUMN IF NOT EXISTS max_students         INTEGER,
    ADD COLUMN IF NOT EXISTS max_staff_users      INTEGER,
    ADD COLUMN IF NOT EXISTS primary_contact_name  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS primary_contact_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS primary_contact_phone VARCHAR(50),
    ADD COLUMN IF NOT EXISTS drive_root_folder_id  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS locale               VARCHAR(20)  NOT NULL DEFAULT 'en-NP',
    ADD COLUMN IF NOT EXISTS timezone             VARCHAR(64)  NOT NULL DEFAULT 'Asia/Kathmandu',
    ADD COLUMN IF NOT EXISTS suspended_at         TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS suspension_reason    VARCHAR(500),
    ADD COLUMN IF NOT EXISTS deactivated_at       TIMESTAMPTZ;

-- 2. Plan must be one of the known tiers.
ALTER TABLE tenants
    ADD CONSTRAINT chk_tenants_plan CHECK (plan IN ('STARTER', 'PROFESSIONAL', 'ENTERPRISE'));

COMMENT ON COLUMN tenants.plan                  IS 'Subscription tier: STARTER | PROFESSIONAL | ENTERPRISE';
COMMENT ON COLUMN tenants.max_students          IS 'Max students allowed; NULL = unlimited (ENTERPRISE)';
COMMENT ON COLUMN tenants.max_staff_users       IS 'Max staff users allowed; NULL = unlimited (ENTERPRISE)';
COMMENT ON COLUMN tenants.drive_root_folder_id  IS 'Optional per-tenant Drive root; NULL falls back to the global app root';
COMMENT ON COLUMN tenants.suspended_at          IS 'When the tenant was last moved to SUSPENDED; cleared on reactivation';
COMMENT ON COLUMN tenants.suspension_reason     IS 'Required reason captured when a tenant is suspended';
COMMENT ON COLUMN tenants.deactivated_at        IS 'When the tenant was last moved to INACTIVE; cleared on reactivation';

CREATE INDEX IF NOT EXISTS idx_tenants_plan ON tenants (plan);

-- 3. Backfill plan limits for tenants seeded before this migration (STARTER tier).
UPDATE tenants
SET max_students    = 250,
    max_staff_users = 5
WHERE max_students IS NULL;

-- 4. tenant_settings — 1:1 with tenant, operator-tunable non-identifying config.
CREATE TABLE tenant_settings (
    tenant_id                     UUID         NOT NULL,
    brand_color                   VARCHAR(20),
    logo_reference                VARCHAR(255),
    default_notification_channels VARCHAR(100) NOT NULL DEFAULT 'EMAIL',
    default_workflow_template_id  UUID,
    required_documents_override   TEXT,

    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),

    CONSTRAINT pk_tenant_settings        PRIMARY KEY (tenant_id),
    CONSTRAINT fk_tenant_settings_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE CASCADE
);

CREATE INDEX idx_tenant_settings_tenant_id ON tenant_settings (tenant_id);

COMMENT ON TABLE  tenant_settings                               IS 'Operator-tunable, non-identifying configuration, 1:1 with a tenant';
COMMENT ON COLUMN tenant_settings.brand_color                   IS 'UI accent colour for light white-labelling';
COMMENT ON COLUMN tenant_settings.logo_reference               IS 'Drive/asset reference for the tenant logo';
COMMENT ON COLUMN tenant_settings.default_notification_channels IS 'Comma-separated channels: EMAIL,SMS,WHATSAPP';
COMMENT ON COLUMN tenant_settings.required_documents_override   IS 'Tenant-specific required-document set; NULL falls back to the DocumentType enum default';

-- 5. Seed a default settings row for tenants that already exist.
INSERT INTO tenant_settings (tenant_id, created_by)
SELECT id, 'system' FROM tenants
ON CONFLICT (tenant_id) DO NOTHING;
