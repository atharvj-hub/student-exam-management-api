-- ═══════════════════════════════════════════════════════════════════════════
-- V1__initial_schema.sql — Flyway Migration
-- ═══════════════════════════════════════════════════════════════════════════
--
-- WHY FLYWAY AND NOT ddl-auto=create?
--
-- Hibernate's ddl-auto:
--   create     → DROPS and recreates ALL tables on every startup. DATA LOSS.
--   update     → Tries to ALTER tables to match entities. Unreliable in production.
--                Doesn't handle column renames, only additions.
--   validate   → Just checks schema matches entities. Throws if mismatched. SAFE.
--   none       → Does nothing. You manage schema manually. PRODUCTION STANDARD.
--
-- Flyway approach (production standard):
--   You write versioned SQL files: V1__, V2__, V3__
--   Flyway runs them IN ORDER, exactly ONCE, and tracks what's been run
--   in a "flyway_schema_history" table.
--   App startup: Flyway checks which migrations haven't run → runs them.
--   Already-run migrations: NEVER touched again.
--
-- Benefits:
--   1. Schema changes are tracked in version control (like code)
--   2. Every environment (dev, staging, prod) goes through exact same migrations
--   3. Rollbacks are explicit (V2__rollback_something.sql)
--   4. Team members always have same schema after `git pull + mvn spring-boot:run`
--
-- File naming: V{version}__{description}.sql
--   V1__ = version 1
--   initial_schema = description (two underscores before description)

-- ── students ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS students (
    id           BIGSERIAL     PRIMARY KEY,
    name         VARCHAR(100)  NOT NULL,
    email        VARCHAR(150)  NOT NULL UNIQUE,
    roll_number  VARCHAR(20)   NOT NULL UNIQUE,
    created_at   TIMESTAMP     DEFAULT NOW(),
    updated_at   TIMESTAMP     DEFAULT NOW()
);

-- ── subjects ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS subjects (
    id            BIGSERIAL    PRIMARY KEY,
    subject_name  VARCHAR(100) NOT NULL,
    subject_code  VARCHAR(20)  NOT NULL UNIQUE,
    total_marks   INTEGER      NOT NULL CHECK (total_marks >= 1),
    created_at    TIMESTAMP    DEFAULT NOW()
);

-- ── exams ─────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS exams (
    id          BIGSERIAL    PRIMARY KEY,
    exam_name   VARCHAR(150) NOT NULL,
    subject_id  BIGINT       NOT NULL REFERENCES subjects(id),
    exam_date   DATE         NOT NULL,
    created_at  TIMESTAMP    DEFAULT NOW()
);

-- ── results ───────────────────────────────────────────────────────────────────
-- CHECK constraint on grade enforces valid values at DB level (defense in depth)
-- Even if application sends wrong value somehow, DB rejects it.
CREATE TABLE IF NOT EXISTS results (
    id          BIGSERIAL       PRIMARY KEY,
    student_id  BIGINT          NOT NULL REFERENCES students(id),
    exam_id     BIGINT          NOT NULL REFERENCES exams(id),
    marks       DECIMAL(8,2)    NOT NULL CHECK (marks >= 0),
    percentage  DECIMAL(5,2)    NOT NULL,
    grade       VARCHAR(10)     NOT NULL CHECK (grade IN ('A_PLUS','A','B','C','FAIL')),
    status      VARCHAR(10)     NOT NULL CHECK (status IN ('PASS','FAIL')),
    created_at  TIMESTAMP       DEFAULT NOW(),
    updated_at  TIMESTAMP       DEFAULT NOW(),

    -- Composite unique constraint: one result per student per exam
    CONSTRAINT uk_student_exam_result UNIQUE (student_id, exam_id)
);

-- ── Indexes for frequently-queried columns ────────────────────────────────────
-- Without indexes: "SELECT * FROM results WHERE student_id = 5" does a full table scan.
-- With index: O(log n) lookup.
CREATE INDEX idx_results_student_id ON results(student_id);
CREATE INDEX idx_results_exam_id    ON results(exam_id);
CREATE INDEX idx_exams_subject_id   ON exams(subject_id);
