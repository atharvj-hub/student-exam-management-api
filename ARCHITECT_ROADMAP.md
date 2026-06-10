# Principal Architect Analysis Report
## Student Exam Result Management System — AI Evolution Roadmap
### Prepared after full codebase analysis

---

## PART 1: CODEBASE ANALYSIS (HONEST)

### 1.1 What Is Actually Good

Before the criticism, acknowledge what is genuinely solid, because this is the foundation everything else builds on.

**Security layer** — the dual-layer defense (SecurityConfig URL rules + AOP `@RequirePermission`) is architecturally correct. The `@RequestScope` `JwtRequestContext` bean is the right pattern. The JWT permission array approach (baking permissions into the token, not just role) is production-grade thinking. Most tutorials don't get this far.

**Business logic isolation** — `ResultService.calculateGrade()`, `calculatePercentage()`, `calculateStatus()` are pure functions that take `BigDecimal` and return values. No Spring dependency, no DB access. This is exactly right. They are trivially testable. Do not let this pattern slip.

**BigDecimal migration** — `Result.java` uses `BigDecimal` with precision/scale annotations. `DECIMAL(8,2)` in the schema. `RoundingMode.HALF_UP` in division. This is the academically correct choice. Most systems get this wrong.

**JOIN FETCH discipline** — `ResultRepository` uses `JOIN FETCH` on all three navigation paths (student, exam, subject). The test in `ResultRepositoryTest` explicitly asserts `Hibernate.isInitialized()` on all three. That test will catch any future developer who naively replaces `findByIdWithDetails` with `findById`.

**DatabaseSeeder** — Gaussian distribution per performance profile with section shift is not something most tutorials write. This is the right way to generate synthetic data that actually tests edge cases.

**DTO separation** — `ResultCreateRequest` has `studentId` + `examId` + `marks`. `ResultUpdateRequest` has only `marks`. The comment in `ResultUpdateRequest` explains exactly why. This is correct and the comment makes it defensive.

**Test pyramid** — the project has all four tiers: pure unit (`ResultServiceTest`), mock-based (`ResultServiceMockitoTest`), slice (`@DataJpaTest`, `@WebMvcTest`), and full integration (`@SpringBootTest`). JaCoCo thresholds enforced in the build. This is production discipline.

---

### 1.2 Critical Issues — Fix Before Any Sprint Begins

These are not style preferences. These will cause real failures.

---

**ISSUE 1: No pagination anywhere.**

Every `findAll()` in the system returns the complete table. With the seeder creating 100 students × 18 exams each = up to 1,800 results. That's fine today. Add another cohort, another academic year — you're at 10,000 results. `findAllWithDetails()` does a `JOIN FETCH` across 4 tables with no `LIMIT`. That's a full table scan on every `GET /api/results`. This is a production outage waiting to happen.

Fix: Wrap all list endpoints with `Pageable`. `JpaRepository` supports it natively.

```java
// ResultRepository — add this variant
@Query("SELECT r FROM Result r JOIN FETCH r.student JOIN FETCH r.exam e JOIN FETCH e.subject")
Page<Result> findAllWithDetails(Pageable pageable);
```

Every `List<T>` controller response becomes `Page<T>` with metadata. Do this before Sprint 1.

---

**ISSUE 2: `application-prod.properties` hardcodes `localhost:5432`.**

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/student_dev
```

`student_dev` is the dev database name. `localhost` doesn't exist in any container or cloud environment. If this ever runs anywhere other than a laptop, it silently connects to nothing or the wrong database. The `jwt.secret` is also hardcoded — this is the only thing that's actually correct in the prod config (`${JWT_SECRET}`).

Fix: Everything in prod config must be `${ENV_VAR}`:
```properties
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
jwt.secret=${JWT_SECRET}
```

---

**ISSUE 3: `SecurityConfig` instantiates `ObjectMapper` inline.**

The `authenticationEntryPoint()` and `accessDeniedHandler()` lambdas both do `new ObjectMapper()`. Spring has a configured `ObjectMapper` bean — the one Jackson auto-configures with `JavaTimeModule`, `NON_NULL` inclusion, date format settings. The inline one has none of that. If someone adds `@JsonInclude` to `ApiErrorResponse`, the error handler ignores it.

Fix: inject the Spring-managed `ObjectMapper` bean via `SecurityConfig` constructor.

---

**ISSUE 4: No rate limiting on `/api/auth/login`.**

The login endpoint is public. There is nothing preventing automated credential stuffing — an attacker can attempt 100,000 logins per second. BCrypt slows each attempt to ~100ms server-side, but the thread pool gets exhausted. A basic bucket4j or Resilience4j rate limiter (5 attempts per IP per minute) fixes this in ~30 minutes.

---

**ISSUE 5: `JwtRequestContext.getRawToken()` is public.**

The raw JWT string is accessible to any `@Service` that injects `JwtRequestContext`. If any service method logs `context.getRawToken()` (debugging, accidental), the token appears in application logs. Tokens in logs are a security incident. Make it package-private or remove it entirely — the token should only be read once in `JwtRequestContextFilter`.

---

**ISSUE 6: `@CreationTimestamp` fields serialize without timezone.**

`LocalDateTime` in Java has no timezone. In a multi-timezone deployment (or when the JVM timezone differs from PostgreSQL timezone), `createdAt` values drift silently. The correct type is `Instant` (UTC-anchored) or `OffsetDateTime`. For a portfolio project this is low-priority, but flag it: your timestamps are only reliable if the JVM and PostgreSQL are in the same timezone.

---

**ISSUE 7: Seeder creates 12,600+ results but there's no index on `results.exam_id` considering compound lookups.**

The schema has `idx_results_student_id` and `idx_results_exam_id` separately. The query `findByStudentIdWithDetails` filters by `student_id` — good. But the upcoming analytics queries will filter by `(student_id, subject)` via a JOIN. Add a compound index in V5 migration:

```sql
CREATE INDEX idx_results_student_exam ON results(student_id, exam_id);
```

---

### 1.3 What to Remove

**`STAFF` references in comments.** V3 migration renamed STAFF → STUDENT. The V2 migration seed comment still says `Role: STAFF`. The `SECURITY_IMPLEMENTATION_PLAN.md` in the project root still references STAFF throughout. These confuse anyone reading the code. Clean it.

**`extractRole()` deprecated method in `JwtUtil`.** It's marked `@Deprecated(forRemoval = true)` since "Phase 1." Tests explicitly assert it returns null. It has no callers. Delete it now. The longer a deprecated method lives, the more likely someone uses it.

**The `@Profile("dev")` check in `DatabaseSeeder` is correct — keep it. But the seeder should check `subjectRepository.count()` in addition to `studentRepository.count()`** — if someone deletes students but not subjects, the seeder thinks the DB is populated and doesn't seed, leaving you with orphaned subjects.

**`NEXT_PHASE_IMPLEMENTATION_PLAN.md`** in the project — this document supersedes it. Remove it to avoid conflicting guidance.

---

### 1.4 What Needs Improvement Before AI Work

These are not breaking issues but they affect the quality of AI integration:

1. **Add `@JsonFormat` or use `Instant` on all timestamp fields** before AI responses include timestamps in their output.

2. **Add `@Schema` annotations to DTOs** — SpringDoc/Swagger already configured. Adding `@Schema(description = "...")` to the key fields (especially `marks`, `percentage`, `grade`) means the OpenAPI spec becomes the authoritative description for AI function-calling tools.

3. **`ResultService` should expose a dedicated analytics method** — currently all queries return full result objects. For AI analysis, you need aggregated statistics (mean, stddev, distribution by grade). Add `ResultAnalyticsRepository` with aggregate queries rather than loading full entities and computing in Java.

4. **Add `spring-boot-starter-cache` + Caffeine** — AI calls are expensive. Cache analysis results keyed by a hash of the input data. If the underlying results haven't changed, return the cached analysis.

---

## PART 2: PRE-SPRINT FIXES (ONE DAY)

Do these before writing a line of AI code. They unblock every sprint.

```
V5 Flyway migration:
  - Add compound index idx_results_student_exam
  - Add ai_analysis_cache table
  - Add api_event_log table (for Sentinel)
  - Add sentinel_alerts table

Dependency additions (pom.xml):
  - spring-ai-openai-spring-boot-starter (1.0.0)
  - spring-boot-starter-cache
  - com.github.ben-manes.caffeine:caffeine
  - bucket4j-core (rate limiting)
  - org.apache.poi:poi-ooxml (Excel parsing for Sprint 2)
  - opencsv (CSV parsing for Sprint 2)

Config fixes:
  - application-prod.properties → all ${ENV_VAR}
  - application-dev.properties → add spring.ai.openai.api-key=${OPENAI_API_KEY}
  - SecurityConfig → inject ObjectMapper bean

Pagination:
  - Add Pageable to ResultRepository, StudentRepository
  - Update service methods to return Page<T>
  - Update controllers to accept page/size query params
  - Add PagedResponse<T> wrapper DTO

Delete:
  - JwtUtil.extractRole() deprecated method
  - Clean STAFF comments from V2 migration
```

---

## PART 3: SPRINT 1 — ACADEMIC INTELLIGENCE SUITE

### Business Goal

Teachers with 100 students cannot manually cross-reference grade patterns across 6 subjects and 3 exam types per student. They know Atharv failed — they don't know why. The AI surfaces the structural reason and a specific intervention. This is not a chatbot — it is a reasoning layer on top of your existing domain data.

---

### 3.1 Database Changes — V5 Migration

```sql
-- ai_analysis_cache: avoid re-running expensive LLM calls on unchanged data
CREATE TABLE ai_analysis_cache (
    id             BIGSERIAL    PRIMARY KEY,
    analysis_type  VARCHAR(50)  NOT NULL,   -- 'STUDENT_TRAJECTORY' | 'EXAM_QUALITY'
    entity_id      BIGINT       NOT NULL,   -- student_id or exam_id
    data_hash      VARCHAR(64)  NOT NULL,   -- SHA-256 of input data snapshot
    result_json    TEXT         NOT NULL,   -- serialized analysis result
    model_used     VARCHAR(50)  NOT NULL,   -- 'gpt-4o' etc
    tokens_used    INTEGER,
    created_at     TIMESTAMP    DEFAULT NOW(),
    expires_at     TIMESTAMP    NOT NULL,   -- TTL: 24 hours
    UNIQUE(analysis_type, entity_id, data_hash)
);

CREATE INDEX idx_ai_cache_lookup ON ai_analysis_cache(analysis_type, entity_id);
CREATE INDEX idx_ai_cache_expiry ON ai_analysis_cache(expires_at);
```

---

### 3.2 New Repository — `ResultAnalyticsRepository`

Do not load full `Result` entities for statistics. Use projections.

```java
public interface ResultAnalyticsRepository extends Repository<Result, Long> {

    // Student trajectory: all results for one student, ordered chronologically
    @Query("""
        SELECT r.percentage, r.grade, r.status,
               e.examName, e.examDate,
               s.subjectName, s.subjectCode
        FROM Result r
        JOIN r.exam e
        JOIN e.subject s
        WHERE r.student.id = :studentId
        ORDER BY e.examDate ASC
        """)
    List<StudentResultProjection> findStudentTrajectory(@Param("studentId") Long studentId);

    // Exam quality: grade distribution for one exam
    @Query("""
        SELECT r.grade AS grade, COUNT(r) AS count, AVG(r.percentage) AS avgPct
        FROM Result r
        WHERE r.exam.id = :examId
        GROUP BY r.grade
        ORDER BY r.grade
        """)
    List<GradeDistributionProjection> findExamDistribution(@Param("examId") Long examId);

    // Cohort comparison: student's percentile per subject
    @Query("""
        SELECT AVG(r.percentage), MIN(r.percentage), MAX(r.percentage),
               STDDEV(CAST(r.percentage AS double))
        FROM Result r
        JOIN r.exam e JOIN e.subject s
        WHERE s.id = :subjectId
        """)
    SubjectStatsProjection findSubjectStats(@Param("subjectId") Long subjectId);
}
```

Projections (interfaces, not classes — Hibernate maps them directly):

```java
public interface StudentResultProjection {
    BigDecimal getPercentage();
    String getGrade();
    String getStatus();
    String getExamName();
    LocalDate getExamDate();
    String getSubjectName();
    String getSubjectCode();
}

public interface GradeDistributionProjection {
    String getGrade();
    Long getCount();
    BigDecimal getAvgPct();
}
```

---

### 3.3 New DTOs

**Request:**
```java
// No fields needed — student/exam ID comes from path variable
// But include an optional `forceRefresh` flag to bypass cache
public record AnalysisRequest(
    @JsonProperty("forceRefresh") boolean forceRefresh
) {
    public AnalysisRequest() { this(false); }
}
```

**Structured output (what the LLM must return — drives `BeanOutputConverter`):**

```java
// Sprint 1 structured outputs
public record StudentTrajectoryAnalysis(
    @JsonProperty("overallAssessment") String overallAssessment,        // 2-3 sentences
    @JsonProperty("performanceProfile") String performanceProfile,      // TOP_PERFORMER etc
    @JsonProperty("patterns") List<PerformancePattern> patterns,
    @JsonProperty("subjectInsights") List<SubjectInsight> subjectInsights,
    @JsonProperty("interventions") List<String> interventions,          // max 3, specific
    @JsonProperty("atRisk") boolean atRisk,
    @JsonProperty("confidence") String confidence                       // HIGH | MEDIUM | LOW
) {}

public record PerformancePattern(
    @JsonProperty("type") String type,           // DECLINING | STABLE | IMPROVING | VOLATILE
    @JsonProperty("scope") String scope,         // CROSS_SUBJECT | SUBJECT_SPECIFIC
    @JsonProperty("description") String description,
    @JsonProperty("subjectsAffected") List<String> subjectsAffected
) {}

public record SubjectInsight(
    @JsonProperty("subjectCode") String subjectCode,
    @JsonProperty("trend") String trend,
    @JsonProperty("relativeToMean") String relativeToMean,             // ABOVE | AT | BELOW
    @JsonProperty("observation") String observation
) {}

public record ExamQualityAnalysis(
    @JsonProperty("verdict") String verdict,          // WELL_CALIBRATED | TOO_EASY | TOO_HARD | BIASED
    @JsonProperty("summary") String summary,
    @JsonProperty("distributionShape") String distributionShape,  // NORMAL | LEFT_SKEWED | RIGHT_SKEWED | BIMODAL | FLAT
    @JsonProperty("passRate") BigDecimal passRate,
    @JsonProperty("insights") List<String> insights,
    @JsonProperty("recommendations") List<String> recommendations,
    @JsonProperty("teachingGapDetected") boolean teachingGapDetected,
    @JsonProperty("confidence") String confidence
) {}
```

**Response wrapper:**
```java
public record AcademicAnalysisResponse<T>(
    Long entityId,
    String analysisType,
    T analysis,
    boolean fromCache,
    String modelUsed,
    Instant analyzedAt
) {}
```

---

### 3.4 Service Layer

```java
@Service
@Slf4j
public class AcademicIntelligenceService {

    private final ResultAnalyticsRepository analyticsRepository;
    private final StudentRepository studentRepository;
    private final ExamRepository examRepository;
    private final AiAnalysisCacheService cacheService;
    private final ChatClient chatClient;

    // Spring AI structured output converter
    private final BeanOutputConverter<StudentTrajectoryAnalysis> trajectoryConverter =
        new BeanOutputConverter<>(StudentTrajectoryAnalysis.class);
    private final BeanOutputConverter<ExamQualityAnalysis> examQualityConverter =
        new BeanOutputConverter<>(ExamQualityAnalysis.class);

    @Transactional(readOnly = true)
    public AcademicAnalysisResponse<StudentTrajectoryAnalysis> analyzeStudent(
            Long studentId, boolean forceRefresh) {

        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));

        List<StudentResultProjection> results = analyticsRepository.findStudentTrajectory(studentId);
        if (results.isEmpty()) {
            throw new BusinessRuleException("Cannot analyze student with no recorded results");
        }

        // Compute hash of current result data — cache key
        String dataHash = computeHash(results);

        if (!forceRefresh) {
            Optional<StudentTrajectoryAnalysis> cached =
                cacheService.getCached("STUDENT_TRAJECTORY", studentId, dataHash, StudentTrajectoryAnalysis.class);
            if (cached.isPresent()) {
                return new AcademicAnalysisResponse<>(studentId, "STUDENT_TRAJECTORY",
                    cached.get(), true, "cached", Instant.now());
            }
        }

        String prompt = buildStudentTrajectoryPrompt(student, results);
        StudentTrajectoryAnalysis analysis = callLlm(prompt, trajectoryConverter);

        cacheService.store("STUDENT_TRAJECTORY", studentId, dataHash, analysis);
        log.info("[AI] Student trajectory analysis completed for studentId={}", studentId);

        return new AcademicAnalysisResponse<>(studentId, "STUDENT_TRAJECTORY",
            analysis, false, "gpt-4o", Instant.now());
    }

    @Transactional(readOnly = true)
    public AcademicAnalysisResponse<ExamQualityAnalysis> analyzeExam(
            Long examId, boolean forceRefresh) {
        // Same pattern: load projections → hash → cache check → LLM → cache store
    }

    private <T> T callLlm(String prompt, BeanOutputConverter<T> converter) {
        String response = chatClient.prompt()
            .user(u -> u.text(prompt + "\n\n" + converter.getFormat()))
            .call()
            .content();
        return converter.convert(response);
    }
}
```

---

### 3.5 Prompt Engineering Strategy

**Student Trajectory Prompt:**

```java
private String buildStudentTrajectoryPrompt(Student student, List<StudentResultProjection> results) {
    StringBuilder sb = new StringBuilder();
    sb.append("""
        You are an academic analyst for a university exam management system.
        Analyze the following student's complete examination history and identify
        performance patterns, learning gaps, and intervention opportunities.
        
        Be specific. Do not generalize. Your observations must be directly
        traceable to specific data points in the results below.
        If the data is insufficient to support a claim, say so in the confidence field.
        """);

    sb.append("\n\nSTUDENT: ").append(student.getName())
      .append(" | Section: ").append(student.getSection())
      .append(" | Roll: ").append(student.getRollNumber());

    sb.append("\n\nEXAMINATION HISTORY (chronological):\n");
    results.forEach(r -> sb.append(String.format(
        "%-20s | %-15s | %6.2f%% | %-6s | %-4s | %s\n",
        r.getExamName(), r.getSubjectName(), r.getPercentage(),
        r.getGrade(), r.getStatus(), r.getExamDate()
    )));

    // Cross-subject summary
    Map<String, DoubleSummaryStatistics> subjectStats = results.stream()
        .collect(groupingBy(StudentResultProjection::getSubjectName,
            summarizingDouble(r -> r.getPercentage().doubleValue())));

    sb.append("\n\nSUBJECT AVERAGES:\n");
    subjectStats.forEach((subject, stats) ->
        sb.append(String.format("  %-20s avg=%.2f%% min=%.2f%% max=%.2f%%\n",
            subject, stats.getAverage(), stats.getMin(), stats.getMax())));

    return sb.toString();
}
```

**Key prompt engineering decisions:**
- Plain-text tabular data (not JSON input) — LLMs reason better over formatted text tables
- Explicit instruction: "traceable to specific data points" — prevents hallucinated observations
- Confidence field is mandatory — forces the model to express uncertainty
- `converter.getFormat()` appends the JSON schema from `BeanOutputConverter` — structured output

---

### 3.6 Controller

```java
@RestController
@RequestMapping("/api/intelligence")
@Slf4j
public class AcademicIntelligenceController {

    private final AcademicIntelligenceService intelligenceService;

    @GetMapping("/students/{studentId}/trajectory")
    @RequirePermission(Permission.AI_INSIGHTS_VIEW)
    public ResponseEntity<AcademicAnalysisResponse<StudentTrajectoryAnalysis>> analyzeStudent(
            @PathVariable Long studentId,
            @RequestParam(defaultValue = "false") boolean forceRefresh) {
        log.info("GET /api/intelligence/students/{}/trajectory", studentId);
        return ResponseEntity.ok(intelligenceService.analyzeStudent(studentId, forceRefresh));
    }

    @GetMapping("/exams/{examId}/quality")
    @RequirePermission(Permission.AI_INSIGHTS_VIEW)
    public ResponseEntity<AcademicAnalysisResponse<ExamQualityAnalysis>> analyzeExam(
            @PathVariable Long examId,
            @RequestParam(defaultValue = "false") boolean forceRefresh) {
        log.info("GET /api/intelligence/exams/{}/quality", examId);
        return ResponseEntity.ok(intelligenceService.analyzeExam(examId, forceRefresh));
    }
}
```

---

### 3.7 Cost Analysis

With the analysis cache active:

| Scenario | Tokens (est.) | Cost @ GPT-4o rates |
|---|---|---|
| Student trajectory (1 student, 15 results) | ~2,000 in + 800 out | ~$0.015 |
| Exam quality (1 exam, 50 results aggregated) | ~1,500 in + 600 out | ~$0.012 |
| Cache hit (same data, re-query) | 0 | $0 |
| 100-student cohort analysis (all new) | ~280,000 tokens | ~$1.50 |

24-hour TTL on cache means daily cost for one cohort of 100 students ≈ $1.50/day maximum. In practice much less since most students' data doesn't change daily.

**Cost control rule:** Only `ADMIN` and teachers with `AI_INSIGHTS_VIEW` can trigger analyses. Students cannot self-request analyses of other students.

---

### 3.8 Security Implications

- `AI_INSIGHTS_VIEW` permission already defined in `Permission` enum and assigned to ADMIN in `RolePermissions`. Only wire it up.
- Student analysis endpoint must verify the requesting user has access to that student's data. In the current flat model (no teacher-to-student assignment), any `AI_INSIGHTS_VIEW` holder can see any student — acceptable for this scope.
- AI output is never stored back to `results` table. It lives only in `ai_analysis_cache`. This keeps the domain model clean.
- LLM API key must never be in version control. Use `${OPENAI_API_KEY}` in properties.

---

### 3.9 Testing Strategy

```java
// Unit test: cache service logic
class AiAnalysisCacheServiceTest { ... }

// Unit test: prompt building (no LLM call)
class AcademicIntelligenceServiceTest {
    @Test void buildStudentTrajectoryPrompt_includesAllSubjects() { ... }
    @Test void analyzeStudent_throwsIfNoResults() { ... }
}

// Integration test: mock the ChatClient
@SpringBootTest @ActiveProfiles("test")
class AcademicIntelligenceIntegrationTest {
    @MockBean ChatClient chatClient; // mock Spring AI
    // Verify: cache miss → LLM call → cache store
    // Verify: cache hit → no LLM call
    // Verify: forceRefresh=true → LLM call even if cached
}
```

**Never call the real LLM in tests.** Mock `ChatClient` in all test profiles.

---

### 3.10 Complexity Estimate

- New files: ~12 (service, controller, 6 DTOs, cache service, 2 repositories, 1 migration)
- Dependencies: Spring AI starter + cache + Caffeine
- Configuration: `application-dev.properties` + `application-test.properties`
- Estimated time: 3–4 days solid work

---

## PART 4: SPRINT 2 — NATURAL LANGUAGE ADMIN ASSISTANT

### Business Goal

Admins stop using Swagger to create data. They either type what they need in plain English or upload a file. The AI maps their intent to the correct service call and executes it. The key constraint: **AI never bypasses the existing service layer**. All business rules still run. The AI is a translator, not a backdoor.

---

### 4.1 Database Changes

No new tables needed for Sprint 2. Operations go through existing service calls which write to existing tables. The only addition is an operation log for audit purposes:

```sql
-- Already planning this for Sprint 3 Sentinel. Pre-create it here.
ALTER TABLE audit_event_log ... -- see Sprint 3 schema
```

---

### 4.2 The `@Tool` Pattern — How Admin Assistant Works

Spring AI function calling: the LLM decides which tools to invoke based on the user's prompt. Tools are plain Java methods annotated with `@Tool`. The `ChatClient` registers them and handles the tool invocation loop.

```java
@Service
@Slf4j
public class AdminAssistantTools {

    private final StudentService studentService;
    private final SubjectService subjectService;
    private final ExamService examService;
    private final ResultService resultService;
    // Injected — these run ALL existing validations

    @Tool(description = """
        Create a single student record. Use when the user wants to register
        one new student. Validates email uniqueness, roll number uniqueness.
        Returns the created student or an error message.
        """)
    public ToolResult createStudent(
            @ToolParam(description = "Full name of the student") String name,
            @ToolParam(description = "Email address") String email,
            @ToolParam(description = "Unique roll number (e.g. MCA045-A)") String rollNumber,
            @ToolParam(description = "Section A or B, optional") String section) {
        try {
            StudentCreateRequest req = new StudentCreateRequest();
            req.setName(name); req.setEmail(email);
            req.setRollNumber(rollNumber); req.setSection(section);
            StudentResponse created = studentService.createStudent(req);
            return ToolResult.success("CREATE_STUDENT", created);
        } catch (DuplicateResourceException | BusinessRuleException e) {
            return ToolResult.failure("CREATE_STUDENT", e.getMessage());
        }
    }

    @Tool(description = """
        Create multiple students from a pre-parsed list. Use when the user has
        already had their file parsed and wants to bulk-insert.
        Returns per-row success/failure details.
        """)
    public BulkToolResult createStudents(
            @ToolParam(description = "List of student records") List<StudentCreateRequest> students) {
        // iterate, call studentService.createStudent() for each, collect results
        // DOES NOT abort on failure — partial success is correct behavior
    }

    @Tool(description = """
        Record an exam result for a student. Automatically calculates
        percentage, grade, and pass/fail status.
        """)
    public ToolResult recordResult(
            @ToolParam(description = "Student ID") Long studentId,
            @ToolParam(description = "Exam ID") Long examId,
            @ToolParam(description = "Marks obtained (decimal)") BigDecimal marks) {
        // ...
    }

    @Tool(description = """
        Look up a student by name or roll number.
        Returns student details including ID, for use in subsequent operations.
        """)
    public ToolResult findStudent(
            @ToolParam(description = "Student name (partial match ok) or roll number") String query) {
        // ...
    }

    @Tool(description = """
        List all exams for a subject, with their IDs and dates.
        Use when the user wants to record results and needs to find the exam ID.
        """)
    public ToolResult listExamsForSubject(
            @ToolParam(description = "Subject name or code") String subjectQuery) {
        // ...
    }
}
```

**`ToolResult`** — a consistent response wrapper:
```java
public record ToolResult(
    String operation,
    boolean success,
    Object data,
    String error
) {
    public static ToolResult success(String op, Object data) { ... }
    public static ToolResult failure(String op, String error) { ... }
}
```

---

### 4.3 Prompt Engineering for Admin Assistant

The system prompt constrains the LLM strictly:

```java
private static final String ADMIN_SYSTEM_PROMPT = """
    You are an administrative assistant for a university exam management system.
    
    Your job: translate the admin's natural language request into one or more
    tool calls that modify the database correctly.
    
    STRICT RULES:
    1. Never invent data. If a required field is missing, ask for it.
    2. Never assume IDs. If you need a student ID or exam ID, use the lookup tools first.
    3. Before executing writes, confirm with a brief summary of what you will do.
    4. If an operation fails (duplicate, validation error), report the reason exactly
       as returned by the system. Do not paraphrase errors.
    5. You operate on behalf of an authenticated ADMIN user.
       All operations are logged.
    
    CAPABILITIES:
    - Create individual students
    - Bulk-create students from a parsed list
    - Record exam results
    - Look up students and exams
    
    CANNOT DO:
    - Delete records
    - Modify existing results in a way that bypasses validation
    - Access data for purposes other than the admin's current task
    """;
```

The confirmation step before writes is important — it gives the admin a chance to catch LLM misinterpretation before data is written.

---

### 4.4 File Upload Flow

```java
@Service
public class BulkFileParsingService {

    public List<StudentCreateRequest> parseStudentFile(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null) throw new BusinessRuleException("File has no name");

        if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
            return parseExcel(file);
        } else if (filename.endsWith(".csv")) {
            return parseCsv(file);
        } else {
            throw new BusinessRuleException("Unsupported file type. Upload .xlsx or .csv");
        }
    }

    private List<StudentCreateRequest> parseExcel(MultipartFile file) throws IOException {
        // Apache POI: read headers from row 0, data from row 1+
        // AI-assisted column mapping: if headers are non-standard,
        // use ChatClient to map "Full Name" → name, "Student No" → rollNumber, etc.
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(0);

            // Extract header names
            List<String> headers = IntStream.range(0, headerRow.getLastCellNum())
                .mapToObj(i -> headerRow.getCell(i).getStringCellValue().toLowerCase().trim())
                .collect(toList());

            // Smart column mapping (handles non-standard headers via LLM if needed)
            Map<String, Integer> columnMap = resolveColumnMapping(headers);

            return StreamSupport.stream(sheet.spliterator(), false)
                .skip(1) // skip header
                .filter(row -> row.getRowNum() > 0)
                .map(row -> mapRowToStudentRequest(row, columnMap))
                .collect(toList());
        }
    }

    private Map<String, Integer> resolveColumnMapping(List<String> headers) {
        // First try deterministic mapping
        Map<String, String> aliases = Map.of(
            "name", "name", "full name", "name", "student name", "name",
            "email", "email", "email address", "email",
            "roll", "rollNumber", "roll number", "rollNumber", "roll no", "rollNumber",
            "section", "section"
        );
        // If any required field not found, call LLM with headers to resolve
        // This handles cases like "Sr. No", "Enrollment ID", etc.
    }
}
```

**File validation before parsing:**
```java
private void validateFile(MultipartFile file) {
    if (file.isEmpty()) throw new BusinessRuleException("Uploaded file is empty");
    if (file.getSize() > 5 * 1024 * 1024)  // 5MB limit
        throw new BusinessRuleException("File too large. Maximum 5MB");
    String contentType = file.getContentType();
    if (!ALLOWED_TYPES.contains(contentType))
        throw new BusinessRuleException("Invalid file type: " + contentType);
}
```

---

### 4.5 Controller

```java
@RestController
@RequestMapping("/api/admin/assistant")
@Slf4j
public class AdminAssistantController {

    @PostMapping("/prompt")
    @RequirePermission(Permission.USER_CREATE) // most permissive write
    public ResponseEntity<AdminOperationResult> handlePrompt(
            @Valid @RequestBody AdminPromptRequest request) {
        // Multi-turn: send message history + system prompt + tools to ChatClient
        // Return the final response after all tool calls complete
    }

    @PostMapping("/upload")
    @RequirePermission(Permission.USER_CREATE)
    public ResponseEntity<BulkUploadResult> handleFileUpload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String entityType, // "students" | "results"
            @RequestParam(defaultValue = "false") boolean preview) {
        // If preview=true: parse only, return what WOULD be created, no writes
        // If preview=false: parse + create + return per-row results
    }
}
```

**The `preview` flag is important.** Admins upload 30 students, preview shows what will be created (including detected validation issues), then they confirm with `preview=false`. This prevents accidental bulk writes.

---

### 4.6 Conversation Management

The admin assistant is multi-turn. The prompt endpoint receives a conversation history:

```java
public record AdminPromptRequest(
    List<ConversationMessage> history,   // previous messages in this session
    String message                        // new user message
) {}

public record ConversationMessage(
    String role,    // "user" | "assistant"
    String content
) {}
```

The `ChatClient` builds the full conversation:
```java
ChatClient.prompt()
    .system(ADMIN_SYSTEM_PROMPT)
    .messages(toSpringAiMessages(request.history()))
    .user(request.message())
    .tools(adminAssistantTools) // registered tool methods
    .call()
    .content();
```

Session state (conversation history) is managed client-side — the frontend sends the full history on each request. This keeps the backend stateless.

---

### 4.7 Security Implications

This endpoint is powerful. Extra safeguards:

1. **Operations per request limit:** max 50 individual operations in a single prompt. Prevents "create 10,000 students" prompt.
2. **Audit log every operation:** the `AuditLoggingAspect` already logs all controller calls. But for the assistant, also log every individual `ToolResult` with the requestor's email.
3. **Preview-first on bulk uploads:** never automate bulk writes without a confirmation step.
4. **The LLM cannot delete.** No delete tools are registered. Accidental "delete all students" is not possible through this interface.

---

### 4.8 Complexity Estimate

- New files: ~10 (controller, assistant service, tools class, bulk parser, 4 DTOs, 1 test class)
- Dependencies: Apache POI, OpenCSV (already planned)
- Estimated time: 4–5 days (the Excel column mapping and conversation management are the hard parts)

---

## PART 5: SPRINT 3 — OPERATIONAL SENTINEL

### Business Goal

The backend monitors itself. Every 5 minutes, an AI agent reads the recent event window (errors, auth events, slow queries, business rule violations) and decides if anything warrants attention. It speaks in operational English, not log syntax. A developer waking up sees three sentences: what happened, why it's unusual, what to check.

---

### 5.1 Database Changes — V5 Migration (cont.)

```sql
-- API event log: ring buffer of recent events for Sentinel analysis
CREATE TABLE api_event_log (
    id            BIGSERIAL     PRIMARY KEY,
    event_type    VARCHAR(30)   NOT NULL,   -- AUTH_FAILURE | BIZ_RULE | SLOW_QUERY | EXCEPTION | AUTH_SUCCESS
    endpoint      VARCHAR(200),
    user_email    VARCHAR(150),
    ip_address    VARCHAR(45),
    http_status   INTEGER,
    duration_ms   INTEGER,
    error_class   VARCHAR(200),
    detail        TEXT,
    occurred_at   TIMESTAMP     DEFAULT NOW()
);

CREATE INDEX idx_event_log_time  ON api_event_log(occurred_at DESC);
CREATE INDEX idx_event_log_type  ON api_event_log(event_type, occurred_at DESC);

-- Sentinel alerts: persisted analysis outputs
CREATE TABLE sentinel_alerts (
    id             BIGSERIAL     PRIMARY KEY,
    severity       VARCHAR(10)   NOT NULL,    -- INFO | WARN | CRITICAL
    category       VARCHAR(50)   NOT NULL,    -- AUTH_ANOMALY | PERFORMANCE | BIZ_RULE_SPIKE | EXCEPTION_SURGE
    summary        TEXT          NOT NULL,
    detail         TEXT,
    recommendations TEXT,
    resolved       BOOLEAN       DEFAULT FALSE,
    created_at     TIMESTAMP     DEFAULT NOW()
);

CREATE INDEX idx_alerts_severity ON sentinel_alerts(severity, created_at DESC);
CREATE INDEX idx_alerts_resolved ON sentinel_alerts(resolved, created_at DESC);
```

---

### 5.2 Event Collection — The Event Writer Aspect

Add a second AOP aspect that writes to `api_event_log` after every request. This is separate from `AuditLoggingAspect` (which does console logging). This one writes structured DB records:

```java
@Aspect
@Component
@Slf4j
public class EventCollectionAspect {

    private final ApiEventLogRepository eventLogRepository;

    @Around("execution(* com.internship.student_exam_api.controller.*.*(..))")
    public Object collectEvent(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - start;

            // Only log events worth monitoring
            if (duration > 500) { // slow query threshold
                writeEvent("SLOW_QUERY", joinPoint, null, duration, null);
            }
            return result;

        } catch (BusinessRuleException e) {
            writeEvent("BIZ_RULE", joinPoint, null,
                System.currentTimeMillis() - start, e.getMessage());
            throw e;
        } catch (BadCredentialsException e) {
            writeEvent("AUTH_FAILURE", joinPoint, null,
                System.currentTimeMillis() - start, null);
            throw e;
        } catch (Exception e) {
            writeEvent("EXCEPTION", joinPoint, e.getClass().getSimpleName(),
                System.currentTimeMillis() - start, e.getMessage());
            throw e;
        }
    }

    private void writeEvent(String type, ProceedingJoinPoint jp,
                             String errorClass, long durationMs, String detail) {
        // Async write — never block the request thread for logging
        CompletableFuture.runAsync(() -> {
            ApiEventLog event = new ApiEventLog();
            event.setEventType(type);
            event.setEndpoint(jp.getSignature().toShortString());
            event.setDurationMs((int) durationMs);
            event.setErrorClass(errorClass);
            event.setDetail(truncate(detail, 500));
            // Pull from SecurityContext for user_email and ip_address
            eventLogRepository.save(event);
        });
    }
}
```

**Critical:** `@Async` / `CompletableFuture.runAsync()` — event writes must never add latency to the request path.

---

### 5.3 Sentinel Service

```java
@Service
@Slf4j
public class SentinelService {

    private final ApiEventLogRepository eventLogRepository;
    private final SentinelAlertRepository alertRepository;
    private final ChatClient chatClient;
    private final BeanOutputConverter<SentinelAnalysis> converter =
        new BeanOutputConverter<>(SentinelAnalysis.class);

    @Scheduled(fixedDelay = 300_000)  // every 5 minutes
    public void runAnalysis() {
        Instant windowStart = Instant.now().minus(5, MINUTES);
        List<ApiEventLog> window = eventLogRepository.findByOccurredAtAfter(windowStart);

        if (window.isEmpty()) return;  // No events = nothing to analyze

        // Pre-aggregate so we're not dumping raw rows into the prompt
        EventSummary summary = aggregate(window);

        // Skip LLM call if nothing unusual in the aggregation
        if (!summary.hasAnomalies()) {
            log.debug("[SENTINEL] Clean 5-minute window. No anomalies detected.");
            return;
        }

        String prompt = buildSentinelPrompt(summary);
        SentinelAnalysis analysis = callLlm(prompt, converter);

        if (analysis.requiresAlert()) {
            SentinelAlert alert = mapToAlert(analysis);
            alertRepository.save(alert);
            log.warn("[SENTINEL] {} — {}", alert.getSeverity(), alert.getSummary());
        }
    }

    private EventSummary aggregate(List<ApiEventLog> events) {
        return EventSummary.builder()
            .windowMinutes(5)
            .totalRequests(events.size())
            .authFailures(countByType(events, "AUTH_FAILURE"))
            .bizRuleViolations(countByType(events, "BIZ_RULE"))
            .exceptions(countByType(events, "EXCEPTION"))
            .slowQueries(countByType(events, "SLOW_QUERY"))
            .slowQueryAvgMs(avgDurationByType(events, "SLOW_QUERY"))
            .topEndpoints(topEndpoints(events, 5))
            .uniqueIps(countUniqueIps(events))
            .build();
    }
}
```

---

### 5.4 Sentinel Prompt Engineering

```java
private String buildSentinelPrompt(EventSummary summary) {
    return String.format("""
        You are an operational monitor for a student exam API backend.
        Analyze the following 5-minute event window and determine if any
        operational issue requires attention.
        
        BASELINES (normal operating conditions):
        - Auth failures: 0-2 per 5 minutes (students mistyping passwords)
        - Biz rule violations: 0-5 per 5 minutes (validation errors)
        - Exceptions: 0 per 5 minutes (any exception is abnormal)
        - Slow queries (>500ms): 0-1 per 5 minutes
        
        CURRENT 5-MINUTE WINDOW:
        Auth failures:         %d
        Biz rule violations:   %d
        Exceptions:            %d (classes: %s)
        Slow queries:          %d (avg %dms)
        Total requests:        %d
        Unique IPs:            %d
        Top endpoints: %s
        
        Determine:
        1. Is this window normal, unusual, or critical?
        2. If unusual/critical: what is the most likely explanation?
        3. What specific thing should an engineer check?
        
        Do not alert for normal variation.
        Only alert when the data suggests a real operational issue.
        
        %s
        """,
        summary.authFailures(), summary.bizRuleViolations(),
        summary.exceptions(), summary.exceptionClasses(),
        summary.slowQueries(), summary.slowQueryAvgMs(),
        summary.totalRequests(), summary.uniqueIps(),
        summary.topEndpoints(),
        converter.getFormat()
    );
}
```

**Structured output:**
```java
public record SentinelAnalysis(
    boolean requiresAlert,
    String severity,          // INFO | WARN | CRITICAL (null if !requiresAlert)
    String category,          // AUTH_ANOMALY | PERFORMANCE | BIZ_RULE_SPIKE | EXCEPTION_SURGE
    String summary,           // 1-2 sentences: what is happening
    String explanation,       // hypothesis: why this is happening
    String recommendation,    // specific: what to check
    String confidence         // HIGH | MEDIUM | LOW
) {}
```

---

### 5.5 Controller

```java
@RestController
@RequestMapping("/api/sentinel")
public class SentinelController {

    @GetMapping("/alerts")
    @RequirePermission(Permission.AI_INSIGHTS_VIEW)
    public ResponseEntity<Page<SentinelAlertResponse>> getAlerts(
            @RequestParam(defaultValue = "false") boolean includeResolved,
            Pageable pageable) { ... }

    @PatchMapping("/alerts/{id}/resolve")
    @RequirePermission(Permission.USER_UPDATE)  // ADMIN only
    public ResponseEntity<Void> resolveAlert(@PathVariable Long id) { ... }

    @GetMapping("/status")
    @RequirePermission(Permission.AI_INSIGHTS_VIEW)
    public ResponseEntity<SentinelStatusResponse> getStatus() {
        // Returns: last analysis time, alert counts by severity, system health
    }
}
```

---

### 5.6 Cost Analysis

Sentinel runs every 5 minutes. If an anomaly is detected (30% of windows), it calls the LLM:

- ~800 tokens per analysis call
- 0.3 × 288 calls/day = ~86 LLM calls/day
- At GPT-4o rates: ~$0.07/day

Under $3/month for continuous operational intelligence. Cost is negligible.

**Optimization:** skip LLM call if `EventSummary` shows completely normal numbers (all within baseline). The `hasAnomalies()` check prevents any LLM cost in normal operation.

---

### 5.7 Complexity Estimate

- New files: ~12 (sentinel service, event aspect, 2 controllers, 4 entities/repos, 3 DTOs, migration)
- Risk: `EventCollectionAspect` + `AuditLoggingAspect` both wrap every controller call — test that async event writes don't cause issues with test transactions
- Estimated time: 4–5 days

---

## PART 6: FRONTEND ARCHITECTURE

### 6.1 Design Philosophy

The aesthetic direction is: **monochromatic industrial + acid accent**.

Not Bloomberg Terminal as a literal reference — that's too busy. The principle borrowed from Bloomberg is: **data density without cognitive overload**. Every element earns its space. No decorative padding. Numbers are the heroes.

Color system:
```css
--bg-base: #0c0c0c;          /* near-black, not pure black */
--bg-surface: #141414;        /* card/panel backgrounds */
--bg-elevated: #1c1c1c;       /* hover states, dropdowns */
--text-primary: #f2f2f2;      /* main content */
--text-secondary: #6b6b6b;    /* labels, metadata */
--text-tertiary: #3a3a3a;     /* dividers, disabled */
--accent: #c8ff00;            /* acid lime — ONE accent, used sparingly */
--accent-dim: #8aad00;        /* secondary accent uses */
--error: #ff4d4d;
--warning: #ffaa00;
--success: #3ddc84;
--border: #242424;
```

Typography:
```
Display/Numbers: "JetBrains Mono" — data-dense, high legibility at small sizes
UI labels:      "DM Sans" — clean, not Inter (everyone uses Inter)
```

Spacing: 4px base grid. Dense. No `p-8` hero sections.

---

### 6.2 Page Hierarchy

```
/                    → Intelligence Hub (default landing)
/students            → Cohort Table
/students/:id        → Student Profile + AI Trajectory
/exams               → Exam Registry
/exams/:id           → Exam Detail + AI Quality Audit  
/results             → Results Grid (filtered, paginated)
/admin               → Admin Assistant
/sentinel            → Operational Monitor
/login               → Auth (minimal)
```

---

### 6.3 Navigation Structure

```
<Shell>
├── <Sidebar> (collapsed: 48px, expanded: 200px)
│   ├── Logo mark (top)
│   ├── NavItem: Hub          /
│   ├── NavItem: Students     /students
│   ├── NavItem: Exams        /exams
│   ├── NavItem: Results      /results
│   ├── Divider
│   ├── NavItem: Admin        /admin  [ADMIN only]
│   ├── NavItem: Sentinel     /sentinel [AI_INSIGHTS_VIEW only]
│   └── UserBadge (bottom) — email, role pill, logout
└── <MainContent>
    └── <Router.Outlet />
```

Sidebar default: **collapsed**. No labels visible, only icons. Hover to expand. This maximizes horizontal space for data-heavy pages.

---

### 6.4 Component Hierarchy

```
<App>
├── <AuthGuard>
│   └── <Shell>
│       ├── <Sidebar>
│       │   ├── <NavItem icon role? />
│       │   └── <UserBadge />
│       └── <PageContent>
│           ├── <IntelligenceHub />     /
│           ├── <StudentsPage />        /students
│           │   └── <DataTable<StudentRow> />
│           ├── <StudentDetailPage />   /students/:id
│           │   ├── <StudentHeader />
│           │   ├── <ResultHistoryTable />
│           │   ├── <SubjectSparklines />
│           │   └── <AITrajectoryPanel />
│           ├── <ExamsPage />           /exams
│           │   └── <DataTable<ExamRow> />
│           ├── <ExamDetailPage />      /exams/:id
│           │   ├── <ExamHeader />
│           │   ├── <GradeDistributionChart />
│           │   ├── <ExamStatGrid />
│           │   └── <AIQualityPanel />
│           ├── <ResultsPage />         /results
│           │   └── <DataTable<ResultRow> filterPanel />
│           ├── <AdminPage />           /admin
│           │   ├── <PromptInterface />
│           │   └── <FileUploadZone />
│           └── <SentinelPage />        /sentinel
│               ├── <AlertFeed />
│               └── <MetricGrid />
└── <LoginPage />
```

**Reusable primitives:**

```tsx
// DataTable: virtualized, sortable, with column pinning
<DataTable<T>
  data={rows}
  columns={columnDef}
  onRowClick={handleRowClick}
  pagination={pageInfo}
  loading={isLoading}
/>

// AIPanel: standardized AI output display
<AIPanel
  title="Trajectory Analysis"
  status={analysisStatus}     // idle | loading | complete | error
  onTrigger={handleAnalyze}
  fromCache={result?.fromCache}
>
  <TrajectoryContent analysis={result} />
</AIPanel>

// MetricTicker: Bloomberg-style number display
<MetricTicker
  label="Pass Rate"
  value={87.3}
  unit="%"
  trend={+2.1}         // shows green/red arrow
/>
```

---

### 6.5 State Management

**No Redux. No Context API for server state.**

- **TanStack Query v5** for all server state: fetching, caching, mutation, background refresh
- **Zustand** for two things only: auth token + sidebar collapsed state

```typescript
// All server state via TanStack Query
const { data: students, isLoading } = useQuery({
  queryKey: ['students', { page, section }],
  queryFn: () => studentApi.getAll({ page, section }),
});

const analysisMutation = useMutation({
  mutationFn: (studentId: number) => intelligenceApi.analyzeStudent(studentId),
  onSuccess: (data) => {
    queryClient.setQueryData(['analysis', 'student', studentId], data);
  },
});

// Auth state only in Zustand
const { token, user, login, logout } = useAuthStore();
```

---

### 6.6 API Integration Strategy

**TypeScript type generation from OpenAPI spec:**

```bash
# Generate types from running backend
npx openapi-typescript http://localhost:8080/v3/api-docs -o src/api/schema.d.ts
```

This means frontend types are always derived from the backend contract. No manual DTO mirroring.

```typescript
// api/client.ts
import axios from 'axios';

const client = axios.create({
  baseURL: import.meta.env.VITE_API_URL,
});

// Attach JWT to every request
client.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// 401 → redirect to login
client.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      useAuthStore.getState().logout();
      window.location.href = '/login';
    }
    return Promise.reject(err);
  }
);
```

---

### 6.7 AI Feature Integration in Frontend

**The AIPanel pattern** — used for both trajectory and quality audit:

```tsx
// AIPanel shows three states:
// 1. Idle: "Run Analysis" button
// 2. Loading: pulsing indicator + "Analyzing..." text
// 3. Complete: GSAP-animated reveal of structured output

function AITrajectoryPanel({ studentId }: { studentId: number }) {
  const [triggered, setTriggered] = useState(false);

  const { data, isLoading, mutate } = useMutation({
    mutationFn: () => intelligenceApi.analyzeStudent(studentId),
  });

  return (
    <div className="ai-panel">
      <div className="ai-panel-header">
        <span className="label">AI TRAJECTORY ANALYSIS</span>
        {data?.fromCache && <span className="cache-badge">CACHED</span>}
        <button onClick={() => mutate()} disabled={isLoading}>
          {isLoading ? 'Analyzing...' : triggered ? 'Re-run' : 'Run Analysis'}
        </button>
      </div>

      {isLoading && <AnalysisPulse />}
      {data && <TrajectoryContent analysis={data.analysis} />}
    </div>
  );
}
```

**GSAP usage — minimal and intentional:**

```typescript
// Only two GSAP moments:
// 1. AI panel reveal — staggered paragraph appearance
useGSAP(() => {
  gsap.from('.analysis-paragraph', {
    opacity: 0, y: 8, stagger: 0.06, duration: 0.4, ease: 'power2.out'
  });
}, [data]);

// 2. Alert feed new item entrance
useGSAP(() => {
  gsap.from('.alert-item:first-child', {
    opacity: 0, x: -16, duration: 0.3, ease: 'power2.out'
  });
}, [alerts]);
```

No page transition animations. No scroll triggers. No decorative motion. Every animation communicates meaning.

---

### 6.8 Intelligence Hub Page (the landing)

This is what a reviewer sees first. Design it accordingly.

```
┌─────────────────────────────────────────────────────────────────┐
│  INTELLIGENCE HUB                              2025-06-10 14:32 │
├──────────────┬──────────────┬──────────────┬────────────────────┤
│  STUDENTS    │  RESULTS     │  PASS RATE   │  ALERTS            │
│  100         │  1,247       │  78.3%       │  2 WARN / 0 CRIT  │
│  ↑2 this wk  │              │  ↑1.2% MoM   │                    │
├──────────────┴──────────────┴──────────────┴────────────────────┤
│  SECTION PERFORMANCE                                             │
│  Section A  ██████████████████████████  83.1%                  │
│  Section B  ████████████████████        74.2%                  │
├─────────────────────────────────────────────────────────────────┤
│  AT-RISK STUDENTS                    RECENT SENTINEL ALERTS     │
│  ┌──────────────────────────┐        ┌───────────────────────┐  │
│  │ Name          Risk  Pct  │        │ 14:27 WARN AUTH surge │  │
│  │ Rohan Kumar   HIGH  31%  │        │ 14:20 INFO slow query │  │
│  │ Ananya Patel  HIGH  35%  │        │ 13:55 INFO biz rule   │  │
│  │ Vikram Singh  MED   41%  │        └───────────────────────┘  │
│  └──────────────────────────┘                                   │
└─────────────────────────────────────────────────────────────────┘
```

At-risk students list: computed from `atRisk: true` fields in cached analyses, or derived directly from `percentage < 45` query. The latter is the pragmatic approach — don't require AI analysis to populate this.

---

## PART 7: FINAL ARCHITECTURE

### 7.1 Complete System Architecture

```
┌───────────────────────────────────────────────────────────────┐
│                    FRONTEND (React + TS)                       │
│  IntelligenceHub | Students | Exams | Admin | Sentinel        │
│  TanStack Query · Zustand · GSAP · OpenAPI-generated types    │
└───────────────────────────┬───────────────────────────────────┘
                            │ HTTPS + JWT Bearer
┌───────────────────────────▼───────────────────────────────────┐
│               SPRING BOOT 3.2 APPLICATION                      │
│                                                               │
│  ┌──────────────┐  ┌─────────────────┐  ┌──────────────────┐ │
│  │ JwtAuthFilter│  │JwtContextFilter │  │EventCollectAspect│ │
│  │ (auth)       │  │(permissions)    │  │(async event log) │ │
│  └──────┬───────┘  └────────┬────────┘  └─────────┬────────┘ │
│         └─────────────────────────────────────────┘          │
│                            │                                   │
│  ┌─────────────────────────▼────────────────────────────────┐ │
│  │                    CONTROLLER LAYER                       │ │
│  │  Student  Subject  Exam  Result  Auth  Intelligence       │ │
│  │  AdminAssistant  Sentinel  (@RequirePermission on all)    │ │
│  └─────────────────────────┬────────────────────────────────┘ │
│                            │  AuthorizationAspect              │
│  ┌─────────────────────────▼────────────────────────────────┐ │
│  │                    SERVICE LAYER                          │ │
│  │  StudentService  ResultService  ExamService               │ │
│  │  AcademicIntelligenceService  AdminAssistantTools         │ │
│  │  BulkFileParsingService  SentinelService(@Scheduled)      │ │
│  │  AiAnalysisCacheService                                   │ │
│  └──────┬──────────────────────────────────┬────────────────┘ │
│         │                                  │                   │
│  ┌──────▼───────┐                 ┌────────▼───────────────┐  │
│  │  REPOSITORY  │                 │    SPRING AI           │  │
│  │  LAYER       │                 │    ChatClient          │  │
│  │  JPA + JPQL  │                 │    BeanOutputConverter │  │
│  │  JOIN FETCH  │                 │    @Tool methods        │  │
│  └──────┬───────┘                 └────────┬───────────────┘  │
│         │                                  │                   │
└─────────┼──────────────────────────────────┼───────────────────┘
          │                                  │
    ┌─────▼──────────┐              ┌────────▼──────┐
    │  PostgreSQL 15  │              │  OpenAI API   │
    │  students       │              │  gpt-4o       │
    │  subjects       │              │               │
    │  exams          │              └───────────────┘
    │  results        │
    │  app_users      │
    │  ai_analysis_cache │
    │  api_event_log  │
    │  sentinel_alerts│
    └─────────────────┘
```

---

### 7.2 Sprint Sequencing

```
WEEK 1: Pre-sprint fixes
├── Pagination on all list endpoints
├── application-prod.properties fixed
├── SecurityConfig ObjectMapper injection
├── Rate limiting on /api/auth/login
├── V5 migration (compound index + new tables)
├── Spring AI dependency + configuration
└── Cache dependency (Caffeine)

WEEK 2–3: Sprint 1 (Academic Intelligence)
├── ResultAnalyticsRepository with projections
├── AcademicIntelligenceService + cache service
├── AcademicIntelligenceController
├── DTOs + structured output records
├── Prompt templates
├── Tests (mock ChatClient)
└── Verify: AI_INSIGHTS_VIEW permission wired

WEEK 4–5: Sprint 2 (Admin Assistant)
├── AdminAssistantTools with @Tool methods
├── BulkFileParsingService (Excel + CSV)
├── AdminAssistantController (prompt + upload)
├── Conversation history management
├── Preview mode for bulk upload
└── Tests (mock tools)

WEEK 6–7: Sprint 3 (Sentinel)
├── EventCollectionAspect (async)
├── SentinelService with @Scheduled
├── SentinelController
├── Alert persistence + resolution
└── Tests (@Scheduled testing pattern)

WEEK 7–10: Frontend (parallel after Sprint 1)
├── Project setup (Vite + TypeScript + Tailwind)
├── Auth flow + Shell layout
├── Students + Student Detail pages
├── Exams + Exam Detail pages
├── AI panels integration
├── Admin Assistant UI
├── Sentinel dashboard
└── Polish
```

---

### 7.3 Technical Risks

**Risk 1 — Spring AI API stability.** Spring AI 1.0.0 is GA but the `@Tool` annotation and `BeanOutputConverter` API are relatively new. Changes between patch versions have happened. Pin to a specific version in pom.xml. Read release notes before upgrading.

**Risk 2 — LLM response format failure.** `BeanOutputConverter` relies on the LLM following the JSON schema in the prompt. GPT-4o does this reliably ~95% of the time. 5% of the time it returns invalid JSON or misses a required field. Wrap all LLM calls in a retry with fallback:

```java
private <T> T callLlmWithRetry(String prompt, BeanOutputConverter<T> converter) {
    for (int attempt = 1; attempt <= 3; attempt++) {
        try {
            String response = chatClient.prompt()
                .user(prompt + "\n\n" + converter.getFormat())
                .call().content();
            return converter.convert(response);
        } catch (Exception e) {
            if (attempt == 3) throw new AiServiceException("Analysis failed after 3 attempts");
            log.warn("[AI] Parse failure attempt {}: {}", attempt, e.getMessage());
        }
    }
    throw new AiServiceException("unreachable");
}
```

**Risk 3 — EventCollectionAspect + AuditLoggingAspect both wrapping controller methods.** Two `@Around` aspects on the same pointcut. Spring AOP handles this via `@Order`. Set `@Order(1)` on `JwtAuthFilter`, `@Order(10)` on `AuditLoggingAspect`, `@Order(20)` on `EventCollectionAspect`. The `async` write in EventCollectionAspect must not hold a Hibernate session — verify there's no `@Transactional` on the event log write path.

**Risk 4 — Admin Assistant is powerful.** The LLM calling `createStudents(100 records)` could be triggered by a malicious or accidental prompt. Mitigate with: operations-per-request limit, preview mode mandatory for >5 records, every tool call audit-logged with requestor email.

**Risk 5 — Frontend CORS.** The backend has no CORS configuration (acknowledged in existing docs). Before any frontend calls work, add:

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("http://localhost:5173")); // Vite dev
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(false); // we use Authorization header, not cookies
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
}
```

This is the first thing that will break when the frontend makes its first API call.

---

### 7.4 What NOT to Build

**Do not build:**

- **Real-time WebSocket for Sentinel.** Polling `/api/sentinel/alerts` every 30 seconds is indistinguishable from real-time for human perception. WebSocket adds connection state management, heartbeats, reconnection logic. Not worth it for this scale.

- **Vector database / RAG.** The analysis cache with SHA-256 keying is sufficient. There is no semantic search requirement in this system. pgvector + embeddings is a solution looking for a problem here.

- **Custom ML model for at-risk prediction.** The AI's pattern reasoning over the structured data is already better than a logistic regression you'd train on 700 students. You don't have enough data for custom ML to beat LLM reasoning.

- **Email/push notifications from Sentinel.** Scope creep. The alert feed in the dashboard is the correct output for now.

- **Student self-service portal.** The `STUDENT` role exists and can read data. Building a portal UI for students is a separate product decision. Don't include it in this roadmap.

- **GraphQL API.** REST is correct for this domain. GraphQL adds schema definition, resolver complexity, and tooling overhead. No benefit here.

- **Microservices split.** This is a cohesive domain. A monolith is architecturally correct. "AI service" as a separate microservice would add network hops and operational complexity with no benefit at this scale.

- **Generic "chat with your data" interface.** Already decided against this. The three specific features are more valuable than a general-purpose chatbot.

- **PDF report export.** That's JasperReports work, not AI work. Different feature.

---

### 7.5 What the System Looks Like When Done

A teacher opens the application. They see 3 at-risk students on the hub. They click Rohan Kumar. His result history loads in a table. They click "Run Analysis." The AI panel reveals: his MATH grades declined 18% across three exams while CS stayed stable — foundational gap from Quiz 1. Intervention: review Quiz 1 material before End Semester.

An admin opens the admin assistant. They type: "Add the following students from Section A:" and paste a list of names and emails. The AI asks for roll numbers. Admin uploads the Excel file. Preview shows 28 students with correct mappings and 2 validation errors. Admin confirms. 28 students created.

A developer opens the sentinel dashboard at 9am. Two WARN alerts from overnight. One: 17 auth failures in 5 minutes at 2am — explanation: likely automated credential test, single IP, no successful logins. Recommendation: check if same IP appears in access logs for port scanning. The developer checks. It was a security scanner. They add the IP to the deny list.

That is a genuinely useful system. Not a demo. Not a tutorial project with AI bolted on.
