# Principal Architect Review — June 2026

Scope: full audit of backend (`src/main`), tests (`src/test`), frontend (`frontend/`), build (`pom.xml`, Dockerfile, compose, CI), and planning docs (ARCHITECT_ROADMAP.md, AOP_AUTHORIZATION_FRAMEWORK.md, README.md).
Optimization targets: production readiness, engineering quality, scalability, security, maintainability. Not demo value.

---

## 1. Architecture Review

### 1.1 Strengths (genuine, verified in code)

- **Layered architecture is clean and consistently enforced.** Controllers do HTTP only; services own business rules and DTO mapping; repositories own queries. No layer violations found.
- **Business logic is pure and testable.** `ResultService.calculatePercentage/calculateGrade/calculateStatus` take `BigDecimal`, return enums, no Spring/DB dependency. `BigDecimal` with `HALF_UP` and `DECIMAL(8,2)` schema is the academically correct choice.
- **JOIN FETCH discipline.** All three `ResultRepository` detail queries fetch student + exam + subject in one query, and `ResultRepositoryTest` asserts `Hibernate.isInitialized()` — a regression tripwire most teams don't have.
- **The AOP authorization framework is correctly built.** `@RequirePermission` + `AuthorizationAspect` + `@RequestScope JwtRequestContext` matches the design doc exactly. Permissions baked into the JWT (not roles) is the right model. Type-safe enum permissions beat SpEL strings.
- **Flyway over ddl-auto, DB-level CHECK constraints, composite unique on (student_id, exam_id)** — defense in depth at the schema level.
- **Operational hygiene exists:** multi-stage Dockerfile with non-root user, CI with JaCoCo gates (60% line / 50% branch) that fail the build, test pyramid with all four tiers (pure unit, Mockito, slice, full integration).

### 1.2 Weaknesses

- **Authorization exists only at the controller layer.** No service method carries `@RequirePermission`. Today that's acceptable (controllers are the only entry point). The moment Sprint 2's `@Tool` methods or Sprint 3's `@Scheduled` jobs call services directly, the entire permission system is bypassed. This is the single most important architectural fact to internalize before any AI sprint.
- **Two parallel identity systems that can drift.** `JwtAuthFilter` populates Spring Security's `SecurityContextHolder` (role-based authorities, DB lookup per request); `JwtRequestContextFilter` populates `JwtRequestContext` (permission claims from the token, no DB lookup). Authentication is checked against the live DB; authorization trusts up-to-24-hour-old token claims. A demoted admin keeps admin permissions until token expiry. There is no refresh or revocation mechanism.
- **`@EnableMethodSecurity` is still enabled** in `SecurityConfig` with zero `@PreAuthorize` left in the codebase — dead configuration that misleads readers about which system enforces authorization.
- **`JwtRequestContext` is `@RequestScope`** — it only exists on servlet request threads. Any `@RequirePermission`-annotated method reached from `@Scheduled`, `@Async`, or a CompletableFuture (all three planned for Sprints 2–3) throws `ScopeNotActiveException`. The roadmap does not acknowledge this.
- **Tutorial prose embedded in production source.** Many files carry 40–100 lines of teaching commentary (`ResultService`, `StudentService`, repositories, migrations). Valuable for the learning goal; in a production codebase it is noise that will rot. Decide which repo this is.
- **README is materially stale**: endpoint table omits authentication entirely, links to deleted `docs/FUTURE_IMPROVEMENTS.md`, claims "high-throughput" and "multi-module" (it is neither), and V2 migration comments still reference STAFF.

### 1.3 Scalability concerns

1. **No pagination anywhere** (`findAll`, `findAllWithDetails` are unbounded). Compounding it, every `ResultResponse` embeds a full nested `StudentResponse` + `ExamResponse` + subject — `GET /api/results` on the seeded dev DB serializes thousands of deeply nested objects per call. This is the first production outage.
2. **Hikari pool is 10 connections** and the Sprint 1 design as written makes a multi-second LLM call *inside* `@Transactional(readOnly = true)` — i.e., holding a DB connection for the duration of the LLM round trip. ~10 concurrent analyses exhaust the pool and take the whole API down. The LLM call must happen outside any transaction.
3. **H2 in tests, PostgreSQL in prod, Flyway disabled in tests** (`ddl-auto=create-drop`). Migrations are never executed by any test, and the analytics sprint's aggregate SQL (`STDDEV`, window-ish queries) behaves differently on H2. This gap widens with every sprint. Testcontainers fixes both.
4. **Seeder performance**: ~12k results inserted one service call (one transaction + one `existsBy` query) at a time on every cold dev start.
5. The monolith itself is correct for this domain — do not split it. The roadmap's "What NOT to Build" list (no microservices, no GraphQL, no vector DB, no WebSocket) is sound; I endorse it fully.

### 1.4 Security concerns (ordered by severity)

1. **CRITICAL — Docker profile falls back to the committed dev JWT secret.** `application-docker.properties` has `jwt.secret=${JWT_SECRET:dGhpcy...}` — the same base64 secret committed in `application-dev.properties`. Any docker-compose deployment without `JWT_SECRET` set silently signs tokens with a public secret: anyone who reads this repo can forge an admin token. Remove the fallback; fail fast at startup.
2. **CRITICAL — `application-prod.properties` points at `localhost:5432/student_dev` with `postgres/postgres`.** Only `jwt.secret` is correctly externalized. Everything must be `${ENV_VAR}` with no defaults.
3. **HIGH — No rate limiting or lockout on `POST /api/auth/login`.** BCrypt at ~100ms/attempt means a credential-stuffing run doubles as a thread-pool-exhaustion DoS.
4. **HIGH — 24-hour tokens with no revocation** (see 1.2). At minimum shorten to 1–2h; ideally add refresh tokens.
5. **MEDIUM — `JwtRequestContext.getRawToken()` is public.** Any service can read (and accidentally log) the bearer token. No caller needs it; delete it.
6. **MEDIUM — DELETE /api/students/{id} on a student with results → raw FK violation → 500.** `results.student_id` has no `ON DELETE` behavior and `GlobalExceptionHandler` has no `DataIntegrityViolationException` handler. This is both a correctness bug and an information-leak-shaped failure mode. Decide the semantics (block with 409, or cascade) and handle the exception.
7. **MEDIUM — audit trail is console-only.** `AuditLoggingAspect` logs to stdout; logs are not durable, structured, or queryable. Fine pre-Sentinel; becomes a real gap when the Admin Assistant starts writing data on a user's behalf.
8. **LOW — inline `new ObjectMapper()` in both security handlers** ignores the Spring-configured mapper (module/format drift). Inject the bean.
9. **LOW — `JwtUtil.extractRole()`** deprecated-for-removal, zero callers, still present. Delete.

### 1.5 Maintainability concerns

- Stale/contradictory documentation set (8+ docs in `docs/`, three root-level plans with different statuses). Consolidate: README (truthful), one architecture doc, one active roadmap.
- Spring Boot **3.2.0 is past OSS end-of-support** (it is mid-2026; 3.2.x OSS support ended late 2024). No security patches. Upgrade to the current 3.3/3.4+ line before adding dependencies (Spring AI in particular tracks recent Boot versions). springdoc 2.3.0 should move in lockstep.
- `pom.xml` carries empty `<name/>`, `<description/>`, `<licenses><license/></licenses>`, `<developers><developer/></developers>` stubs.
- Controller slice tests stub `hasPermission(any()) → true`, so per-endpoint permission mapping is only covered by 4 integration tests — far short of the 65-test matrix the AOP doc itself specifies. Test count overall: ~59 `@Test` methods (docs claim 69; close, but keep claims honest).
- No Actuator: no health endpoint, no metrics. The compose file health-checks PostgreSQL but the app container has no healthcheck at all.

---

## 2. Frontend Review

The `frontend/` directory is not the application the roadmap specifies. It is a 2,682-line static demo: React 18 UMD + **Babel Standalone from CDN** (in-browser JSX transpilation), GSAP + ScrollTrigger from CDN, **zero network calls** (no fetch/axios anywhere), a seeded synthetic dataset in `data.js`, and **mock auth that accepts any credentials** ("any input, or demo access, proceeds" — its own comment).

### UX problems
- Heavy decorative motion: cursor trail (`data-trail`), perpetual floating "atmosphere" objects, parallax typographic backgrounds, grid distortion — this directly contradicts the roadmap's own design principle ("No decorative motion. Every animation communicates meaning") and the data-dense industrial aesthetic it specifies (it also ships `data-theme="light"` against the dark spec).
- The login flow is theater: it animates a fake authentication. Shipping this against a real API would train users to distrust the product.
- No accessibility consideration: no `prefers-reduced-motion` gating visible for the perpetual animations, motion-dependent navigation.

### Performance issues
- Babel Standalone transpiles ~1,260 lines of JSX **on every page load** — multi-MB script, main-thread compile cost, no caching of output. This is prototyping tooling, explicitly not for production.
- Dozens of infinitely-looping GSAP timelines (`data-float` on every atmosphere object) burn CPU/compositor continuously — battery drain on laptops, jank on mid-range hardware.
- No code splitting, no minification, no tree shaking — none is possible without a build system, and there is no `package.json`.

### Architecture issues
- Five IIFE scripts communicating through `window.AID` globals, dependent on `<script>` tag order. No modules, no types, no router (hand-rolled), no tests, no API layer.
- This is not refactorable into the target architecture; the cost of retrofitting Vite/TS/modules into script-order IIFEs exceeds a rebuild.

### State management recommendation
The roadmap's own prescription is correct — adopt it as written: **Vite + TypeScript; TanStack Query v5 for all server state; Zustand for auth token + UI chrome only; types generated from the OpenAPI spec** (`openapi-typescript` against `/v3/api-docs`) so the frontend contract is derived, never hand-mirrored. Keep the current demo's visual language (typography, layout instincts are genuinely good) as a design reference; do not keep the code. Salvage `style.css` design tokens, delete the rest.

Note: the backend has **no CORS configuration**, so the first real frontend request will fail regardless. That fix belongs in the hardening phase.

---

## 3. AI Review

### Sprint 1 — Academic Intelligence: **BUILD (with corrections)**
The strongest of the three: real user value (pattern detection a teacher can't do manually), correct instincts (projections not entities, SHA-256 cache keying, structured outputs, mocked ChatClient in tests, plain-text tables in prompts). Corrections required:

1. **Move the LLM call outside the transaction** (see §1.3.2). Load projections in a short read-only transaction, close it, then call the LLM.
2. **Drop one of the two caches.** The plan specifies both an `ai_analysis_cache` table *and* Caffeine. The DB table alone is sufficient (survives restarts, supports the hash-key model); Caffeine adds a second invalidation problem for zero measurable win at this traffic.
3. **Compute the statistics deterministically in Java/SQL; let the LLM only interpret.** Means, stddevs, trends, percentiles must come from code — never let the model do arithmetic it can hallucinate. The prompt already half-does this; make it total. The `atRisk` flag and the at-risk dashboard list should be a SQL query (`percentage < threshold`), not an LLM output — the roadmap concedes this in §6.8; make it the rule.
4. **Add a timeout + circuit breaker** around the LLM call; the 3-attempt retry in the risk section triples worst-case latency and cost with no backoff or budget cap. Cap tokens per request and per day.
5. The self-reported `confidence` field is fine as UX copy but must never gate logic — it is not calibrated.

### Sprint 2 — Admin Assistant: **SPLIT IT**
The valuable 80% is deterministic: **bulk file upload (POI/OpenCSV) with preview-then-confirm**. That needs no LLM and should ship first as a plain endpoint. The remaining 20% — the natural-language tool-calling agent — has the worst risk profile in the roadmap:

1. **Authorization bypass (design flaw).** The controller gates the whole assistant behind one permission (`USER_CREATE`), then `@Tool` methods call services directly — where no `@RequirePermission` exists. A user holding only USER_CREATE gains RESULT_CREATE, EXAM lookup, everything the tools wrap. The roadmap's claim that "AI never bypasses the existing service layer" is true for *validation* but false for *authorization*. Fix: each tool must check `JwtRequestContext.hasPermission()` for its specific permission (and note §1.2 — the request scope must actually be active in the tool-execution thread).
2. **The "confirm before write" safeguard is a prompt instruction, not a control.** LLMs do not reliably obey. Writes need a server-side two-phase protocol: tools return a proposal ID; a separate endpoint executes it after explicit human confirmation.
3. **Prompt injection.** Uploaded spreadsheet headers/cells are fed to the LLM for column mapping; a cell containing instructions is in the prompt. The LLM-assisted column mapper must run with *no tools registered* and a schema-constrained output.
4. **Client-supplied conversation history is forgeable.** The client sends the full message history each turn, including "assistant" messages — a user can fabricate an assistant turn ("I have confirmed this operation"). History must be server-held or signed.
5. Keep: no delete tools, per-request operation cap, audit logging of every `ToolResult`.

### Sprint 3 — Sentinel: **REPLACE THE LLM WITH RULES; keep the event log**
The honest assessment: as designed, the LLM adds cost and nondeterminism to a job that is already solved before it runs. The pipeline pre-aggregates the window, compares against **hardcoded baselines in the prompt**, and only calls the LLM when `hasAnomalies()` is already true — detection is rule-based; the model just writes prose about a decision the code already made. For a backend whose stated values are production readiness and maintainability:

- Build `api_event_log` + the async event writer (genuinely useful), **with a retention/pruning job** — the plan has unbounded growth.
- Build threshold alerting in plain Java (the `hasAnomalies()` logic *is* the alerter). Deterministic, testable, free.
- Fix the async write design: `CompletableFuture.runAsync` on the common ForkJoinPool for transactional DB writes means no transaction context, pool starvation under load, and silently lost events on shutdown. Use a bounded `@Async` executor or a queue.
- Do not send user emails and IP addresses to an external LLM provider as a background process — that is PII egress nobody consented to, for negligible benefit.
- Add alert deduplication/cooldown (the design re-alerts every 5 minutes on a persisting condition).
- Add Spring Boot Actuator + Micrometer now regardless — that is the industry-standard answer to "the backend monitors itself," and its absence is a bigger operational gap than any AI feature fills.
- If LLM summarization of alert windows is still wanted afterward as a portfolio piece, bolt it onto the rule-based alerts as an optional "explain" action — triggered by a human, not a scheduler.

---

## 4. Roadmap Review

### Build first
1. **Hardening phase (the roadmap's "Part 2" — expanded).** Config externalization, secret-fallback removal, pagination, CORS, login rate limiting, student-delete FK semantics, `DataIntegrityViolationException` handler, ObjectMapper injection, delete `extractRole()`/`getRawToken()`, shorter token TTL, Actuator, Spring Boot upgrade, Testcontainers. None of the sprints is safe to build on the current base.
2. **Sprint 1 backend** (with §3 corrections) — highest value-to-risk ratio.
3. **Real frontend** (Vite/TS/TanStack), integrated against the live API, CRUD + Sprint 1 panels. Frontend-backend integration will surface contract issues; do it before more backend AI surface accumulates.
4. **Bulk upload with preview** (Sprint 2's deterministic half).

### Delay
- **Sprint 2's NL agent** until: per-tool authorization proven, server-side confirmation protocol designed, request-scope-in-tool-threads resolved. It is the most dangerous feature and the least necessary.
- **Any Sentinel LLM work** until rule-based alerting and Actuator are live and demonstrably insufficient (they won't be).
- Compound index / analytics-specific migrations until Sprint 1's actual query shapes exist.

### Remove entirely
- **The current `frontend/` implementation** as a production path (archive it; salvage design tokens).
- **Sentinel's scheduled-LLM-analysis loop** in its proposed form (replaced per §3).
- **The Caffeine cache layer** from Sprint 1 (DB cache table only).
- **`@EnableMethodSecurity`**, `extractRole()`, `getRawToken()`, STAFF comment remnants, empty pom stubs.
- Endorse the roadmap's own "What NOT to Build" list unchanged — every item on it is correctly rejected.

---

## 5. Technical Debt Report

### Critical (fix before any feature work)
| # | Item | Where |
|---|------|-------|
| C1 | JWT secret falls back to committed dev secret in docker profile → forgeable admin tokens | `application-docker.properties` |
| C2 | Prod config hardcodes `localhost:5432/student_dev`, `postgres/postgres` | `application-prod.properties` |
| C3 | No pagination on any list endpoint; unbounded JOIN FETCH + deeply nested response DTOs | all controllers/repos |
| C4 | Student delete with results → unhandled FK violation → 500 | `StudentService.deleteStudent`, V1 schema, `GlobalExceptionHandler` |
| C5 | No rate limiting/lockout on login (credential stuffing + BCrypt DoS) | `AuthController` |

### High
| # | Item |
|---|------|
| H1 | Spring Boot 3.2.0 past OSS EOL — no security patches; blocks Spring AI adoption |
| H2 | 24h tokens, no refresh/revocation; stale permissions honored until expiry |
| H3 | Flyway migrations never executed in tests; H2≠PostgreSQL dialect gap (Testcontainers) |
| H4 | No CORS config — blocks all frontend work |
| H5 | Authorization absent below controller layer — direct blocker for Sprint 2/3 designs |
| H6 | `@RequestScope` context unusable from `@Scheduled`/`@Async` threads — blocker for Sprint 3 |
| H7 | No Actuator/health/metrics; app container has no healthcheck |

### Medium
| # | Item |
|---|------|
| M1 | `getRawToken()` public (token-in-logs risk) |
| M2 | Inline `new ObjectMapper()` in 401/403 handlers |
| M3 | Audit logging console-only; no durable audit trail |
| M4 | Permission matrix undertested (slice tests stub `hasPermission(any())→true`; only 4 integration tests) |
| M5 | `LocalDateTime` timestamps (timezone drift); move to `Instant` |
| M6 | README/docs stale and contradictory; deleted-file references |
| M7 | Seeder: ~12k single-row transactions on startup; only checks `studentRepository.count()` |

### Low
| # | Item |
|---|------|
| L1 | `extractRole()` deprecated, zero callers |
| L2 | `@EnableMethodSecurity` dead config |
| L3 | Empty pom metadata stubs |
| L4 | STAFF references in V2 comments |
| L5 | Duplicate `jwt.expiration-ms` injection in `AuthController` |
| L6 | `spring-boot-devtools` in pom (excluded from repackage by default, but verify) |

---

## 6. Revised Implementation Plan

**Phase 0 — Stabilize (1 week).** All Critical items + H1–H4, H7. Concretely: env-var-only prod/docker config with no secret defaults (fail fast on missing `JWT_SECRET`); `Pageable` on all list endpoints with a `PagedResponse<T>` wrapper; resolve student-delete semantics (recommend: 409 with count of dependent results) + `DataIntegrityViolationException` handler; bucket4j rate limit on login; token TTL → 2h; CORS bean; Spring Boot → current GA line + springdoc bump; Actuator with health/info/metrics; Testcontainers profile replacing H2 for repository/integration tests (Flyway on); delete L1–L4 cruft; truthful README rewrite. Exit gate: CI green on Testcontainers, all secrets external, `GET /api/results?page=0&size=20` paginated.

**Phase 1 — Academic Intelligence (2 weeks).** V5 migration (`ai_analysis_cache` only — defer event tables); `ResultAnalyticsRepository` with projections; deterministic stats computed in SQL/Java; `AcademicIntelligenceService` with LLM call *outside* transactions, timeout + single retry + daily token budget; DB cache keyed by SHA-256 (no Caffeine); structured-output records as specced; at-risk list as a plain SQL query; ChatClient mocked in all tests. Exit gate: analysis endpoint p95 < LLM latency + 200ms overhead; zero LLM calls in test suite; cache-hit path returns < 50ms.

**Phase 2 — Frontend, real (2–3 weeks, parallelizable with late Phase 1).** Vite + TS + TanStack Query + Zustand; OpenAPI-generated types; auth flow against real `/api/auth/login`; Students/Exams/Results CRUD with server pagination; Sprint 1 AI panels; design tokens carried over from the demo; the demo's motion layer dropped, `prefers-reduced-motion` respected for what remains. Exit gate: every screen backed by live API; no mock data in the bundle.

**Phase 3 — Bulk upload, deterministic (1 week).** POI/OpenCSV parser; strict file validation (size, type, row cap); preview→confirm as a server-side two-phase operation (proposal persisted with ID, separate confirm endpoint); per-row results; durable audit records for every write. No LLM. Exit gate: 30-row upload with 2 bad rows yields 28 creates + 2 precise row-level errors, and nothing writes without a confirm call.

**Phase 4 — Operational visibility (1 week).** `api_event_log` + async writer on a bounded executor + retention job; rule-based threshold alerts persisted to `sentinel_alerts` with dedup/cooldown; alerts API + frontend feed; Micrometer counters for auth failures/biz-rule violations/latency. No LLM, no PII egress.

**Phase 5 — Optional AI extensions (only if Phases 0–4 are solid).** (a) LLM column-mapping for messy upload headers — isolated call, no tools, schema-constrained output. (b) Human-triggered "explain this alert window" summarization. (c) NL admin agent — only after per-tool permission checks and server-held conversation state exist; treat as research, not roadmap.

**Cut from the previous plan:** scheduled Sentinel LLM loop, Caffeine layer, NL-agent-before-bulk-upload ordering, and the assumption that the existing `frontend/` is the integration starting point.

---

*Review conducted against branch `custom-authz-phase2` @ `982bbae`, 2026-06-10.*
