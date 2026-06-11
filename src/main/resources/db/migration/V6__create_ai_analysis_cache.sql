-- ═══════════════════════════════════════════════════════════════════════════
-- V6__create_ai_analysis_cache.sql
-- ═══════════════════════════════════════════════════════════════════════════
--
-- Content-addressed cache for AI-generated student insights.
--
-- KEY DESIGN DECISIONS
--
-- Cache key: (student_id, data_hash)
--   data_hash = SHA-256 of the prompt context string fed to the LLM.
--   The prompt context contains every piece of data the model sees: exam
--   trajectory, subject aggregates, computed trends, cohort averages.
--   No wall-clock timestamps appear in the context, so the hash is stable
--   for as long as the underlying result rows remain unchanged.
--
-- Invalidation: content-addressed, automatic.
--   When any exam result changes, the context string changes, producing a
--   new hash. The old row is never matched again — it becomes an unreachable
--   orphan. No explicit delete hooks or event listeners are required.
--
-- Payload storage: JSON TEXT.
--   The payload column stores a JSON-serialized StudentInsightPayload.
--   TEXT is used instead of JSONB because we never query into the payload
--   content — we only read the whole document by (student_id, data_hash).
--   TEXT avoids the overhead of JSONB parsing on write.
--
-- Concurrency safety:
--   The UNIQUE constraint on (student_id, data_hash) prevents duplicate
--   insertions from concurrent requests that both miss the cache. The
--   second writer receives a constraint violation which is caught and
--   silently ignored — the first writer's result is already stored.

CREATE TABLE IF NOT EXISTS ai_analysis_cache (
    id          BIGSERIAL       PRIMARY KEY,
    student_id  BIGINT          NOT NULL REFERENCES students(id),
    data_hash   VARCHAR(64)     NOT NULL,
    model_used  VARCHAR(100)    NOT NULL,
    payload     TEXT            NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_cache_student_hash UNIQUE (student_id, data_hash)
);

CREATE INDEX idx_cache_student_hash ON ai_analysis_cache(student_id, data_hash);
