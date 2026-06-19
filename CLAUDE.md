# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

EduFlow CRM — a multi-tenant SaaS for education consultancies, managing the student lifecycle
(Lead → Registration → Documents → Applications → Offer Letter → Visa → Enrolled). Spring Boot
4.1 / Java 25 / PostgreSQL 17, server-rendered with Thymeleaf plus a JSON REST API under `/api/v1`.

> `.github/copilot-instructions.md` is the authoritative spec for conventions and the *intended*
> full domain model. Many modules it lists (`university`, `application`, `visa`, `workflow`, `task`,
> `notification`, `report`, `config`) are **planned but not yet built** — only `tenant`, `user`,
> `role`, `student`, `document`, `audit`, `security`, `common`, `web`, and `exception` exist today.
> Read that file before adding a new module so naming/layout matches the plan.

## Commands

```bash
# Database must be running before the app or any non-mocked test
docker compose up -d                      # start PostgreSQL 17 on localhost:5432
docker compose down -v && docker compose up -d   # wipe data + restart fresh

./mvnw spring-boot:run                    # run app at http://localhost:8080
./mvnw test                               # full test suite
./mvnw test -Dtest=StudentServiceTest     # single test class
./mvnw test -Dtest=StudentServiceTest#registerStudent_whenEmailExists_throws  # single method
./mvnw package -DskipTests                # build jar

# Frontend assets (build:css also runs automatically in Maven's generate-resources phase)
npm run build:css                         # one-shot minified Tailwind build
npm run watch:css                         # rebuild Tailwind on change during dev
npm run build:htmx                        # copy htmx.min.js into static/js/
npm run build:icons                       # copy lucide.js into static/js/
npm run build                             # build:css + build:icons + build:htmx
```

Dev login (seeded by Flyway `V4`): `admin@eduflow.com` / `Admin@1234` (ROLE_SUPER_ADMIN).

DataSource is hardcoded for local dev in `application.properties`: `eduflow` / `eduflow` @ `localhost:5432/eduflow`.

## Architecture

**Package-by-feature.** Each domain (`com.eduflow.student`, `.document`, …) is self-contained:
entity, repository, service, controller, enums, exceptions, and a `dto/` sub-package live together.
`com.eduflow.web` holds the Thymeleaf `@Controller`s (`StudentWebController`, `DashboardController`,
`AuthController`); `@RestController`s live in their own domain package and serve `/api/v1/...`.

**Multi-tenancy is the central invariant.** Every business entity carries `tenant_id NOT NULL`.
The tenant ID is *always* resolved from the authenticated principal, never from a request param:
services cast `SecurityContextHolder...getPrincipal()` to `EduFlowUserDetails` and call
`getTenantId()` / `getUserId()` (see the `resolvedTenantId()` / `principal()` helpers in
`StudentService`). Repositories must filter by tenant — use `findByIdAndTenantId(...)` style
methods, and `StudentSpecification.from(criteria, tenantId)` always ANDs in the tenant predicate.
A query that omits the tenant filter is a security bug. `ROLE_SUPER_ADMIN` is the only role allowed
to bypass it (note: this bypass is not yet implemented in `StudentService` — it assumes a tenant).

**Auditing is two-layered, don't confuse them:**
- `BaseEntity` (in `common/`) gives every entity a UUID PK + `created_at/by`, `updated_at/by`,
  populated automatically by Spring Data JPA auditing. `JpaAuditingConfig` wires `AuditorAware`
  to the security principal's name (falls back to `"system"`). **Never declare `id` or audit
  columns on an entity** — extend `BaseEntity`.
- `audit/AuditService.publish(...)` writes immutable rows to the append-only `audit_events` table
  for state-changing business actions. It runs in `Propagation.REQUIRES_NEW` so the audit survives
  even if the parent transaction later rolls back. Call it after create/update/status-change.

**Schema is owned by Flyway, not Hibernate** (`ddl-auto=validate`). Add schema changes only as new
`src/main/resources/db/migration/V{n}__snake_desc.sql` files — never edit an applied migration,
never switch `ddl-auto`. Highest current version is `V7`. Migrations use `TIMESTAMPTZ`, `UUID`
PKs, enums stored as `VARCHAR` with `CHECK` constraints, and the `pk_`/`fk_`/`uq_`/`chk_`/`idx_`
naming conventions described in the copilot instructions.

**Status state machines live in the service layer.** Each domain with a lifecycle keeps an
`ALLOWED_TRANSITIONS` map (e.g. `StudentService`) and throws an
`Invalid*StatusTransitionException` for illegal moves — transitions are never trusted from input.

**Security:** form login at `/login` using `email` as the username field, BCrypt strength 10,
method-level `@PreAuthorize` (`@EnableMethodSecurity`). `EduFlowUserDetails` adapts the `User`
entity and exposes `getTenantId()`/`getUserId()`. Authorities are the raw role names
(`ROLE_SUPER_ADMIN`, `ROLE_TENANT_ADMIN`, `ROLE_COUNSELOR`, `ROLE_DOC_OFFICER`,
`ROLE_VISA_OFFICER`, `ROLE_STUDENT`) — so `@PreAuthorize("hasRole('COUNSELOR')")`.

**Errors:** `exception/GlobalExceptionHandler` (`@RestControllerAdvice`) maps domain exceptions to
a consistent `ErrorResponse` envelope (`status`, `error`, `message`, `timestamp`, `path`).

**Frontend is Thymeleaf + Tailwind CSS + HTMX — no SPA, no build-step JS framework.**
- **Tailwind** (v3, `tailwind.config.js`) generates utilities into `static/css/tailwind.css` from
  the input file `src/main/frontend/tailwind.css`. The config scans `templates/**/*.html` for class
  names and extends the theme with the EduFlow `brand`/`accent` palettes and the `sans`/`display`/
  `mono` font families. The brand colors map 1-to-1 to the CSS custom-property design tokens in the
  hand-written `static/css/eduflow.css` — keep the two in sync if you touch either. `@tailwindcss/forms`
  is enabled. Lucide icons are rendered from `<i data-lucide="...">` elements via `static/js/lucide.js`.
- **HTMX** (v2, `static/js/htmx.min.js`) drives all in-page interactivity — live search, pagination,
  status changes, row deletes — through `hx-get`/`hx-post`/`hx-target`/`hx-trigger` attributes on
  Thymeleaf templates (use `th:hx-get`/`th:hx-post` when the URL needs Thymeleaf expression building).
  The server responds with a **Thymeleaf fragment, not a full page**: a `@Controller` method returns
  e.g. `"students/list :: studentResults"` when the request is HTMX, and the full `"students/list"`
  view otherwise. Branch on the `HX-Request` header (`@RequestHeader(value = "HX-Request", required = false)`)
  to decide — see `StudentWebController` and `DocumentViewController`.
- **CSRF for HTMX:** the layout exposes the token via `<meta name="csrf-token">` / `<meta name="csrf-header">`,
  and `static/js/eduflow.js` adds it to every request on the `htmx:configRequest` event. After a swap,
  `htmx:afterSwap` re-initialises Lucide icons and re-binds menu behaviour — re-bind any new JS-driven
  widgets there too, since swapped-in DOM is not covered by initial `DOMContentLoaded` handlers.
- **Layout:** all pages render through the `fragments/layout.html :: layout(pageTitle, breadcrumb, activeNav, content)`
  fragment (sidebar + topbar + content slot). Page-specific scripts/styles live in `eduflow.css`/`eduflow.js`,
  not inline.

## Conventions that bite if missed

- All persisted timestamps are `Instant`, never `Date`/`LocalDateTime`. Enum columns use
  `@Enumerated(EnumType.STRING)`. All relationships are `LAZY`.
- Lombok everywhere — no hand-written getters/setters/constructors; `@Slf4j` not `System.out`.
- DTOs: `*Request` (inbound, `@Valid`) / `*Response` (outbound, immutable `@Builder`) in `dto/`.
  Never expose `passwordHash` or any credential/token in a response.
- Controllers never inject repositories — go through a `@Service` (`@Transactional`, read methods
  `@Transactional(readOnly = true)`).

## Testing

- Web slice tests use **standalone MockMvc + Mockito** (`MockMvcBuilders.standaloneSetup`), not
  `@WebMvcTest` — so `@PreAuthorize` is *not* enforced there; access control is covered by
  integration tests. The `GlobalExceptionHandler` is wired in manually to assert status mapping.
- Spring Boot 4.x: use `@MockitoBean` (not the removed `@MockBean`).
- Test method naming: `methodName_stateUnderTest_expectedBehaviour`. Repository tests must use a
  `tenantId` fixture and assert cross-tenant isolation.