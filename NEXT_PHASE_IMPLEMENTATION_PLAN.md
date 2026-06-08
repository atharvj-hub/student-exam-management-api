# Next Phase Implementation Plan
## student-exam-api — Post-Security Sprint

**Current state:** 46/46 tests passing, JWT security complete (9 phases), Spring Boot 3.2, Java 17, Docker-ready.  
**Next sprint goal:** JaCoCo + Spring AI + Synthetic Data + CI/CD + Frontend  
**Realistic window:** 5 hours (aggressive but achievable with clear sequencing)

---

## Honest 5-Hour Feasibility Assessment

| Phase | Task | Est. Time | Risk |
|---|---|---|---|
| 0 | Swagger JWT button fix (already identified gap) | 15 min | None |
| 1 | JaCoCo configuration + HTML report + thresholds | 30 min | Low |
| 2 | Spring AI — 3 endpoints + service layer | 90 min | Medium (API key dependency) |
| 3 | Synthetic data seeder (DataFaker) | 30 min | Low |
| 4 | GitHub Actions CI pipeline | 45 min | Low |
| 5 | Frontend (Thymeleaf dashboard, no separate framework) | 75 min | Medium |

**Total: ~5h 45min.** Achievable if you skip the frontend or scope it to a read-only dashboard.  
**Recommended cut:** If time runs out, drop the frontend — the CI/CD + AI integration carries more portfolio weight.

---

## Phase 0 — Swagger JWT Fix (15 min, do this first)

This was flagged in the walkthrough as the one meaningful gap. Fix before anything else.

**File:** `src/main/java/com/internship/student_exam_api/config/OpenApiConfig.java`

Replace the existing `@OpenAPIDefinition` annotation class with a `@Bean`-based config:

```java
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
            .info(new Info()
                .title("Student Exam Result API")
                .version("2.0.0")
                .description("JWT-secured REST API. Login via POST /api/auth/login, " +
                             "paste the token in the Authorize button."))
            .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
            .components(new Components()
                .addSecuritySchemes(securitySchemeName,
                    new SecurityScheme()
                        .name(securitySchemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}
```

**No new dependencies needed.** `springdoc-openapi-starter-webmvc-ui` already includes these imports.  
**Result:** Swagger UI at `/swagger-ui.html` now shows a 🔓 Authorize button that accepts `Bearer <token>`.

---

## Phase 1 — JaCoCo Coverage (30 min)

### 1.1 pom.xml — Add JaCoCo Plugin

Add inside `<build><plugins>`:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
    <executions>
        <!-- Instrument bytecode before tests run -->
        <execution>
            <id>prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <!-- Generate HTML report after tests -->
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <!-- Enforce minimum coverage threshold — build FAILS if below -->
        <execution>
            <id>check</id>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.60</minimum> <!-- 60% line coverage -->
                            </limit>
                            <limit>
                                <counter>BRANCH</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.50</minimum> <!-- 50% branch coverage -->
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### 1.2 Exclude Generated/Config Classes

JaCoCo will try to cover Lombok-generated code, DTOs, and config classes which aren't meaningful to test.  
Add this inside the JaCoCo `<configuration>` block:

```xml
<configuration>
    <excludes>
        <exclude>**/dto/**</exclude>
        <exclude>**/entity/**</exclude>
        <exclude>**/enums/**</exclude>
        <exclude>**/config/**</exclude>
        <exclude>**/StudentExamApiApplication.class</exclude>
    </excludes>
</configuration>
```

### 1.3 Run and View Report

```bash
./mvnw clean test

# HTML report appears at:
# target/site/jacoco/index.html
# Open in browser — shows line/branch/method coverage per class
```

**With current 46 tests, expect ~70-75% line coverage** on service + controller + repository layers.  
If coverage check fails, you haven't lost tests — just add targeted tests for uncovered branches.

### 1.4 Add Badge to README.md

After CI is set up (Phase 4), the badge will auto-update. For now, add a placeholder:

```markdown
[![Coverage](https://img.shields.io/badge/coverage-70%25-brightgreen.svg)]()
```

---

## Phase 2 — Spring AI Integration (90 min)

This is the centerpiece of the next phase. The goal is **AI-powered academic analytics** — not chatbot fluff, but targeted endpoints that add real utility to the exam system.

### 2.1 What Spring AI Is

Spring AI is a Spring-native integration layer for LLMs (OpenAI, Anthropic, Gemini, Ollama, etc). It provides:
- `ChatClient` — the main API call interface
- `PromptTemplate` — structured, parameterized prompts
- `@Service` / Spring DI integration — no framework shoehorning
- Streaming, function calling, embeddings (for future phases)

**Version:** `spring-ai-openai-spring-boot-starter:1.0.0`  
**Default provider:** OpenAI (easiest to get started — needs `OPENAI_API_KEY`)  
**Local alternative:** Ollama (see Section 2.6 — no API key, runs Llama3/Mistral locally)

### 2.2 Dependency (pom.xml)

Add to `<dependencyManagement>` BOM first:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Then the actual dependency:

```xml
<!-- For OpenAI -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
</dependency>

<!-- OR for Ollama (local, no API key) -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-ollama-spring-boot-starter</artifactId>
</dependency>
```

Add the Spring AI repository to `pom.xml`:

```xml
<repositories>
    <repository>
        <id>spring-milestones</id>
        <url>https://repo.spring.io/milestone</url>
    </repository>
</repositories>
```

### 2.3 Configuration (application-dev.properties)

```properties
# OpenAI
spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.openai.chat.options.model=gpt-4o-mini
spring.ai.openai.chat.options.temperature=0.3

# OR Ollama (local)
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.options.model=llama3.2
```

### 2.4 Three AI Endpoints to Build

#### Endpoint 1 — Student Performance Report
`GET /api/ai/students/{studentId}/report`

Fetches the student's full result history, then asks the LLM to generate a personalized academic summary.

**AiInsightService.java:**
```java
@Service
public class AiInsightService {

    private final ChatClient chatClient;
    private final ResultService resultService;
    private final StudentService studentService;

    public AiInsightService(ChatClient.Builder builder,
                            ResultService resultService,
                            StudentService studentService) {
        this.chatClient = builder.build();
        this.resultService = resultService;
        this.studentService = studentService;
    }

    public String generateStudentReport(Long studentId) {
        // 1. Fetch real data from existing services
        StudentResponse student = studentService.getStudentById(studentId);
        List<ResultResponse> results = resultService.getResultsByStudent(studentId);

        // 2. Build a structured data summary (don't dump raw JSON at the model)
        String dataSummary = buildResultSummary(student, results);

        // 3. Call the LLM with a precise, role-grounded prompt
        return chatClient.prompt()
            .system("""
                You are an academic advisor AI for a student exam management system.
                Analyze the provided student performance data and generate a concise,
                professional report. Be specific, reference actual grades and subjects.
                Keep it under 200 words. Do not make up data.
                """)
            .user("Generate a performance report for this student:\n\n" + dataSummary)
            .call()
            .content();
    }

    private String buildResultSummary(StudentResponse student,
                                      List<ResultResponse> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("Student: ").append(student.getName())
          .append(" (Roll: ").append(student.getRollNumber()).append(")\n");
        sb.append("Total exams taken: ").append(results.size()).append("\n\n");

        for (ResultResponse r : results) {
            sb.append("- Subject: ").append(r.getExam().getSubject().getSubjectName())
              .append(" | Exam: ").append(r.getExam().getExamName())
              .append(" | Marks: ").append(r.getMarks())
              .append("/").append(r.getExam().getSubject().getTotalMarks())
              .append(" | Grade: ").append(r.getGrade())
              .append(" | Status: ").append(r.getStatus()).append("\n");
        }
        return sb.toString();
    }
}
```

#### Endpoint 2 — Exam Difficulty Analyzer
`GET /api/ai/exams/{examId}/analysis`

Aggregates all results for an exam (multiple students), then asks the LLM to assess difficulty and suggest adjustments.

**Prompt approach:**
```java
public String analyzeExamDifficulty(Long examId) {
    List<ResultResponse> results = resultService.getAllResultsByExam(examId);
    double avgPercentage = results.stream()
        .mapToDouble(ResultResponse::getPercentage)
        .average().orElse(0);
    long passCount = results.stream()
        .filter(r -> r.getStatus() == ResultStatus.PASS).count();

    String stats = String.format("""
        Exam: %s | Subject: %s | Total students: %d
        Average percentage: %.1f%%
        Pass rate: %d/%d (%.0f%%)
        Grade distribution: A+=%-2d | A=%-2d | B=%-2d | C=%-2d | FAIL=%-2d
        """,
        examName, subjectName, results.size(),
        avgPercentage, passCount, results.size(),
        (passCount * 100.0 / results.size()),
        countGrade(results, Grade.A_PLUS), countGrade(results, Grade.A),
        countGrade(results, Grade.B), countGrade(results, Grade.C),
        countGrade(results, Grade.FAIL));

    return chatClient.prompt()
        .system("You are an educational assessment expert. " +
                "Analyze exam difficulty and provide actionable recommendations.")
        .user("Analyze this exam's performance data:\n\n" + stats)
        .call()
        .content();
}
```

#### Endpoint 3 — At-Risk Student Detector
`GET /api/ai/insights/at-risk`

Scans all students across all exams, identifies those with concerning patterns, and generates prioritized intervention recommendations.

```java
public String detectAtRiskStudents() {
    // Aggregate: students with >1 FAIL or avg percentage < 45%
    List<StudentRiskProfile> atRisk = computeRiskProfiles();
    String profileData = formatRiskProfiles(atRisk);

    return chatClient.prompt()
        .system("""
            You are a student success coordinator. Given student performance profiles,
            identify who needs immediate intervention and suggest specific actions
            for each student. Be concise and prioritized.
            """)
        .user("Identify at-risk students and recommend interventions:\n\n" + profileData)
        .call()
        .content();
}
```

### 2.5 AiController.java

```java
@RestController
@RequestMapping("/api/ai")
@Slf4j
public class AiController {

    private final AiInsightService aiInsightService;

    public AiController(AiInsightService aiInsightService) {
        this.aiInsightService = aiInsightService;
    }

    @GetMapping("/students/{studentId}/report")
    public ResponseEntity<AiReportResponse> getStudentReport(@PathVariable Long studentId) {
        log.info("GET /api/ai/students/{}/report", studentId);
        String report = aiInsightService.generateStudentReport(studentId);
        return ResponseEntity.ok(new AiReportResponse(report, LocalDateTime.now()));
    }

    @GetMapping("/exams/{examId}/analysis")
    public ResponseEntity<AiReportResponse> getExamAnalysis(@PathVariable Long examId) {
        log.info("GET /api/ai/exams/{}/analysis", examId);
        String analysis = aiInsightService.analyzeExamDifficulty(examId);
        return ResponseEntity.ok(new AiReportResponse(analysis, LocalDateTime.now()));
    }

    @GetMapping("/insights/at-risk")
    public ResponseEntity<AiReportResponse> getAtRiskStudents() {
        log.info("GET /api/ai/insights/at-risk");
        String insights = aiInsightService.detectAtRiskStudents();
        return ResponseEntity.ok(new AiReportResponse(insights, LocalDateTime.now()));
    }
}
```

**Security:** Add `/api/ai/**` to the existing `SecurityConfig` — GET → ADMIN + STAFF, consistent with existing RBAC.

### 2.6 Ollama Alternative (Local LLM — No API Key)

If you don't want an OpenAI API key or want to demo offline:

```bash
# Install Ollama (Windows/Mac/Linux)
curl -fsSL https://ollama.ai/install.sh | sh

# Pull a model (~2GB)
ollama pull llama3.2

# Ollama runs a local server at http://localhost:11434
```

Then switch the dependency from `spring-ai-openai` to `spring-ai-ollama` and update `application.properties`. The `ChatClient` API is identical — your service code doesn't change.

**Tradeoff:** Ollama response quality is lower than GPT-4o-mini, but it's free and works offline. Perfectly fine for portfolio demos.

---

## Phase 3 — Synthetic Database Seeder (30 min)

Real data makes demos compelling. Rather than manually calling APIs, auto-populate the DB on startup.

### 3.1 Dependency

```xml
<dependency>
    <groupId>net.datafaker</groupId>
    <artifactId>datafaker</artifactId>
    <version>2.1.0</version>
    <scope>runtime</scope>
</dependency>
```

### 3.2 DatabaseSeeder.java

```java
@Component
@Profile("dev")  // Only runs in dev profile — never in prod or test
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private final StudentRepository studentRepository;
    private final SubjectRepository subjectRepository;
    private final ExamRepository examRepository;
    private final ResultService resultService;

    // Constructor injection...

    @Override
    public void run(String... args) {
        // Idempotent: skip if data already exists
        if (studentRepository.count() > 5) {
            log.info("Database already seeded — skipping");
            return;
        }

        log.info("Seeding synthetic database...");
        Faker faker = new Faker();

        // Create 5 subjects
        List<Subject> subjects = seedSubjects();

        // Create 3 exams per subject (15 total)
        List<Exam> exams = seedExams(subjects);

        // Create 30 students
        List<Student> students = seedStudents(faker, 30);

        // Create results: each student takes 4-6 random exams
        seedResults(students, exams, faker);

        log.info("Seeding complete: {} students, {} exams, {} results",
            students.size(), exams.size(), resultRepository.count());
    }

    private List<Subject> seedSubjects() {
        return List.of(
            new Subject("Mathematics", "MATH101", 100),
            new Subject("Physics", "PHY101", 100),
            new Subject("Computer Science", "CS101", 100),
            new Subject("English Literature", "ENG101", 80),
            new Subject("Chemistry", "CHEM101", 100)
        ).stream()
            .map(subjectRepository::save)
            .collect(Collectors.toList());
    }

    private List<Student> seedStudents(Faker faker, int count) {
        List<Student> students = new ArrayList<>();
        Set<String> usedEmails = new HashSet<>();
        for (int i = 0; i < count; i++) {
            String email;
            do {
                email = faker.internet().emailAddress();
            } while (usedEmails.contains(email));
            usedEmails.add(email);

            students.add(studentRepository.save(
                new Student(
                    faker.name().fullName(),
                    email,
                    "ROLL" + String.format("%04d", i + 1)
                )
            ));
        }
        return students;
    }

    // Seeding results: assign realistic marks (not just random)
    private void seedResults(List<Student> students, List<Exam> exams, Faker faker) {
        Random random = new Random();
        for (Student student : students) {
            // Each student takes 4-6 exams
            int examCount = 4 + random.nextInt(3);
            List<Exam> selectedExams = getRandomSubset(exams, examCount);

            for (Exam exam : selectedExams) {
                // Gaussian distribution: most students score 40-85%
                double marks = Math.max(0,
                    Math.min(exam.getSubject().getTotalMarks(),
                        random.nextGaussian() * 20 + 65));

                ResultCreateRequest req = new ResultCreateRequest();
                req.setStudentId(student.getId());
                req.setExamId(exam.getId());
                req.setMarks(Math.round(marks * 100.0) / 100.0);
                resultService.createResult(req);
            }
        }
    }
}
```

**Why Gaussian distribution?** Random marks between 0-100 would give a flat distribution (unrealistic). Gaussian centered at 65 with σ=20 gives a bell curve — mostly 45-85% with some fails and some A+'s, which is what real class data looks like.

---

## Phase 4 — GitHub Actions CI Pipeline (45 min)

### 4.1 File: `.github/workflows/ci.yml`

```yaml
name: CI Pipeline

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  build-and-test:
    runs-on: ubuntu-latest

    services:
      postgres:
        image: postgres:15-alpine
        env:
          POSTGRES_DB: student_test_db
          POSTGRES_USER: postgres
          POSTGRES_PASSWORD: postgres
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: maven

      - name: Build and run tests
        run: ./mvnw clean verify -P ci
        env:
          SPRING_PROFILES_ACTIVE: test
          # AI tests should be skipped in CI (no API key in CI)
          SPRING_AI_OPENAI_API_KEY: dummy-key-for-ci

      - name: Upload JaCoCo coverage report
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: jacoco-report
          path: target/site/jacoco/

      - name: Coverage check
        run: |
          echo "Coverage report generated — see artifact above"

      - name: Build Docker image
        run: docker build -t student-exam-api:ci .

  # Optional: publish coverage to PR comment
  coverage-comment:
    needs: build-and-test
    runs-on: ubuntu-latest
    if: github.event_name == 'pull_request'
    steps:
      - name: Download coverage report
        uses: actions/download-artifact@v4
        with:
          name: jacoco-report
          path: jacoco-report

      - name: Comment coverage on PR
        uses: madrapps/jacoco-report@v1.6.1
        with:
          paths: jacoco-report/jacoco.xml
          token: ${{ secrets.GITHUB_TOKEN }}
          min-coverage-overall: 60
          min-coverage-changed-files: 50
```

### 4.2 AI Test Strategy in CI

The AI endpoints call external APIs — you can't run them in CI without spending money. Two strategies:

**Option A (recommended): Skip AI tests in CI**

```java
@Test
@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
void studentReportIsGenerated() { ... }
```

**Option B: Mock the ChatClient in tests**

```java
@SpringBootTest
class AiInsightServiceTest {

    @MockBean
    private ChatClient chatClient;

    @Test
    void generateStudentReport_ReturnsMockedContent() {
        when(chatClient.prompt()).thenReturn(mockPromptSpec);
        // ... mock the chain
        String result = aiInsightService.generateStudentReport(1L);
        assertThat(result).isNotBlank();
    }
}
```

Option B is cleaner — it tests the service logic (data aggregation, prompt building) without calling OpenAI.

---

## Phase 5 — Frontend Dashboard (75 min)

### Recommended Approach: Thymeleaf (No Separate Framework)

Building a React/Vue frontend requires a separate dev server, CORS config, JWT handling in JS, and build tooling. In a 75-minute window, Thymeleaf (Spring's built-in templating engine) gives you a working UI with zero framework overhead.

**What to build:** A read-only admin dashboard (ADMIN-only, server-side rendered).

### 5.1 Add Thymeleaf Dependency

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-thymeleaf</artifactId>
</dependency>
<dependency>
    <groupId>org.thymeleaf.extras</groupId>
    <artifactId>thymeleaf-extras-springsecurity6</artifactId>
</dependency>
```

### 5.2 DashboardController.java

```java
@Controller
@RequestMapping("/dashboard")
public class DashboardController {

    private final StudentService studentService;
    private final ResultService resultService;
    private final ExamService examService;

    @GetMapping
    public String dashboard(Model model, Authentication auth) {
        model.addAttribute("studentCount", studentService.getAllStudents().size());
        model.addAttribute("recentResults", resultService.getAllResults().stream()
            .sorted(Comparator.comparing(ResultResponse::getCreatedAt).reversed())
            .limit(10)
            .collect(Collectors.toList()));
        model.addAttribute("passRate", computePassRate());
        model.addAttribute("username", auth.getName());
        return "dashboard";
    }

    @GetMapping("/students")
    public String students(Model model) {
        model.addAttribute("students", studentService.getAllStudents());
        return "students";
    }
}
```

### 5.3 SecurityConfig — Permit Dashboard

Add to `SecurityConfig.java`:

```java
.requestMatchers("/dashboard/**", "/login", "/css/**", "/js/**").permitAll()
// Or restrict dashboard to authenticated users:
.requestMatchers("/dashboard/**").authenticated()
```

### 5.4 Templates

`src/main/resources/templates/dashboard.html` — A Bootstrap 5 admin panel with:
- Student count, exam count, pass rate cards at the top
- Recent results table
- Sidebar navigation (Students / Exams / AI Insights)

Bootstrap 5 CDN means no build step needed.

### 5.5 Alternative: Standalone React SPA (if more time)

If you have the full time and want a separate frontend:

```
/frontend          ← Vite + React project
  /src
    /components
      StudentList.jsx
      ResultDashboard.jsx
      AIInsights.jsx
    /api
      client.js      ← axios with Bearer token injection
    App.jsx
  package.json
  vite.config.js
```

**CORS config in SecurityConfig:**

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("http://localhost:5173")); // Vite dev server
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    return source;
}
```

React is more impressive in a portfolio but takes 2-3 hours minimum for a functional demo. Thymeleaf is the pragmatic 75-minute choice.

---

## Execution Order for the 5-Hour Sprint

```
Hour 1 (0:00 - 1:00)
├── 0:00 - 0:15   Phase 0 — Swagger JWT fix (OpenApiConfig.java, 20 lines)
├── 0:15 - 0:45   Phase 1 — JaCoCo (pom.xml + run mvn test + check report)
└── 0:45 - 1:00   Phase 3 — Synthetic seeder (DatabaseSeeder.java)

Hour 2-3 (1:00 - 3:00)
└── Phase 2 — Spring AI
    ├── 1:00 - 1:20   pom.xml + properties (dependency + API key config)
    ├── 1:20 - 2:00   AiInsightService.java (3 methods)
    ├── 2:00 - 2:20   AiController.java + AiReportResponse DTO
    ├── 2:20 - 2:40   SecurityConfig updates + test with Swagger
    └── 2:40 - 3:00   AiInsightServiceTest.java (mocked, CI-safe)

Hour 4 (3:00 - 4:00)
└── Phase 4 — CI/CD
    ├── 3:00 - 3:30   .github/workflows/ci.yml
    └── 3:30 - 4:00   Push to GitHub, verify Actions run, fix any failures

Hour 5 (4:00 - 5:00)
└── Phase 5 — Frontend
    ├── 4:00 - 4:15   Thymeleaf dependency + DashboardController
    ├── 4:15 - 4:50   dashboard.html (Bootstrap 5, tables, stat cards)
    └── 4:50 - 5:00   SecurityConfig permit + smoke test in browser
```

---

## AI/LLM Expansion: What Else You Can Build

This section is intentionally separate — these are extensions for after the core sprint, or for a dedicated AI-focused sprint.

---

### Tier 1 — Immediate Extensions (same codebase, same stack)

#### Natural Language Query Engine
`POST /api/ai/query` — accepts plain English questions about the data:

> "How many students passed mathematics?"  
> "Who scored the highest in the last exam?"  
> "Which subject has the lowest pass rate?"

Implementation: Spring AI function calling — define your repository methods as callable "tools", let the LLM decide which to invoke.

```java
@Bean
@Description("Get all results for a specific subject by subject name")
public Function<SubjectQueryRequest, List<ResultResponse>> getResultsBySubject() {
    return request -> resultService.getResultsBySubjectName(request.subjectName());
}
```

The LLM decides when to call this based on the user's question. Your service layer stays clean.

#### Automated Exam Feedback Generator
After a result is created (`POST /api/results`), trigger an async AI call:

```java
@Async
public void generateAndStoreResultFeedback(Long resultId) {
    // AI generates: "You scored 62% in Physics. Strong performance in mechanics.
    //               Focus more on electromagnetism before the final exam."
    String feedback = aiInsightService.generateResultFeedback(resultId);
    resultFeedbackRepository.save(new ResultFeedback(resultId, feedback));
}
```

Requires a `result_feedback` table (V3 migration), but the AI call is non-blocking.

#### Grade Prediction (Pre-Exam)
`POST /api/ai/predict` — given a student ID and exam ID (before they've taken it), predict likely grade based on:
- Historical performance in the same subject
- Performance in prerequisite subjects
- Class average for this exam type

This is a classification/reasoning task, not calculation — the LLM handles uncertainty well here.

---

### Tier 2 — Embeddings + Semantic Search (1-2 days)

#### Semantic Student Search
Instead of: `GET /api/students?name=Atharv`  
You could build: `POST /api/ai/search` with body `{"query": "students struggling in STEM subjects"}`

Implementation:
1. Embed student performance profiles into vector representations (Spring AI Embedding API)
2. Store in a vector store (PGVector — PostgreSQL extension, so no new infra)
3. Query: embed the search text, find nearest vectors

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-pgvector-store-spring-boot-starter</artifactId>
</dependency>
```

#### Knowledge Base / FAQ
Embed your `CONCEPTS.md`, `CONTEXT.md`, and API documentation. Expose:  
`POST /api/ai/docs/ask` → "What HTTP status does creating a student return?"

Uses RAG (Retrieval Augmented Generation) — Spring AI has built-in support.

---

### Tier 3 — Local Agents (Ollama + LangChain4j)

This is the "something like that" you mentioned — running local agents.

#### Architecture

```
CLI or API Request
        │
        ▼
┌──────────────────┐
│  Agent Planner   │  (LLM decides what tools to use)
│  (Llama3 local)  │
└────────┬─────────┘
         │ invokes
    ┌────┼────┐
    ▼    ▼    ▼
[DB   ][API ][File  ]   ← Tool layer (your existing services)
[Tool ][Tool][Tool  ]
```

#### LangChain4j (Java Agent Framework)

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-ollama-spring-boot-starter</artifactId>
    <version>0.35.0</version>
</dependency>
```

Define an agent with tools:

```java
interface ExamAdvisorAgent {
    String chat(String userMessage);
}

@Component
class ExamAdvisorTools {

    @Tool("Get the academic performance summary for a student by their ID")
    String getStudentPerformance(Long studentId) {
        return resultService.getResultsByStudent(studentId).toString();
    }

    @Tool("Get all students who failed any exam")
    List<String> getFailingStudents() {
        return resultService.getAllResults().stream()
            .filter(r -> r.getStatus() == ResultStatus.FAIL)
            .map(r -> r.getStudent().getName())
            .distinct()
            .collect(Collectors.toList());
    }
}
```

The agent can then answer multi-step questions:
> "Compare Atharv's performance with the class average in Mathematics and tell me if he should retake the exam."

The LLM plans: fetch Atharv's results → fetch class average → compare → reason → respond.

#### What Makes This Portfolio-Worthy

Local agents are a current bleeding-edge topic in backend engineering. A Spring Boot app that runs a local Llama3 agent with access to real academic data is something almost no internship project has. It demonstrates:
- Understanding of agentic patterns
- Practical LLM integration (not just API call wrapper)
- Local-first AI (no API costs, privacy-preserving)

---

### Tier 4 — Future AI Ideas (Post-Internship)

| Idea | Technology | Complexity |
|---|---|---|
| Plagiarism detection for exam answers | Embeddings + cosine similarity | Medium |
| Adaptive difficulty recommendation | RL or scoring heuristic + LLM explanation | High |
| Multi-modal input (exam paper photos) | Vision models (GPT-4V or LLaVA) | High |
| Automated MCQ generation from subject content | Structured output (JSON mode) | Medium |
| Real-time performance alerts (Kafka + AI) | Kafka consumer + LLM | High |

---

## Updated Project State After Sprint

```
student-exam-api/
├── [EXISTING] Core REST API — Students, Subjects, Exams, Results
├── [EXISTING] Spring Security — JWT, RBAC, AuditLogging
├── [NEW] JaCoCo — Coverage reports + 60% threshold enforcement
├── [NEW] Spring AI
│   ├── AiInsightService.java — 3 AI-powered analytics methods
│   ├── AiController.java — 3 secured endpoints
│   └── AiReportResponse.java — response DTO
├── [NEW] DatabaseSeeder.java — 30 students, 15 exams, ~150 results
├── [NEW] .github/workflows/ci.yml — automated build + test + coverage
└── [NEW] Frontend
    ├── DashboardController.java — server-side rendering
    └── templates/
        ├── dashboard.html — stat cards + recent results
        └── students.html — student list table
```

---

## Portfolio Signal: What This Sprint Adds

| Before | After |
|---|---|
| REST API + Security | REST API + Security + AI Analytics |
| Manual testing | Automated CI with coverage gates |
| No data for demos | Realistic synthetic dataset (30 students, 150+ results) |
| API-only | Browser UI for non-technical demos |
| Static docs | Swagger with JWT Authorize button (fully interactive) |

The Spring AI integration is the highest-signal addition. Most backend internship projects don't touch LLMs at all. Having a production-architecture Spring Boot app that integrates AI for real academic analytics — with proper service layering, security, and CI — is a genuine differentiator.
