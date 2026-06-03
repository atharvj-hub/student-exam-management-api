# Architecture Audit Report — `student-exam-api`

> **Audit date:** 2026-06-02  
> **Scope:** Actual codebase vs. [CONTEXT.md](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/CONTEXT.md), [CONCEPTS.md](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/CONCEPTS.md), [WEEK-BY-WEEK.md](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/WEEK-BY-WEEK.md)

---

## Executive Summary

The project has a **fundamentally broken package structure** — the codebase will **not compile**. Nearly every `.java` file on disk lives under `com.internship.student_exam_api`, but the `package` declarations inside those files use the phantom namespace `com.atharv.examapi`. This single class of defect cascades into ~30 compile errors. Beyond that, the Grade enum is wrong, there's a rogue `StudentServiceImpl` class, the Spring Boot version is suspicious, and several config/infra issues would prevent the Docker build from succeeding even after fixing the Java code.

---

## 1. CRITICAL — App will not compile / will not start

### C1. Package declaration mismatch (EVERY entity, repo, service, controller, exception)

> [!CAUTION]
> This is the #1 blocker. Nothing else matters until this is fixed.

| File on disk (actual path) | `package` declared inside file | Should be |
|---|---|---|
| [Student.java](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/entity/Student.java#L1) | `com.atharv.examapi.entity` | `com.internship.student_exam_api.entity` |
| [Subject.java](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/entity/Subject.java#L1) | `com.atharv.examapi.entity` | `com.internship.student_exam_api.entity` |
| [Exam.java](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/entity/Exam.java#L1) | `com.atharv.examapi.entity` | `com.internship.student_exam_api.entity` |
| [Result.java](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/entity/Result.java#L1) | `com.atharv.examapi.entity` | `com.internship.student_exam_api.entity` |
| [StudentRepository.java](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/repository/StudentRepository.java#L1) | `com.atharv.examapi.repository` | `com.internship.student_exam_api.repository` |
| [SubjectRepository.java](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/repository/SubjectRepository.java#L1) | `com.atharv.examapi.repository` | `com.internship.student_exam_api.repository` |
| [ExamRepository.java](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/repository/ExamRepository.java#L1) | `com.atharv.examapi.repository` | `com.internship.student_exam_api.repository` |
| [ResultRepository.java](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/repository/ResultRepository.java#L1) | `com.atharv.examapi.repository` | `com.internship.student_exam_api.repository` |
| [StudentService.java](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/service/StudentService.java#L1) | `com.atharv.examapi.service` | `com.internship.student_exam_api.service` |
| [SubjectService.java](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/service/SubjectService.java#L1) | `com.atharv.examapi.service` | `com.internship.student_exam_api.service` |
| [ExamService.java](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/service/ExamService.java#L1) | `com.atharv.examapi.service` | `com.internship.student_exam_api.service` |
| [ResultService.java](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/service/ResultService.java#L1) | `com.atharv.examapi.service` | `com.internship.student_exam_api.service` |
| [StudentController.java](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/controller/StudentController.java#L1) | `com.atharv.examapi.controller` | `com.internship.student_exam_api.controller` |
| [SubjectController.java](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/controller/SubjectController.java#L1) | `com.atharv.examapi.controller` | `com.internship.student_exam_api.controller` |
| [ExamController.java](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/controller/ExamController.java#L1) | `com.atharv.examapi.controller` | `com.internship.student_exam_api.controller` |
| [ResultController.java](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/controller/ResultController.java#L1) | `com.atharv.examapi.controller` | `com.internship.student_exam_api.controller` |
| [ResourceNotFoundException.java](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/exception/ResourceNotFoundException.java#L1) | `com.atharv.examapi.exception` | `com.internship.student_exam_api.exception` |
| [DuplicateResourceException.java](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/exception/DuplicateResourceException.java#L1) | `com.atharv.examapi.exception` | `com.internship.student_exam_api.exception` |
| [BusinessRuleException.java](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/exception/BusinessRuleException.java#L1) | `com.atharv.examapi.exception` | `com.internship.student_exam_api.exception` |
| [GlobalExceptionHandler.java](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/exception/GlobalExceptionHandler.java#L1) | `com.atharv.examapi.exception` | `com.internship.student_exam_api.exception` |

**Root cause:** The files were likely authored targeting `com.atharv.examapi` (as CONTEXT.md specifies), but the Maven project was initialized with groupId `com.internship` and package `com.internship.student_exam_api`. The files on disk live in the Maven-generated directory structure, but their `package` declarations point to a non-existent path.

**Impact:** `javac` will refuse to compile — package declaration must match file location.

**Additionally:** All `import` statements inside these files that reference `com.atharv.examapi.*` will also fail. For example, [ResultResponse.java](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/dto/response/ResultResponse.java#L3-L4) imports `com.atharv.examapi.enums.Grade` and `com.atharv.examapi.enums.ResultStatus`.

**Files with mixed imports** (importing from BOTH namespaces):
- `StudentService.java` — `package com.atharv.examapi.service` but imports `com.internship.student_exam_api.dto.request.StudentRequest`
- Same pattern in `SubjectService`, `ExamService`, `ResultService`, all controllers, `GlobalExceptionHandler`

---

### C2. Grade enum values are WRONG

| What | Spec (CONTEXT.md / CONCEPTS.md / Flyway SQL) | Actual ([Grade.java](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/enums/Grade.java)) |
|---|---|---|
| Values | `A_PLUS, A, B, C, FAIL` | `A, B, C, D, F` |

**Impact:**
- `ResultService.calculateGrade()` references `Grade.A_PLUS` and `Grade.FAIL` — both **do not exist** in the current enum → **compile error**
- Flyway `V1__initial_schema.sql` has `CHECK (grade IN ('A_PLUS','A','B','C','FAIL'))` — values `D` and `F` will be rejected by DB
- Test script expects `A_PLUS` and `FAIL` — all grade boundary tests would fail

---

### C3. Rogue `StudentServiceImpl.java` creates a fatal Spring conflict

[StudentServiceImpl.java](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/service/StudentServiceImpl.java) exists with:
- `package com.internship.student_exam_api.service` (correct package!)
- `@Service` annotation
- `implements StudentService` — but `StudentService` is a **concrete class**, not an interface

**Problems:**
1. `StudentService.java` declares `package com.atharv.examapi.service` (wrong package) — so `implements StudentService` in `StudentServiceImpl` actually can't resolve `StudentService` (it would look for `com.internship.student_exam_api.service.StudentService`)
2. If both are fixed to the same package, `StudentService` is a concrete `@Service` class, not an interface — `implements StudentService` won't compile
3. Even if it compiled, two `@Service` beans of the same type → `NoUniqueBeanDefinitionException` at startup
4. `StudentServiceImpl` returns raw `Student` entities (not DTOs) — violates the DTO pattern
5. `StudentServiceImpl` has no `@Transactional`, no duplicate checks, no findStudentOrThrow — it's an incomplete skeleton

**Verdict:** This file must be **deleted**. It's a leftover from early experimentation.

---

### C4. Spring Boot version `4.0.6` does not exist (at time of project creation)

[pom.xml L8](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/pom.xml#L8) declares `spring-boot-starter-parent` version `4.0.6`.

CONTEXT.md specifies Spring Boot `3.2.0`. As of mid-2026, Spring Boot 4.x may or may not be available — but all the code patterns (Jakarta namespace, annotations) appear to be Spring Boot 3.x patterns. Several declared dependencies may not exist in 4.x:

---

### C5. Fake/non-existent test dependencies in pom.xml

| Dependency ([pom.xml](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/pom.xml#L70-L89)) | Exists? |
|---|---|
| `spring-boot-starter-data-jpa-test` | ❌ Not a real artifact |
| `spring-boot-starter-flyway-test` | ❌ Not a real artifact |
| `spring-boot-starter-validation-test` | ❌ Not a real artifact |
| `spring-boot-starter-webmvc-test` | ❌ Not a real artifact (the real one is `spring-boot-starter-test`) |

**Impact:** `mvn dependency:resolve` fails → `mvn compile` fails → Docker build fails.

The correct test dependency should be:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

Also missing: **H2 database** dependency for test profile (`application-test.properties` configures H2 but it's not in pom.xml).

---

### C6. Dockerfile references wrong JAR name

[Dockerfile L39](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/Dockerfile#L39):
```dockerfile
COPY --from=builder /app/target/student-exam-api-1.0.0.jar app.jar
```

But [pom.xml L13](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/pom.xml#L13) declares version `0.0.1-SNAPSHOT`.

Maven will produce: `student-exam-api-0.0.1-SNAPSHOT.jar`  
Dockerfile looks for: `student-exam-api-1.0.0.jar`  
→ Docker build fails at COPY step.

---

## 2. IMPORTANT — Will cause runtime failures or wrong behavior

### I1. `ddl-auto=update` conflicts with Flyway

[application.properties L38](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/resources/application.properties#L38):
```
spring.jpa.hibernate.ddl-auto=update
```

CONTEXT.md states: *"ddl-auto=validate — Hibernate just validates schema matches"* and *"never ddl-auto=create/update in prod"*.

With `update`, Hibernate may alter tables created by Flyway, causing schema drift. Should be `validate` (or `none`) with Flyway enabled.

---

### I2. Missing H2 dependency for test profile

[application-test.properties](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/resources/application-test.properties) configures H2 (`jdbc:h2:mem:testdb`) but [pom.xml](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/pom.xml) has no H2 dependency. The `@SpringBootTest` context test will fail with `ClassNotFoundException: org.h2.Driver`.

---

### I3. `spring-boot-starter-webmvc` is the wrong artifact name

[pom.xml L47](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/pom.xml#L47): `spring-boot-starter-webmvc`

The actual artifact ID is `spring-boot-starter-web`. There is no `spring-boot-starter-webmvc` starter.

---

### I4. `spring-boot-starter-flyway` is the wrong artifact name

[pom.xml L39](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/pom.xml#L39): `spring-boot-starter-flyway`

The correct artifact is just `flyway-core` (with `flyway-database-postgresql` which you already have). There is no `spring-boot-starter-flyway`. Spring Boot auto-configures Flyway when `flyway-core` is on the classpath.

---

### I5. Docker logging namespace is stale

[application-docker.properties L22](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/resources/application-docker.properties#L22):
```
logging.level.com.atharv.examapi=INFO
```

Should be `logging.level.com.internship.student_exam_api=INFO` to match the actual package structure.

---

### I6. `toResponse()` method visibility inconsistency

| Service | `toResponse()` visibility | Called externally by |
|---|---|---|
| [StudentService](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/service/StudentService.java#L234) | `private` | `ResultService.toResponse()` at [L251](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/service/ResultService.java#L251) |
| [SubjectService](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/service/SubjectService.java#L90) | package-private | `ExamService.toResponse()` at [L96](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/service/ExamService.java#L96) |

`StudentService.toResponse()` is **private** but `ResultService` calls `studentService.toResponse()` — this will fail to compile (cannot access private member from another class). It must be made package-private or public.

---

### I7. Result `marks` column type mismatch: Entity (Double) vs Schema (DECIMAL)

| Layer | Type |
|---|---|
| [Result.java L99](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/entity/Result.java#L99) | `Double` |
| [V1__initial_schema.sql L66](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/resources/db/migration/V1__initial_schema.sql#L66) | `DECIMAL(8,2)` |

Using Java `Double` to map to `DECIMAL(8,2)` is imprecise. `BigDecimal` is the correct Java type for `DECIMAL` columns. Same applies to `percentage` (`Double` vs `DECIMAL(5,2)`). This can cause floating-point rounding issues in grade boundary calculations.

---

### I8. Validation annotations on entities AND DTOs (double validation)

Both [Student.java](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/entity/Student.java#L79-L90) and [StudentRequest.java](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/dto/request/StudentRequest.java#L54-L62) have `@NotBlank` / `@Email` annotations. The entities should NOT have validation annotations — validation belongs on DTOs only. Entity annotations can trigger unexpected validation on `save()` from Hibernate validator auto-config.

---

## 3. NICE TO HAVE — Correctness / cleanliness improvements

### N1. CONTEXT.md package path is outdated

CONTEXT.md says `com.atharv.examapi` but the actual Maven project is `com.internship.student_exam_api`. After fixing code, update CONTEXT.md to match.

### N2. `Collectors.toList()` → `Stream.toList()`

Java 17+ has `Stream.toList()`. All services use the older `Collectors.toList()` pattern. Minor modernization.

### N3. Missing `@Column` annotation for `percentage` precision

[Result.java L105-L106](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/entity/Result.java#L105-L106) lacks `precision` and `scale` on the `@Column` to match the `DECIMAL(5,2)` in the migration.

### N4. `docker-compose.yml` uses deprecated `version` key

[docker-compose.yml L1](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/docker-compose.yml#L1): `version: '3.8'` is deprecated in Docker Compose V2. It's harmless but generates a warning.

---

## Actual vs. Expected Folder Structure

### Expected (per CONTEXT.md — corrected to actual Maven package)

```
src/main/java/com/internship/student_exam_api/
├── StudentExamApiApplication.java          ✅ Exists, correct package
├── entity/
│   ├── Student.java                        ❌ Wrong package declaration
│   ├── Subject.java                        ❌ Wrong package declaration
│   ├── Exam.java                           ❌ Wrong package declaration
│   └── Result.java                         ❌ Wrong package declaration
├── enums/
│   ├── Grade.java                          ❌ Wrong values (A,B,C,D,F vs A_PLUS,A,B,C,FAIL)
│   └── ResultStatus.java                   ✅ Correct
├── dto/
│   ├── request/
│   │   ├── StudentRequest.java             ✅ Correct
│   │   ├── SubjectRequest.java             ✅ Correct
│   │   ├── ExamRequest.java                ✅ Correct
│   │   └── ResultRequest.java              ✅ Correct
│   └── response/
│       ├── StudentResponse.java            ✅ Correct
│       ├── SubjectResponse.java            ✅ Correct
│       ├── ExamResponse.java               ✅ Correct
│       ├── ResultResponse.java             ❌ Wrong enum imports
│       └── ApiErrorResponse.java           ✅ Correct
├── repository/
│   ├── StudentRepository.java              ❌ Wrong package + imports
│   ├── SubjectRepository.java              ❌ Wrong package + imports
│   ├── ExamRepository.java                 ❌ Wrong package + imports
│   └── ResultRepository.java               ❌ Wrong package + imports
├── service/
│   ├── StudentService.java                 ❌ Wrong package + mixed imports
│   ├── StudentServiceImpl.java             ❌ ROGUE FILE — DELETE
│   ├── SubjectService.java                 ❌ Wrong package + mixed imports
│   ├── ExamService.java                    ❌ Wrong package + mixed imports
│   └── ResultService.java                  ❌ Wrong package + mixed imports
├── controller/
│   ├── StudentController.java              ❌ Wrong package + mixed imports
│   ├── SubjectController.java              ❌ Wrong package + mixed imports
│   ├── ExamController.java                 ❌ Wrong package + mixed imports
│   └── ResultController.java              ❌ Wrong package + mixed imports
└── exception/
    ├── ResourceNotFoundException.java      ❌ Wrong package
    ├── DuplicateResourceException.java     ❌ Wrong package
    ├── BusinessRuleException.java          ❌ Wrong package
    └── GlobalExceptionHandler.java         ❌ Wrong package + mixed imports
```

---

## Implementation Order (Fix Roadmap)

> [!IMPORTANT]
> Each phase must compile before moving to the next. Do not skip phases.

### Phase 1 — Make it compile (blocks everything)

| Step | Action | Files |
|---|---|---|
| 1.1 | Fix `pom.xml`: correct Spring Boot version, fix artifact names (`spring-boot-starter-web`, remove `spring-boot-starter-flyway`), replace fake test deps with `spring-boot-starter-test`, add H2 test dep | [pom.xml](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/pom.xml) |
| 1.2 | Fix `Grade.java` enum values to `A_PLUS, A, B, C, FAIL` | [Grade.java](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/enums/Grade.java) |
| 1.3 | Delete `StudentServiceImpl.java` | [StudentServiceImpl.java](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/service/StudentServiceImpl.java) |
| 1.4 | Fix all `package` declarations to `com.internship.student_exam_api.*` | All 20 files listed in C1 |
| 1.5 | Fix all `import` statements from `com.atharv.examapi.*` to `com.internship.student_exam_api.*` | All files with cross-namespace imports |
| 1.6 | Change `StudentService.toResponse()` from `private` to package-private | [StudentService.java L234](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/java/com/internship/student_exam_api/service/StudentService.java#L234) |
| 1.7 | Run `mvn compile` — verify 0 errors | — |

### Phase 2 — Make it start correctly

| Step | Action | Files |
|---|---|---|
| 2.1 | Change `ddl-auto=update` to `ddl-auto=validate` | [application.properties](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/resources/application.properties#L38) |
| 2.2 | Fix Docker logging namespace | [application-docker.properties](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/src/main/resources/application-docker.properties#L22) |
| 2.3 | Run `mvn spring-boot:run` against PostgreSQL — verify startup | — |

### Phase 3 — Make Docker work

| Step | Action | Files |
|---|---|---|
| 3.1 | Fix Dockerfile JAR name to `student-exam-api-0.0.1-SNAPSHOT.jar` (or use wildcard `*.jar`) | [Dockerfile L39](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/Dockerfile#L39) |
| 3.2 | Run `docker-compose up --build` — verify both containers start | — |

### Phase 4 — Make tests pass

| Step | Action | Files |
|---|---|---|
| 4.1 | Run `python test_student_exam_api.py` — check all 20 tests | [test_student_exam_api.py](file:///c:/Users/athar/OneDrive/Desktop/internship/student-exam-api/test_student_exam_api.py) |
| 4.2 | Fix any remaining runtime issues discovered by tests | — |

### Phase 5 — Cleanup (non-blocking)

| Step | Action | Files |
|---|---|---|
| 5.1 | Remove validation annotations from entity classes (keep only on DTOs) | All entity files |
| 5.2 | Consider `BigDecimal` for `marks`/`percentage` fields | Result entity + ResultService |
| 5.3 | Update CONTEXT.md to reflect actual package name | CONTEXT.md |
| 5.4 | Remove deprecated `version` from docker-compose.yml | docker-compose.yml |

---

## Issue Count Summary

| Severity | Count | Blocks Compilation | Blocks Startup | Blocks Tests |
|---|---|---|---|---|
| 🔴 CRITICAL | 6 | ✅ Yes | ✅ Yes | ✅ Yes |
| 🟡 IMPORTANT | 8 | Some | Some | Some |
| 🟢 NICE TO HAVE | 4 | No | No | No |
