-- ═══════════════════════════════════════════════════════════════════════════
-- R__seed_demo_data.sql — Repeatable demo seed (DEV / DOCKER ONLY)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- WIRING: this file lives in classpath:db/seed, which is added to
--   spring.flyway.locations ONLY in application-dev.properties and
--   application-docker.properties. Production (application-prod.properties)
--   and tests (Flyway disabled, H2) never load it.
--
-- WHY REPEATABLE (R__) NOT VERSIONED (V__):
--   Repeatable migrations run after all versioned ones and re-apply whenever
--   their checksum changes — they never collide with the V-series numbering
--   that prod also runs. Every statement below is idempotent (ON CONFLICT /
--   WHERE NOT EXISTS), so re-runs are safe and converge to the same state.
--
-- CONTENT: a believable cohort the analytics + AI endpoints can actually
--   analyze — 12 students across 2 sections, 6 subjects, 18 exams
--   (3 per subject), and a full result matrix (~216 rows) with deterministic
--   scores that encode per-student ability, per-subject difficulty, and
--   per-student exam trends (some improving, some declining) so the trend
--   detection and AI narrative have real signal to work with.

-- ── Subjects (6) ────────────────────────────────────────────────────────────
INSERT INTO subjects (subject_name, subject_code, total_marks) VALUES
    ('Mathematics',      'MATH', 100),
    ('Physics',          'PHYS', 100),
    ('Computer Science', 'CSC',  100),
    ('Literature',       'LIT',  100),
    ('Economics',        'ECON', 100),
    ('Biology',          'BIO',  100)
ON CONFLICT (subject_code) DO NOTHING;

-- ── Students (12, sections A/B) ─────────────────────────────────────────────
INSERT INTO students (name, email, roll_number, section) VALUES
    ('Aria Vance',      'aria.vance@school.edu',      'S001', 'A'),
    ('Noah Okafor',     'noah.okafor@school.edu',     'S002', 'B'),
    ('Maya Lindqvist',  'maya.lindqvist@school.edu',  'S003', 'A'),
    ('Leo Haddad',      'leo.haddad@school.edu',      'S004', 'B'),
    ('Iris Moreau',     'iris.moreau@school.edu',     'S005', 'A'),
    ('Kai Sato',        'kai.sato@school.edu',        'S006', 'B'),
    ('Sofia Romano',    'sofia.romano@school.edu',    'S007', 'A'),
    ('Theo Bauer',      'theo.bauer@school.edu',      'S008', 'B'),
    ('Lena Petrov',     'lena.petrov@school.edu',     'S009', 'A'),
    ('Omar Nair',       'omar.nair@school.edu',       'S010', 'B'),
    ('Nina Costa',      'nina.costa@school.edu',      'S011', 'A'),
    ('Eli Larsen',      'eli.larsen@school.edu',      'S012', 'B')
ON CONFLICT (email) DO NOTHING;

-- ── Exams (3 per subject = 18) ──────────────────────────────────────────────
-- Idempotent on (exam_name, subject_id); exam_name is subject-qualified so it
-- is unique per subject even though "Midterm I" repeats across subjects.
INSERT INTO exams (exam_name, subject_id, exam_date)
SELECT v.exam_name, s.id, v.exam_date
FROM (VALUES
    ('MATH', 'Mathematics Midterm I',       DATE '2026-02-12'),
    ('MATH', 'Mathematics Midterm II',      DATE '2026-04-09'),
    ('MATH', 'Mathematics Final',           DATE '2026-05-21'),
    ('PHYS', 'Physics Midterm I',           DATE '2026-02-13'),
    ('PHYS', 'Physics Midterm II',          DATE '2026-04-10'),
    ('PHYS', 'Physics Final',               DATE '2026-05-22'),
    ('CSC',  'Computer Science Midterm I',  DATE '2026-02-16'),
    ('CSC',  'Computer Science Midterm II', DATE '2026-04-13'),
    ('CSC',  'Computer Science Final',      DATE '2026-05-25'),
    ('LIT',  'Literature Midterm I',        DATE '2026-02-17'),
    ('LIT',  'Literature Midterm II',       DATE '2026-04-14'),
    ('LIT',  'Literature Final',            DATE '2026-05-26'),
    ('ECON', 'Economics Midterm I',         DATE '2026-02-18'),
    ('ECON', 'Economics Midterm II',        DATE '2026-04-15'),
    ('ECON', 'Economics Final',             DATE '2026-05-27'),
    ('BIO',  'Biology Midterm I',           DATE '2026-02-19'),
    ('BIO',  'Biology Midterm II',          DATE '2026-04-16'),
    ('BIO',  'Biology Final',               DATE '2026-05-28')
) AS v(subject_code, exam_name, exam_date)
JOIN subjects s ON s.subject_code = v.subject_code
WHERE NOT EXISTS (
    SELECT 1 FROM exams e WHERE e.exam_name = v.exam_name AND e.subject_id = s.id
);

-- ── Results (full matrix: each seeded student × each exam) ───────────────────
-- Deterministic score model (all integer arithmetic, percentage = marks since
-- total_marks = 100):
--   s = student ordinal (S001 -> 1 …)            ability  = ((s*37) % 45) - 18
--   j = subject index 0..5                        subj_mean per discipline
--   k = exam phase (Midterm I=0, II=1, Final=2)   trend = (((s*j+k)%5)-2)*k*2
--   noise = ((s*13 + j*7 + k*5) % 17) - 8
--   score = clamp( round(subj_mean + ability*0.7 + trend + noise), 8, 99 )
WITH base AS (
    SELECT
        st.id AS student_id,
        e.id  AS exam_id,
        CAST(substring(st.roll_number FROM 2) AS int) AS s,
        CASE sub.subject_code
            WHEN 'MATH' THEN 0 WHEN 'PHYS' THEN 1 WHEN 'CSC' THEN 2
            WHEN 'LIT'  THEN 3 WHEN 'ECON' THEN 4 ELSE 5 END AS j,
        CASE
            WHEN e.exam_name LIKE '%Final%'       THEN 2
            WHEN e.exam_name LIKE '%Midterm II%'  THEN 1
            ELSE 0 END AS k,
        CASE sub.subject_code
            WHEN 'MATH' THEN 73 WHEN 'PHYS' THEN 61 WHEN 'CSC' THEN 80
            WHEN 'LIT'  THEN 75 WHEN 'ECON' THEN 67 ELSE 58 END AS subj_mean
    FROM students st
    JOIN exams e   ON TRUE
    JOIN subjects sub ON sub.id = e.subject_id
    WHERE st.roll_number ~ '^S[0-9]+$'
),
scored AS (
    SELECT student_id, exam_id,
        GREATEST(8, LEAST(99, ROUND(
            subj_mean
            + (((s * 37) % 45) - 18) * 0.7
            + ((((s * j + k) % 5) - 2) * k * 2)
            + (((s * 13 + j * 7 + k * 5) % 17) - 8)
        )))::int AS score
    FROM base
)
INSERT INTO results (student_id, exam_id, marks, percentage, grade, status)
SELECT
    student_id, exam_id, score, score,
    CASE
        WHEN score >= 90 THEN 'A_PLUS'
        WHEN score >= 75 THEN 'A'
        WHEN score >= 60 THEN 'B'
        WHEN score >= 40 THEN 'C'
        ELSE 'FAIL' END,
    CASE WHEN score >= 40 THEN 'PASS' ELSE 'FAIL' END
FROM scored
ON CONFLICT (student_id, exam_id) DO NOTHING;
