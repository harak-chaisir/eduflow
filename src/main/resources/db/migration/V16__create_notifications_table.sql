-- ─────────────────────────────────────────────────────────────────────────────
-- V16: Notification domain (PRD §13, §7.8). Every dispatched notification is
--      persisted (for the in-app feed and for email audit/retry). One row per
--      (recipient, channel).
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE notifications (
    id                 UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id          UUID         NOT NULL,
    recipient_user_id  UUID         NOT NULL,

    type               VARCHAR(40)  NOT NULL,
    channel            VARCHAR(20)  NOT NULL DEFAULT 'IN_APP',
    title              VARCHAR(200) NOT NULL,
    body               TEXT,
    link               VARCHAR(500),
    status             VARCHAR(20)  NOT NULL DEFAULT 'SENT',
    read_at            TIMESTAMPTZ,
    sent_at            TIMESTAMPTZ,

    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         VARCHAR(255),
    updated_by         VARCHAR(255),

    CONSTRAINT pk_notifications        PRIMARY KEY (id),
    CONSTRAINT fk_notifications_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_user   FOREIGN KEY (recipient_user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_notifications_type    CHECK (type IN (
        'STAGE_ENTERED', 'STAGE_COMPLETED', 'DOCUMENT_MISSING',
        'SLA_BREACHED', 'TASK_ASSIGNED', 'TASK_COMPLETED'
    )),
    CONSTRAINT chk_notifications_channel CHECK (channel IN ('EMAIL', 'IN_APP', 'SMS', 'WHATSAPP')),
    CONSTRAINT chk_notifications_status  CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX idx_notifications_tenant_id ON notifications (tenant_id);
CREATE INDEX idx_notifications_recipient ON notifications (recipient_user_id, channel, read_at);

COMMENT ON TABLE  notifications         IS 'Persisted notifications for the in-app feed and email audit/retry (PRD §7.8)';
COMMENT ON COLUMN notifications.channel IS 'IN_APP | EMAIL | SMS | WHATSAPP — one row per recipient per channel';
COMMENT ON COLUMN notifications.read_at IS 'When the recipient marked an IN_APP notification read; NULL = unread';
