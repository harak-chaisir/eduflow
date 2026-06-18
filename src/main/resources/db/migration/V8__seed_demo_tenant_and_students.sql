-- ─────────────────────────────────────────────────────────────────────────────
-- V8: Seed a demo consultancy tenant with staff and sample students so the
--     student module is populated and demoable out of the box.
--
-- ⚠️ DEV ONLY — these accounts use weak, well-known passwords.
--    Remove or replace this seed in a production environment.
--    Credentials: admin@brightfuture.com / Demo@1234  (ROLE_TENANT_ADMIN)
--                 counselor@brightfuture.com / Demo@1234  (ROLE_COUNSELOR)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. Demo consultancy tenant (fixed UUID for deterministic references below).
INSERT INTO tenants (id, name, slug, status, created_by)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'Bright Future Education',
    'bright-future',
    'ACTIVE',
    'system'
)
ON CONFLICT (id) DO NOTHING;

-- 2. Staff users for the demo tenant (tenant admin + counselor).
INSERT INTO users (id, email, password_hash, first_name, last_name, tenant_id, status, email_verified, created_by)
VALUES
    ('22222222-2222-2222-2222-222222222222',
     'admin@brightfuture.com',
     crypt('Demo@1234', gen_salt('bf', 10)),
     'Asha', 'Sharma',
     '11111111-1111-1111-1111-111111111111',
     'ACTIVE', TRUE, 'system'),
    ('33333333-3333-3333-3333-333333333333',
     'counselor@brightfuture.com',
     crypt('Demo@1234', gen_salt('bf', 10)),
     'Bikash', 'Thapa',
     '11111111-1111-1111-1111-111111111111',
     'ACTIVE', TRUE, 'system')
ON CONFLICT (id) DO NOTHING;

-- 3. Grant roles to the demo staff.
INSERT INTO user_roles (user_id, role_id, granted_by)
SELECT '22222222-2222-2222-2222-222222222222', r.id, 'system'
FROM   roles r WHERE r.name = 'ROLE_TENANT_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id, granted_by)
SELECT '33333333-3333-3333-3333-333333333333', r.id, 'system'
FROM   roles r WHERE r.name = 'ROLE_COUNSELOR'
ON CONFLICT DO NOTHING;

-- 4. Sample students across the LEAD → ENROLLED pipeline.
--    Some are assigned to the demo counselor (33333333…).
INSERT INTO students (id, tenant_id, first_name, last_name, email, phone, date_of_birth,
                      gender, nationality, city, country, status, assigned_counselor_id, created_by)
VALUES
    ('a0000001-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111',
     'Sita', 'Gurung', 'sita.gurung@example.com', '+977 9801000001', TIMESTAMPTZ '2004-03-12 00:00:00+00',
     'FEMALE', 'Nepali', 'Pokhara', 'Nepal', 'LEAD',
     '33333333-3333-3333-3333-333333333333', 'system'),
    ('a0000002-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111',
     'Ram', 'Bahadur', 'ram.bahadur@example.com', '+977 9801000002', TIMESTAMPTZ '2003-07-22 00:00:00+00',
     'MALE', 'Nepali', 'Kathmandu', 'Nepal', 'LEAD',
     NULL, 'system'),
    ('a0000003-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111',
     'Anita', 'Rai', 'anita.rai@example.com', '+977 9801000003', TIMESTAMPTZ '2002-11-05 00:00:00+00',
     'FEMALE', 'Nepali', 'Biratnagar', 'Nepal', 'QUALIFIED',
     '33333333-3333-3333-3333-333333333333', 'system'),
    ('a0000004-0000-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111',
     'Kiran', 'Shrestha', 'kiran.shrestha@example.com', '+977 9801000004', TIMESTAMPTZ '2001-01-18 00:00:00+00',
     'MALE', 'Nepali', 'Lalitpur', 'Nepal', 'ACTIVE',
     '33333333-3333-3333-3333-333333333333', 'system'),
    ('a0000005-0000-0000-0000-000000000005', '11111111-1111-1111-1111-111111111111',
     'Maya', 'Tamang', 'maya.tamang@example.com', '+977 9801000005', TIMESTAMPTZ '2000-09-30 00:00:00+00',
     'FEMALE', 'Nepali', 'Bhaktapur', 'Nepal', 'ACTIVE',
     NULL, 'system'),
    ('a0000006-0000-0000-0000-000000000006', '11111111-1111-1111-1111-111111111111',
     'Prakash', 'Koirala', 'prakash.koirala@example.com', '+977 9801000006', TIMESTAMPTZ '1999-05-14 00:00:00+00',
     'MALE', 'Nepali', 'Butwal', 'Nepal', 'ENROLLED',
     '33333333-3333-3333-3333-333333333333', 'system'),
    ('a0000007-0000-0000-0000-000000000007', '11111111-1111-1111-1111-111111111111',
     'Deepa', 'Karki', 'deepa.karki@example.com', '+977 9801000007', TIMESTAMPTZ '2002-02-28 00:00:00+00',
     'FEMALE', 'Nepali', 'Dharan', 'Nepal', 'ENROLLED',
     '33333333-3333-3333-3333-333333333333', 'system'),
    ('a0000008-0000-0000-0000-000000000008', '11111111-1111-1111-1111-111111111111',
     'Suresh', 'Magar', 'suresh.magar@example.com', '+977 9801000008', TIMESTAMPTZ '2003-12-01 00:00:00+00',
     'MALE', 'Nepali', 'Hetauda', 'Nepal', 'INACTIVE',
     NULL, 'system')
ON CONFLICT (id) DO NOTHING;

-- 5. A few interested-country / interested-course rows for richer detail pages.
INSERT INTO student_interested_countries (student_id, country) VALUES
    ('a0000001-0000-0000-0000-000000000001', 'United Kingdom'),
    ('a0000001-0000-0000-0000-000000000001', 'Australia'),
    ('a0000003-0000-0000-0000-000000000003', 'Canada'),
    ('a0000004-0000-0000-0000-000000000004', 'Australia'),
    ('a0000006-0000-0000-0000-000000000006', 'United States')
ON CONFLICT DO NOTHING;

INSERT INTO student_interested_courses (student_id, course) VALUES
    ('a0000001-0000-0000-0000-000000000001', 'Computer Science'),
    ('a0000003-0000-0000-0000-000000000003', 'Business Administration'),
    ('a0000004-0000-0000-0000-000000000004', 'Data Science'),
    ('a0000006-0000-0000-0000-000000000006', 'Mechanical Engineering')
ON CONFLICT DO NOTHING;
