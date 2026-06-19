-- ─────────────────────────────────────────────────────────────────────────────
-- V17: Seed the PRD's example "Australia Student Workflow" (7 stages) for the demo
--      tenant (11111111-…) so the builder, execution, and dashboard have real data.
--      Sets it as the tenant's default workflow.
--
--      Fixed UUIDs keep the seed deterministic. Idempotent via ON CONFLICT guards is
--      unnecessary (a migration runs once), but inserts only target the demo tenant.
-- ─────────────────────────────────────────────────────────────────────────────

-- Template
INSERT INTO workflow_templates (id, tenant_id, name, description, country, version,
                                is_active, is_default, is_archived, created_by)
VALUES ('b0000000-0000-0000-0000-000000000001',
        '11111111-1111-1111-1111-111111111111',
        'Australia Student Workflow',
        'Standard Lead → Enrolled process for Australian study applications.',
        'Australia', 1, TRUE, TRUE, FALSE, 'system');

-- Stages (display_order drives the entry point and progression)
INSERT INTO workflow_stages (id, tenant_id, workflow_template_id, name, code, display_order,
                             color, is_active, sla_days, stage_type, owner_role, created_by)
VALUES
 ('b1000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'b0000000-0000-0000-0000-000000000001',
  'Lead',                 'LEAD',        1, '#94a3b8', TRUE,  5, 'NORMAL',            'ROLE_COUNSELOR',     'system'),
 ('b1000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'b0000000-0000-0000-0000-000000000001',
  'Document Collection',  'DOC_COLLECTION', 2, '#3b82f6', TRUE, 7, 'DOCUMENT_STAGE',   'ROLE_DOC_OFFICER',   'system'),
 ('b1000000-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'b0000000-0000-0000-0000-000000000001',
  'Application Submitted','APP_SUBMITTED', 3, '#6366f1', TRUE, 10, 'APPLICATION_STAGE', 'ROLE_COUNSELOR',    'system'),
 ('b1000000-0000-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111', 'b0000000-0000-0000-0000-000000000001',
  'Offer Received',       'OFFER_RECEIVED', 4, '#8b5cf6', TRUE, 14, 'DECISION_STAGE',   'ROLE_COUNSELOR',    'system'),
 ('b1000000-0000-0000-0000-000000000005', '11111111-1111-1111-1111-111111111111', 'b0000000-0000-0000-0000-000000000001',
  'Visa Submitted',       'VISA_SUBMITTED', 5, '#ec4899', TRUE, 14, 'VISA_STAGE',       'ROLE_VISA_OFFICER', 'system'),
 ('b1000000-0000-0000-0000-000000000006', '11111111-1111-1111-1111-111111111111', 'b0000000-0000-0000-0000-000000000001',
  'Visa Approved',        'VISA_APPROVED', 6, '#22c55e', TRUE, 7, 'VISA_STAGE',        'ROLE_VISA_OFFICER', 'system'),
 ('b1000000-0000-0000-0000-000000000007', '11111111-1111-1111-1111-111111111111', 'b0000000-0000-0000-0000-000000000001',
  'Enrolled',             'ENROLLED',    7, '#16a34a', TRUE, NULL, 'FINAL_STAGE',      NULL,                'system');

-- Required documents (PRD §10): Passport, IELTS, Bank Statement, Transcript at Document Collection
INSERT INTO workflow_stage_required_documents (stage_id, document_type) VALUES
 ('b1000000-0000-0000-0000-000000000002', 'PASSPORT'),
 ('b1000000-0000-0000-0000-000000000002', 'IELTS'),
 ('b1000000-0000-0000-0000-000000000002', 'BANK_STATEMENT'),
 ('b1000000-0000-0000-0000-000000000002', 'PLUS_TWO_TRANSCRIPT');

-- Forward transitions (PRD §9)
INSERT INTO workflow_transitions (id, tenant_id, workflow_template_id, from_stage_id, to_stage_id,
                                  transition_type, requires_approval, created_by)
VALUES
 ('b2000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'b0000000-0000-0000-0000-000000000001',
  'b1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0000-000000000002', 'FORWARD', FALSE, 'system'),
 ('b2000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'b0000000-0000-0000-0000-000000000001',
  'b1000000-0000-0000-0000-000000000002', 'b1000000-0000-0000-0000-000000000003', 'FORWARD', FALSE, 'system'),
 ('b2000000-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'b0000000-0000-0000-0000-000000000001',
  'b1000000-0000-0000-0000-000000000003', 'b1000000-0000-0000-0000-000000000004', 'FORWARD', FALSE, 'system'),
 ('b2000000-0000-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111', 'b0000000-0000-0000-0000-000000000001',
  'b1000000-0000-0000-0000-000000000004', 'b1000000-0000-0000-0000-000000000005', 'FORWARD', FALSE, 'system'),
 ('b2000000-0000-0000-0000-000000000005', '11111111-1111-1111-1111-111111111111', 'b0000000-0000-0000-0000-000000000001',
  'b1000000-0000-0000-0000-000000000005', 'b1000000-0000-0000-0000-000000000006', 'FORWARD', TRUE,  'system'),
 ('b2000000-0000-0000-0000-000000000006', '11111111-1111-1111-1111-111111111111', 'b0000000-0000-0000-0000-000000000001',
  'b1000000-0000-0000-0000-000000000006', 'b1000000-0000-0000-0000-000000000007', 'FORWARD', FALSE, 'system');

-- Make it the tenant default (mirror into tenant_settings).
UPDATE tenant_settings
SET default_workflow_template_id = 'b0000000-0000-0000-0000-000000000001'
WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
