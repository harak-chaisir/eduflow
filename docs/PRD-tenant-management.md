# PRD — EduFlow CRM Tenant Management

**Status:** Draft for build
**Owner:** Engineering / Platform
**Applies to:** `com.eduflow.tenant` (+ glue in `security`, `config`, and the existing `user`/`role` modules)
**Stack:** Java 25 · Spring Boot 4.1 · Spring Data JPA · Flyway · PostgreSQL 17 · Spring Security 6 · Thymeleaf

---

## 1. Summary

Tenant Management is the platform-level module that lets the **Platform Super Admin**
onboard, configure, and run the lifecycle of each consultancy ("tenant") on EduFlow.
A tenant is the isolation boundary for *everything else* in the product — students,
documents, applications, staff — so this module owns the record that every other
table points at via `tenant_id`.

It answers two questions:

> **For the platform operator:** "Which consultancies exist, what plan are they on, and what state is each in?"
>
> **For the system:** "Given this authenticated request, which tenant's data may it touch — and is that tenant allowed to operate right now?"

The existing schema already has a `tenants` table (migration `V1`) with slug, status,
and audit columns. This PRD specifies the management layer on top of it (lifecycle,
provisioning, settings, plans, access control) and the **additive** schema changes
needed — never modifying `V1`.

---

## 2. Business context & value

EduFlow is sold to consultancies as a SaaS subscription. The tenant record is where the
commercial relationship and the technical isolation meet, so this module directly
protects revenue and data safety.

| Problem without it | Cost | How the module addresses it |
|---|---|---|
| Onboarding a new client is a manual DB/script job | Slow sales-to-live; error-prone seed data | One provisioning action creates the tenant, its defaults, and its first admin |
| No clean way to stop a non-paying client | Either keep serving free, or destroy data and lose them forever | **Suspend** blocks access while retaining data for instant reactivation |
| Churned clients linger with live access | Compliance/data-retention risk; security surface | **Offboard** path with retention window and export |
| Plans aren't modelled | Can't enforce limits or upsell | `plan` + limits on the tenant, enforced where resources are created |
| Isolation depends on every developer remembering `tenant_id` | One missed filter = cross-tenant data leak | Tenant context is resolved once, centrally, and the active tenant's *status* gates access |

### Representative business cases

1. **Onboard a new consultancy.** Sales closes "Bright Future Education". The super
   admin creates the tenant (name, slug, plan = Professional, primary contact) and the
   system provisions defaults and invites the first `TENANT_ADMIN` — live in minutes,
   not a support ticket.
2. **Suspend for non-payment.** A tenant's invoice lapses. The super admin moves it to
   `SUSPENDED`; all its users are blocked at login, but no data is touched. On payment,
   one transition to `ACTIVE` restores them exactly as they were.
3. **Offboard a churned client.** A consultancy cancels. The tenant goes `INACTIVE`,
   their data is retained for a defined grace period and made exportable, then
   offboarded per policy — satisfying both the client and data-protection obligations.
4. **Upgrade a plan.** A growing tenant hits the Starter staff limit; moving them to
   Professional lifts the cap and unlocks features, captured on the tenant record for
   billing reconciliation.
5. **Support troubleshooting.** A tenant admin reports a misconfiguration. The super
   admin views the tenant's profile and settings (read) to diagnose, with every view of
   sensitive scope auditable.

---

## 3. Goals & non-goals

**Goals**
- Super-admin CRUD over tenants: create (with provisioning), list/filter, view, update.
- A guarded tenant status lifecycle (`ACTIVE` / `SUSPENDED` / `INACTIVE`).
- Self-service profile & settings editing for a tenant's own `TENANT_ADMIN`.
- Plans with limits, and a single place other modules enforce those limits.
- Tenant status gating at authentication (suspended/inactive tenants can't operate).
- Clean integration with the document module's per-tenant Drive root folder.

**Non-goals (this iteration)**
- Billing/payment processing and invoicing (V2 SaaS billing — this module only stores
  `plan` and limits).
- Public self-service signup (super-admin-initiated onboarding only for now).
- Schema-per-tenant or database-per-tenant isolation (stays shared-schema + `tenant_id`).
- White-labelling delivery (settings model leaves room; rendering is future work).

---

## 4. Personas & permissions

| Capability | `SUPER_ADMIN` | `TENANT_ADMIN` (own tenant) | Other roles |
|---|:--:|:--:|:--:|
| List all tenants | ✓ | — | — |
| Create / provision tenant | ✓ | — | — |
| View any tenant | ✓ | own only | — |
| Update profile (name, contact) | ✓ | own only | — |
| Update settings (branding, locale, defaults) | ✓ | own only | — |
| Change status (suspend/activate/deactivate) | ✓ | — | — |
| Change plan / limits | ✓ | — | — |
| Invite first tenant admin | ✓ | — | — |

`SUPER_ADMIN` is the only role not scoped to a tenant; all others operate strictly
within their own tenant and can never name a tenant id other than their own.

---

## 5. Domain model

```
Tenant (extends BaseEntity)
  ├─ name                 String        (display name of the consultancy)
  ├─ slug                 String UNIQUE (URL-safe, immutable after creation)
  ├─ status               TenantStatus  (ACTIVE | SUSPENDED | INACTIVE)
  ├─ plan                 TenantPlan    (STARTER | PROFESSIONAL | ENTERPRISE)
  ├─ maxStudents          Integer       (limit derived from plan; overridable)
  ├─ maxStaffUsers        Integer       (limit derived from plan; overridable)
  ├─ primaryContactName   String
  ├─ primaryContactEmail  String
  ├─ primaryContactPhone  String?
  ├─ driveRootFolderId    String?       (per-tenant Drive root; see §10.3)
  ├─ locale               String        (e.g. en-NP)
  ├─ timezone             String        (e.g. Asia/Kathmandu)
  ├─ suspendedAt          Instant?
  ├─ suspensionReason     String?
  └─ deactivatedAt        Instant?
```

**Enums**
- `TenantStatus` — `ACTIVE`, `SUSPENDED`, `INACTIVE` (matches the project's existing
  enum). Transition rules are encoded on the enum (see §6), mirroring how
  `DocumentStatus` works in the document module.
- `TenantPlan` — `STARTER`, `PROFESSIONAL`, `ENTERPRISE`, each carrying default
  `maxStudents` / `maxStaffUsers` so creation can seed sensible limits.

Tenant-scoped settings beyond the core profile (branding colour, logo reference,
default notification channels, default workflow template) live in a small
`tenant_settings` table keyed 1:1 to the tenant, keeping the hot `tenants` row lean and
letting the settings surface grow without churning the core table.

---

## 6. Tenant lifecycle & status transitions

```
                ┌────────── suspend ──────────┐
                ▼                              │
   ┌─────────────────┐  reactivate   ┌──────────────────┐
   │     ACTIVE       │◄──────────────┤    SUSPENDED      │
   └───────┬─────────┘                └──────────────────┘
           │  deactivate (offboard)          ▲
           ▼                                 │ (cannot go SUSPENDED→INACTIVE directly)
   ┌─────────────────┐  reactivate           │
   │    INACTIVE      │───────────────────────┘ → ACTIVE
   └─────────────────┘
```

| From → To | Allowed | Meaning |
|---|:--:|---|
| `ACTIVE` → `SUSPENDED` | ✓ | Temporary hold (e.g. non-payment); reversible |
| `SUSPENDED` → `ACTIVE` | ✓ | Restore after hold cleared |
| `ACTIVE` → `INACTIVE` | ✓ | Offboard / churn |
| `INACTIVE` → `ACTIVE` | ✓ | Win-back / reactivation |
| `SUSPENDED` → `INACTIVE` | ✗ | Must reactivate then deactivate (keeps reason history clean) |
| same → same | ✗ | No-op rejected |

- Encoded on `TenantStatus.canTransitionTo(...)`; the service validates and throws
  `InvalidTenantStatusTransitionException` on a violation.
- `SUSPENDED` and `INACTIVE` stamp `suspendedAt`/`deactivatedAt` and (for suspension) a
  required `suspensionReason`; reactivation clears them.

---

## 7. Provisioning flow (tenant creation)

Creation is deliberately a **simple transaction** plus a few decoupled follow-ups, so it
can't half-fail in a way that leaves an unusable tenant.

1. **Validate** — slug is unique and URL-safe; plan is known.
2. **Persist tenant** (single DB transaction) — status `ACTIVE`, limits seeded from the
   plan, locale/timezone defaulted (e.g. `Asia/Kathmandu`).
3. **Seed settings row** — default branding/notification settings in the same tx.
4. **Drive root — lazy, not eager.** No Drive call at creation. `driveRootFolderId`
   stays null and the document module's `DriveFolderProvisioningService` resolves the
   tenant's tree under the global app root on first upload. If an operator later sets a
   dedicated per-tenant Shared Drive folder, it overrides the global root (§10.3). This
   avoids a distributed-transaction problem at onboarding.
5. **Invite first tenant admin (after commit)** — publish `TenantCreated`; a listener in
   the user module creates a `TENANT_ADMIN` in `PENDING_VERIFICATION` and emails a
   set-password invite. Because it runs `AFTER_COMMIT`, a rolled-back creation never
   sends an invite.

> Rationale: the only step that talks to an external system (Drive) is deferred and
> idempotent, so tenant creation needs no saga/compensation. This also resolves the
> "persist a per-tenant Drive root" item flagged in the document module PRD (§14).

---

## 8. Tenant settings & configuration

`tenant_settings` (1:1 with tenant) holds operator-tunable, non-identifying config:

| Setting | Purpose | Default |
|---|---|---|
| `brand_color` | UI accent for light white-labelling | platform teal |
| `logo_reference` | Drive/asset reference for the tenant logo | none |
| `default_notification_channels` | Channels enabled for triggers (EMAIL/SMS/WHATSAPP) | EMAIL |
| `default_workflow_template_id` | Workflow applied to new students | platform default |
| `required_documents_override` | Tenant-specific required-document set | null → enum default |

`required_documents_override` is the hook that lets the document module's dossier
readiness become **tenant-configurable** (its current default lives on the
`DocumentType` enum). When present, the document module reads the required set from here
instead.

Tenant admins edit their own settings; super admins can edit any. Plan and status are
**not** settings — they're commercial/lifecycle fields restricted to super admin.

---

## 9. Plans, limits & quota enforcement

| Plan | maxStudents | maxStaffUsers | Notes |
|---|---|---|---|
| `STARTER` | 250 | 5 | Single-branch consultancies |
| `PROFESSIONAL` | 2,000 | 25 | Multi-counselor offices |
| `ENTERPRISE` | unlimited (null) | unlimited (null) | Negotiated |

- Defaults are seeded from the plan at creation but stored on the tenant so an
  individual deal can override them.
- **Enforcement is cross-cutting:** the student and user modules check the limit before
  creating a record, via a small `TenantLimitService.assertCanAddStudent(tenantId)` /
  `assertCanAddStaff(tenantId)`. Centralising the check keeps the rule out of each
  create path. Exceeding a limit returns `409 CONFLICT` with a clear message.

---

## 10. Multi-tenancy & how tenant context drives isolation

### 10.1 Resolving the tenant
The active tenant is resolved from the authenticated principal — never from a path,
query, or body parameter. This reuses the same seam the document module introduced:
the principal implements `TenantPrincipal` and `CurrentUser.tenantId()` is the single
source of truth. Tenant Management *owns* that contract; every other module consumes it.

### 10.2 Status gating at authentication
A tenant's status is an access gate, enforced once rather than per query:
- On login / token validation, if the user's tenant is `SUSPENDED` or `INACTIVE`,
  authentication is rejected with a clear, non-leaking message ("This workspace is not
  active. Contact your administrator.").
- `SUPER_ADMIN` is exempt (no tenant) and can always operate.
- Implemented in the security layer (a check in the `UserDetailsService` /
  authentication provider, or a thin filter), so suspending a tenant takes effect on the
  next request without touching any business code.

### 10.3 Per-tenant Drive root (integration with the document module)
`DriveFolderProvisioningService` currently nests every tenant under the global app root
and caches the tenant container in memory. With this module, it will prefer
`tenant.driveRootFolderId` when set, falling back to the global root when null. That
gives operators the option of a dedicated Shared Drive per tenant for stricter
isolation, with zero change to the upload flow.

### 10.4 Super-admin acting within a tenant
A super admin has no tenant of their own, so to operate *inside* a tenant (support,
impersonation) they need an explicit **tenant-context switch** (select a tenant for the
session). This is preferable to silently widening queries and keeps the "tenant from
context" rule intact. Scoped as a defined feature rather than an implicit bypass.

---

## 11. Suspension & offboarding behaviour

| State | Login/API | Data | Billing intent |
|---|---|---|---|
| `ACTIVE` | allowed | live | subscribed |
| `SUSPENDED` | blocked | retained, untouched | temporary hold; expected to return |
| `INACTIVE` | blocked | retained for grace window, then offboarded; exportable | churned |

- Suspension is **non-destructive and instantly reversible** — the core revenue-
  protection lever.
- Offboarding (`INACTIVE`) starts a retention clock; during it, an operator can export
  the tenant's data (students, document metadata, applications) before any deletion.
  Actual deletion is a separate, audited, policy-driven job (out of scope here, but the
  state machine sets it up).

---

## 12. Security & access control

- `@PreAuthorize("hasRole('SUPER_ADMIN')")` on create, list-all, status change, and plan
  change.
- Profile/settings reads and writes allow `SUPER_ADMIN` or the tenant's own
  `TENANT_ADMIN`; "own tenant" is verified against `CurrentUser.tenantId()`, not a path
  param, so a tenant admin cannot address another tenant's id.
- No credential or internal field (e.g. any future billing token) appears in a response
  DTO.
- Slug is immutable post-creation (it can appear in URLs/links); name and contact are
  editable.

---

## 13. Events & audit

Lifecycle changes publish domain events (same pattern as the document module), consumed
by the audit module and, later, billing/notification:

| Event | When | Consumers |
|---|---|---|
| `TenantCreated` | provisioning committed | user module (invite admin), audit |
| `TenantStatusChanged` | any valid transition | audit, billing (future), notifications |
| `TenantPlanChanged` | plan/limit change | audit, billing (future) |

Audit listeners run transactionally (`@EventListener`); side-effecting consumers
(invite email, billing sync) run `@TransactionalEventListener(AFTER_COMMIT)`. Every
record lands in the append-only `audit_events` table.

---

## 14. API contract

| Method | Path | Role | Body / notes |
|---|---|---|---|
| GET | `/api/v1/tenants` | `SUPER_ADMIN` | pageable; filter by `status`, `plan`, `q` (name/slug) |
| POST | `/api/v1/tenants` | `SUPER_ADMIN` | `CreateTenantRequest` → provisions + invites admin |
| GET | `/api/v1/tenants/{id}` | `SUPER_ADMIN` | full tenant view |
| PATCH | `/api/v1/tenants/{id}` | `SUPER_ADMIN` / own `TENANT_ADMIN` | `UpdateTenantProfileRequest` (name, contact) |
| PATCH | `/api/v1/tenants/{id}/status` | `SUPER_ADMIN` | `{ status, reason? }` (reason required for SUSPENDED) |
| PATCH | `/api/v1/tenants/{id}/plan` | `SUPER_ADMIN` | `{ plan, maxStudents?, maxStaffUsers? }` |
| GET | `/api/v1/tenants/me` | any tenant user | current tenant profile (read) |
| GET | `/api/v1/tenants/{id}/settings` | `SUPER_ADMIN` / own `TENANT_ADMIN` | settings view |
| PATCH | `/api/v1/tenants/{id}/settings` | `SUPER_ADMIN` / own `TENANT_ADMIN` | `UpdateTenantSettingsRequest` |
| POST | `/api/v1/tenants/{id}/admins` | `SUPER_ADMIN` | invite an additional `TENANT_ADMIN` |

Errors use the project's standard envelope via `GlobalExceptionHandler`
(`TenantNotFoundException` → 404, `InvalidTenantStatusTransitionException` → 409,
`DuplicateSlugException` → 409, `TenantLimitExceededException` → 409).

A staff-facing Thymeleaf surface (super-admin "Tenants" screen, tenant-admin "Workspace
settings") reuses the shared `fragments/layout.html` shell from the UI redesign.

---

## 15. Data model & migrations

The `tenants` table exists (`V1`). Add the new lifecycle/commercial/config columns and
the settings table in a **new** migration (the next free version after your current
highest — e.g. `V6`), never editing `V1`. Reconcile column names with your actual `V1`.

- `V6__extend_tenants_and_add_settings.sql`
  - `ALTER TABLE tenants ADD COLUMN` for: `name` (if absent), `plan`, `max_students`,
    `max_staff_users`, `primary_contact_name`, `primary_contact_email`,
    `primary_contact_phone`, `drive_root_folder_id`, `locale`, `timezone`,
    `suspended_at`, `suspension_reason`, `deactivated_at`.
  - `CHECK` constraints: `chk_tenants_status` (ACTIVE/SUSPENDED/INACTIVE),
    `chk_tenants_plan` (STARTER/PROFESSIONAL/ENTERPRISE).
  - `uq_tenants_slug` if not already unique.
  - `CREATE TABLE tenant_settings` (1:1, `tenant_id` UUID PK/FK, settings columns,
    full audit columns, `COMMENT ON`).

Follows project Flyway conventions: `TIMESTAMPTZ`, UUID PKs, named constraints,
`COMMENT ON TABLE`/`COLUMN`, indexes on FK columns.

---

## 16. Testing strategy

- **`@DataJpaTest`** — slug uniqueness; status filter; settings 1:1 relationship.
- **Service unit tests (Mockito)** — provisioning seeds limits from plan; transition
  guard (each allowed/blocked pair from §6); suspension requires a reason; event
  publication; limit assertions.
- **`@WebMvcTest` + `@MockitoBean`** — `@PreAuthorize` matrix (super-admin-only
  endpoints reject tenant admins; tenant admin can read/update *only* its own tenant);
  status payload validation.
- **Security integration** — a user whose tenant is `SUSPENDED`/`INACTIVE` is denied;
  `SUPER_ADMIN` is exempt.
- Naming: `methodName_stateUnderTest_expectedBehaviour`.

---

## 17. Risks & trade-offs

| Decision | Trade-off | Mitigation / future |
|---|---|---|
| Shared schema, row-level isolation via `tenant_id` | A missed filter risks cross-tenant leakage | Tenant resolved centrally via `CurrentUser`; status gate at auth; repository tests assert isolation; consider Hibernate `@TenantId`/filters later |
| Lazy Drive provisioning at creation | Tenant's folder tree first appears on first upload | Idempotent and avoids a distributed transaction; operator can pre-set `driveRootFolderId` |
| Suspension blocks at auth, not per-query | A long-lived session could outlast suspension until next auth | Short token lifetimes / per-request status check; acceptable for near-real-time enforcement |
| Slug immutable | Re-brands keep the old slug | Display name is editable; slug is an internal/URL key |
| Super-admin tenant switch instead of query-widening | Extra step for cross-tenant work | Keeps "tenant from context" invariant intact; explicit and auditable |
| Plan limits enforced in each create path | Requires a shared `TenantLimitService` call | One small service centralises the rule; 409 on breach |

---

## 18. Future work

- SaaS billing integration (Stripe-style): map `plan`/limits to subscriptions, dunning
  drives suspension automatically.
- Public self-service signup with email verification → provisions a `STARTER` tenant.
- White-labelling delivery (custom domain, full theming from `tenant_settings`).
- Per-tenant Shared Drives wired through `driveRootFolderId` by default.
- Automated offboarding/export job with retention policy.
- Tenant usage metrics and quota dashboards (students/staff/storage vs. plan).
