\# Sprint 1 Architecture — Student Performance Analytics

\*\*Student Exam Management API · Design Document · No code, contract-level only\*\*



\---



\## 0. Scope and Design Posture



Two capabilities, deliberately separated:



| Capability | Engine | Availability |

|---|---|---|

| \*\*Performance Summary\*\* — deterministic statistics (averages, trends, cohort deltas) | SQL aggregation + pure Java math | Always works, even with the LLM provider down |

| \*\*Performance Insights\*\* — narrative interpretation (patterns, interventions, risk flag) | Spring AI → LLM, structured output | Degradable; backed by DB cache |



The split is the load-bearing decision of this design. The LLM is treated as an \*\*untrusted, unreliable, expensive enrichment layer\*\* on top of facts the system computes itself. If the provider is down, slow, or hallucinating, the summary endpoint is unaffected and the insights endpoint degrades explicitly. Numbers are never sourced from the LLM — the LLM interprets numbers the backend already computed.



\*\*Pre-conditions inherited from the audit (assumed done before this sprint):\*\* prod config env-vars (C2), seeded credentials removed (C1), CORS locked (C4), Boot upgraded off 3.2.0 (H1 — required for current Spring AI), Testcontainers in place (H3). The LLM-outside-transaction rule (H7) is baked into §5 below.



Out of scope by constraint: RAG, vector stores, agents, multi-turn conversation, exam-quality analysis (defer to a later sprint — one analysis type proves the whole pipeline).



\---



\## 1. Endpoint Contract



All endpoints live under the existing security model: JWT + `@RequirePermission`. Both use `Permission.AI\_INSIGHTS\_VIEW` (currently ADMIN-only), which sidesteps the open student-ownership question entirely — no student-facing analytics this sprint.



\### 1.1 `GET /api/analytics/students/{studentId}/summary`

Deterministic statistics. No LLM involvement.



| Aspect | Contract |

|---|---|

| Permission | `AI\_INSIGHTS\_VIEW` |

| Path param | `studentId` — Long |

| Query params | none |

| 200 | `StudentPerformanceSummaryResponse` (§3.1) |

| 404 | student does not exist |

| 400 | student exists but has zero results (`BusinessRuleException`, existing envelope) |



\### 1.2 `GET /api/analytics/students/{studentId}/insights`

LLM-backed narrative analysis.



| Aspect | Contract |

|---|---|

| Permission | `AI\_INSIGHTS\_VIEW` |

| Path param | `studentId` — Long |

| Query params | `forceRefresh` — boolean, default `false`. Bypasses cache read (never cache write). |

| 200 | `StudentInsightsResponse` (§3.2) — `fromCache` flag distinguishes cached vs fresh |

| 404 / 400 | same semantics as summary |

| 502 | LLM returned unusable output after one retry (`AI\_ANALYSIS\_FAILED`) |

| 503 | LLM provider unreachable/timed out and no cache entry exists (`AI\_PROVIDER\_UNAVAILABLE`, includes `Retry-After`) |



GET is correct despite the side effect (cache write): the operation is semantically a read, idempotent per `dataHash`, and safe to retry. `forceRefresh` exists because admins will ask "but I just corrected his marks" — note that a marks correction changes the data hash, which \*already\* invalidates the cache naturally; `forceRefresh` is for re-rolling an unsatisfying narrative on identical data.



Rate limiting: the existing bucket4j infrastructure gets a second, stricter bucket for `/api/analytics/\*\*` insights calls (e.g. 10/min per user) — this endpoint converts requests into money.



\---



\## 2. Request DTO



There is deliberately \*\*no request body\*\* — both endpoints are GETs parameterized by path + query. This kills an entire class of validation and prompt-injection surface: no client-supplied text ever reaches the prompt. The only client inputs are a `Long` (validated by existence check) and a `boolean`.



If a future sprint adds tunable analysis (date ranges, subject filters), they enter as validated query params with server-side allowlists — never free text.



\---



\## 3. Response DTOs



\### 3.1 `StudentPerformanceSummaryResponse`



```json

{

&#x20; "studentId": 42,

&#x20; "name": "…", "rollNumber": "…", "section": "A",

&#x20; "examsTaken": 18,

&#x20; "overallAverage": 67.45,

&#x20; "passRate": 88.89,

&#x20; "overallTrend": "DECLINING",

&#x20; "cohort": { "sectionAverage": 71.20, "deltaFromSection": -3.75 },

&#x20; "subjects": \[

&#x20;   {

&#x20;     "subjectCode": "MTH101", "subjectName": "Mathematics",

&#x20;     "examCount": 3, "average": 81.00, "min": 72.00, "max": 91.50,

&#x20;     "trend": "IMPROVING", "deltaFromSubjectCohortAvg": 6.20

&#x20;   }

&#x20; ],

&#x20; "generatedAt": "2026-06-10T09:30:00Z"

}

```



\- All numeric fields are `BigDecimal` semantics, 2-decimal `HALF\_UP` — same convention as `ResultService`.

\- `trend` enum: `IMPROVING | DECLINING | STABLE | VOLATILE` — computed deterministically (§5.2), never by the LLM.

\- Timestamps are `Instant` (UTC) — new DTOs adopt the correct type even though legacy DTOs use `LocalDateTime`.



\### 3.2 `StudentInsightsResponse`



```json

{

&#x20; "studentId": 42,

&#x20; "analysisType": "STUDENT\_PERFORMANCE",

&#x20; "fromCache": true,

&#x20; "modelUsed": "…",

&#x20; "analyzedAt": "2026-06-10T08:01:11Z",

&#x20; "insight": { /\* exactly the structured-output schema of §7 \*/ }

}

```



The envelope (provenance: cache status, model, timestamp) is system-owned metadata; `insight` is the validated LLM payload. They are never mixed — a consumer can always tell which fields a machine computed and which a model wrote.



\---



\## 4. Repository Requirements



\### 4.1 `ResultAnalyticsRepository` (new, read-only)

Extends Spring Data's `Repository` (not `JpaRepository`) so no save/delete methods exist on an analytics path. \*\*All aggregation happens in SQL via interface projections — full `Result` entities are never loaded for statistics.\*\*



Required queries (JPQL/native, all projection-returning):



1\. \*\*Student trajectory\*\* — per-result rows for one student: percentage, grade, status, exam name, exam date, subject code/name, ordered by exam date ascending. Feeds trend math and the prompt table. Bounded: latest \*\*50\*\* results (prompt size control; more than 50 adds tokens, not insight).

2\. \*\*Per-subject aggregates for one student\*\* — subject, count, avg, min, max grouped by subject.

3\. \*\*Section cohort average\*\* — avg percentage for the student's section, and per-subject section averages (for the delta fields).



\### 4.2 `AiAnalysisCacheRepository` (new)

Standard CRUD over the cache table with: lookup by `(analysisType, entityId, dataHash)` where `expiresAt > now`, and bulk delete of expired rows.



\### 4.3 V5 Flyway migration



```sql

ai\_analysis\_cache (

&#x20; id, analysis\_type VARCHAR(50), entity\_id BIGINT,

&#x20; data\_hash VARCHAR(64), result\_json TEXT,

&#x20; model\_used VARCHAR(50), tokens\_in INT, tokens\_out INT,

&#x20; created\_at TIMESTAMP, expires\_at TIMESTAMP,

&#x20; UNIQUE (analysis\_type, entity\_id, data\_hash)

)

\-- index on (analysis\_type, entity\_id), index on (expires\_at)

```



Two notes against the roadmap draft: (a) the proposed `idx\_results\_student\_exam` compound index is \*\*redundant\*\* — `UNIQUE(student\_id, exam\_id)` on `results` already materializes as an index in PostgreSQL; do not add it. (b) Token columns are split in/out because that's how every provider bills — one merged column makes cost attribution guesswork later.



This migration is the first to land under the Testcontainers regime (H3) — it gets executed against real PostgreSQL in CI from day one, which matters because the analytics SQL (`AVG`/`STDDEV` over `DECIMAL`) is exactly where H2 and PostgreSQL semantics diverge.



\---



\## 5. Service Architecture



Four components, one rule: \*\*no transaction is ever open while the LLM is being called.\*\*



```

AnalyticsController

&#x20;  ├── StudentAnalyticsService      (deterministic; @Transactional(readOnly))

&#x20;  └── StudentInsightService        (orchestrator; NO class-level @Transactional)

&#x20;         ├── StudentAnalyticsService     (reuses the same projections/stats)

&#x20;         ├── InsightPromptBuilder        (pure function: stats → prompt string)

&#x20;         ├── AnalysisCacheService        (short transactions: get / put / sweep)

&#x20;         └── LlmClient (thin wrapper over Spring AI ChatClient)

```



\### 5.1 `StudentInsightService` orchestration sequence

1\. \*\*\[tx 1 — short]\*\* Validate student exists; load trajectory + aggregates via projections; compute `dataHash` = SHA-256 over the canonical serialization of the projection rows (sorted, fixed format — hash stability is a tested invariant).

2\. \*\*\[tx 2 — short]\*\* Cache lookup `(STUDENT\_PERFORMANCE, studentId, dataHash)` unless `forceRefresh`. Hit → return with `fromCache=true`. 

3\. \*\*\[no tx]\*\* Build prompt from the already-loaded data; call LLM with timeout (§6). This is the 2–30 s window — with the Hikari pool of 10, holding a connection here is the difference between "AI is slow" and "the whole API is down" (audit H7).

4\. Parse + validate structured output (§7). Malformed → one retry with the format reminder appended → still malformed → `AiAnalysisException`.

5\. \*\*\[tx 3 — short]\*\* Upsert cache row (24 h TTL); opportunistically delete expired rows for this `(type, entityId)` in the same transaction — cache hygiene without a scheduler, which also avoids waking the `@RequestScope`/`@Scheduled` incompatibility this sprint.



\### 5.2 Deterministic trend algorithm (lives in `StudentAnalyticsService`, pure method)

Least-squares slope over chronologically ordered percentages: slope > +1.5 per exam → `IMPROVING`; < −1.5 → `DECLINING`; otherwise `STABLE` — unless stddev of residuals exceeds a threshold (e.g. 12) → `VOLATILE`. Thresholds are named constants beside the existing grade thresholds. Fewer than 3 results → `STABLE` (insufficient signal), and the insights prompt is told so. These are pure `BigDecimal → enum` functions in the exact mold of `calculateGrade()` — the project's most-tested pattern.



\---



\## 6. Spring AI Integration Architecture



\- \*\*One bean:\*\* a `ChatClient` built from auto-configuration; model name, API key, temperature, and max output tokens come from properties (`spring.ai.\*`), never constants. Key is `${AI\_API\_KEY}` env-injected — same fail-fast discipline as `JWT\_SECRET`.

\- \*\*Settings:\*\* temperature ≤ 0.2 (interpretation task, not creative writing); max output tokens capped (\~1,200 — fits the §7 schema with headroom); \*\*call timeout 30 s\*\* configured on the underlying HTTP client. No streaming (structured output is parsed whole). No retries at the HTTP layer beyond the single malformed-output retry — retry storms against a degraded provider are how outages metastasize.

\- \*\*Structured output:\*\* `BeanOutputConverter` over the §7 record graph; its generated format instructions are appended to the prompt. The converter's parse is followed by \*\*application-level validation\*\* (enum membership, list length caps, string length caps). LLM output crosses a trust boundary identical to user input — it is parsed, validated, and rejected, never trusted.

\- \*\*Prompt construction:\*\* system instruction (role, "observations must be traceable to specific data rows", "state uncertainty in the confidence field") + plain-text aligned table of the trajectory rows + the deterministic per-subject aggregates \*\*including the computed trend enums\*\*. The model interprets trends; it does not compute them. No user-supplied text enters the prompt (§2), and student email/PII beyond name-roll-section is excluded.

\- \*\*Observability hooks (minimal):\*\* log one structured line per call — studentId, cache hit/miss, latency ms, tokens in/out, outcome — and persist token counts in the cache row. This is the dataset that answers "what does this feature cost?" in week two.

\- \*\*Test seam:\*\* all LLM access goes through the one thin `LlmClient` wrapper so tests mock a single interface rather than Spring AI internals (§10).



\---



\## 7. Structured Output Schema



The contract the LLM must satisfy — also the shape of `insight` in §3.2:



```json

{

&#x20; "overallAssessment": "string, 2–3 sentences, max 600 chars",

&#x20; "performanceProfile": "TOP\_PERFORMER | SOLID | INCONSISTENT | STRUGGLING | AT\_RISK",

&#x20; "patterns": \[

&#x20;   {

&#x20;     "type": "DECLINING | IMPROVING | STABLE | VOLATILE",

&#x20;     "scope": "CROSS\_SUBJECT | SUBJECT\_SPECIFIC",

&#x20;     "description": "string, max 300 chars",

&#x20;     "subjectsAffected": \["subjectCode — must be from the input data"]

&#x20;   }

&#x20; ],

&#x20; "subjectInsights": \[

&#x20;   {

&#x20;     "subjectCode": "must match an input subject",

&#x20;     "relativeToCohort": "ABOVE | AT | BELOW",

&#x20;     "observation": "string, max 300 chars"

&#x20;   }

&#x20; ],

&#x20; "interventions": \["string — max 3 items, each specific and actionable"],

&#x20; "atRisk": "boolean",

&#x20; "confidence": "HIGH | MEDIUM | LOW"

}

```



\*\*Post-parse validation rules (server-enforced, not prompt-hoped):\*\* every enum field must match its set; `patterns` ≤ 4; `interventions` ≤ 3; every `subjectCode` must exist in the input projection set (rejects hallucinated subjects); `confidence` must be `LOW` if `examsTaken < 3`. Any violation counts as malformed output → retry-once-then-502 path. Validation failures are logged with the raw output for prompt-tuning forensics.



\---



\## 8. Error Handling Strategy



Extends the existing `GlobalExceptionHandler` envelope (`ApiErrorResponse`) — no new error format.



| Failure | Exception | HTTP | Notes |

|---|---|---|---|

| Student not found | existing `ResourceNotFoundException` | 404 | unchanged path |

| Zero results | existing `BusinessRuleException` | 400 | "Cannot analyze a student with no recorded results" |

| Provider timeout / connect failure / 5xx / 429 | new `AiProviderUnavailableException` | \*\*503\*\* + `Retry-After` | \*\*Stale-if-error:\*\* before throwing, check cache for \*any\* non-expired entry for this student (even a different `dataHash`) and serve it with `fromCache=true` — a slightly stale narrative beats an error page |

| Output unparseable/invalid after retry | new `AiAnalysisException` | \*\*502\*\* | message generic; raw output goes to logs, never to the client |

| Rate limit exceeded | existing rate-limit path | 429 | stricter bucket per §1.2 |



Two hard rules: provider error bodies are \*\*never\*\* propagated to clients (information leak + contract coupling), and the summary endpoint shares none of these failure modes — the UI can always render numbers and show "insights temporarily unavailable" beside them. Fail-degraded, not fail-closed.



The two new exceptions live beside the existing custom exceptions and get two new `@ExceptionHandler` methods — additive, no existing handler changes.



\---



\## 9. Caching Opportunities



\*\*One cache, in PostgreSQL\*\* (`ai\_analysis\_cache`) — per the standing architecture decision rejecting in-process Caffeine for this: the cache must survive restarts (entries cost real money to recreate), be inspectable with SQL, and stay correct if a second instance ever runs. At this traffic, a DB read is performance-irrelevant.



\- \*\*Key = `(analysisType, entityId, dataHash)`\*\* — content-addressed: a marks correction changes the hash and invalidates automatically; unchanged data hits the cache for the full TTL. No explicit invalidation hooks in `ResultService` needed — zero coupling from the CRUD path to the AI feature.

\- \*\*TTL 24 h\*\* via `expiresAt`; sweep is opportunistic on write (§5.1), no scheduler this sprint.

\- \*\*`forceRefresh` bypasses read, never write\*\* — a refreshed analysis replaces the cached one for everybody.

\- \*\*Deliberately not cached:\*\* the summary endpoint (SQL aggregates over ≤ thousands of rows are single-digit milliseconds — caching would add staleness for nothing) and negative results (404/400 are cheap).

\- Stale-if-error reuse of expired-hash entries per §8 is the cache's second job: availability buffer, not just cost control.



Cost envelope: \~2k input + \~1k output tokens per fresh analysis; a 100-student cohort fully re-analyzed daily stays in single-digit dollars/day worst case; the hash design means real spend tracks \*data change rate\*, not request rate.



\---



\## 10. Test Strategy



Follows the project's existing four-tier pyramid; \*\*no live LLM call in any test or CI run, ever.\*\*



\*\*Tier 1 — pure unit (no Spring):\*\*

\- Trend algorithm: improving/declining/stable/volatile fixtures, < 3 results rule, boundary slopes exactly at thresholds.

\- `dataHash` determinism: same rows in different retrieval order → same hash; one mark changed → different hash.

\- `InsightPromptBuilder`: prompt contains every subject, the computed trends, the format instructions; contains no email/PII; row cap at 50 enforced.

\- Schema validation rules of §7: each rejection rule has a fixture (hallucinated subjectCode, 5 interventions, bad enum, over-length strings).



\*\*Tier 2 — mocked orchestration (Mockito on `LlmClient`):\*\*

\- Cache miss → exactly one LLM call → cache row written.

\- Cache hit → zero LLM calls → `fromCache=true`.

\- `forceRefresh=true` with valid cache → LLM called, cache overwritten.

\- Malformed output → exactly one retry → second failure → `AiAnalysisException`.

\- Provider timeout with a stale cache entry → stale served; without → 503.

\- \*\*Transaction boundary guard:\*\* assert no active transaction during the mocked LLM call (e.g. via `TransactionSynchronizationManager` probe in the mock) — this pins the H7 rule as a regression test, not a convention.



\*\*Tier 3 — repository slice (Testcontainers PostgreSQL, Flyway on):\*\*

\- Analytics projections return correct aggregates against seeded known data (hand-computed expected values).

\- V5 migration applies cleanly; cache unique constraint enforced; expired-row sweep deletes only expired.



\*\*Tier 4 — integration (`@SpringBootTest` + MockMvc, `LlmClient` mock-bean):\*\*

\- 401 without token; 403 for STUDENT-role token (verifies `AI\_INSIGHTS\_VIEW` gating through the real AOP aspect).

\- Full happy path: HTTP → orchestration → mocked LLM fixture → validated JSON response matching §3.2.

\- Error envelope shape for 502/503 paths matches `ApiErrorResponse`.



\*\*Coverage note:\*\* the new analytics packages must not be added to the JaCoCo exclusion list — the pure-function design exists precisely so the math and validation logic clear the 60/50 gates honestly. The ArchUnit guard from the audit (every `@\*Mapping` method carries `@RequirePermission`) must be in place before this sprint's controller lands — `AnalyticsController` is its first new customer.



\---



\### Build order within the sprint

V5 migration + cache repo → analytics repository + deterministic service + summary endpoint (shippable on its own) → prompt builder + schema validation → `LlmClient` + orchestration → insights endpoint → error handlers → integration tests. The summary endpoint going first means there is a demonstrable, LLM-independent deliverable by mid-sprint, and the riskiest integration (provider behavior) lands on top of an already-tested data layer.

