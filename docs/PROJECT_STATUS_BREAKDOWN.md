# Project Status Breakdown

This file summarizes what is finished, partially finished, and not started in the `student-exam-api` project, based on the current codebase and supporting documentation.

## Overall View

- Core REST API: mostly finished
- Database schema: finished
- Layered Spring Boot structure: finished
- Testing and regression automation: partial
- Production hardening: partial
- Documentation: partial

## Finished

| Area | Status | Evidence |
|---|---|---|
| Spring Boot application setup | Finished | `src/main/java/com/internship/student_exam_api/StudentExamApiApplication.java` |
| Student module | Finished | `StudentController`, `StudentService`, repository, DTOs, entity |
| Subject module | Finished | `SubjectController`, `SubjectService`, repository, DTOs, entity |
| Exam module | Finished | `ExamController`, `ExamService`, repository, DTOs, entity |
| Result module | Finished | `ResultController`, `ResultService`, repository, DTOs, entity |
| DTO-based API design | Finished | Request and response DTOs are used instead of exposing entities directly |
| Validation at API boundary | Finished | `@Valid` on controller inputs and validation annotations on request DTOs |
| Global exception handling | Finished | `GlobalExceptionHandler` with custom exception mapping |
| Flyway schema migration | Finished | `src/main/resources/db/migration/V1__initial_schema.sql` |
| Database table design | Finished | `students`, `subjects`, `exams`, `results` tables exist |
| Dockerfile | Finished | Multi-stage Docker build exists |
| Docker Compose setup | Finished | App and PostgreSQL services are defined |
| Result business rules | Finished | Grade and pass/fail logic exists in `ResultService` |
| Basic unit tests for result logic | Finished | `src/test/java/com/internship/student_exam_api/service/ResultServiceTest.java` |
| Support documentation | Finished | `CONTEXT.md`, `CONCEPTS.md`, `WEEK-BY-WEEK.md`, `architecture_audit.md` |

## Partially Finished

| Area | Status | What is done | What is still missing |
|---|---|---|---|
| Week 4 testing | Partial | Some unit tests exist for result logic and a Spring smoke test exists | Missing controller tests, repository tests, and true integration tests |
| Regression automation | Partial | `test_student_exam_api.py` exists | Not verified as passing in this review; not yet tied into CI |
| Spring profiles | Partial | `dev`, `test`, `docker`, and `prod` files exist | Base config still forces `dev`, and `prod` is not production-safe |
| Production config | Partial | Separate prod file exists | Hardcoded dev-style DB settings and verbose logging remain |
| Numeric modeling | Partial | Marks and percentage are implemented | `Double` is still used where `BigDecimal` would be safer |
| Documentation completeness | Partial | Several design docs already exist | No confirmed Postman collection, API reference, or final user-facing guide |

## Not Started

| Area | Status | Why it is not done yet |
|---|---|---|
| Controller integration tests | Not started | No `MockMvc` or web-layer test suite found |
| Repository tests | Not started | No `@DataJpaTest` suite found |
| Flyway integration test coverage | Not started | No explicit test verifying migration startup and schema behavior |
| Postman collection | Not started | No collection file found in the repository |
| OpenAPI / Swagger documentation | Not started | No generated API contract found |
| Security hardening | Not started | No authentication, authorization, or secret externalization yet |
| CI pipeline | Not started | No build/test automation pipeline file found |

## Week-by-Week Mapping

### Week 1: Java Programming

Status: Finished

- Project structure exists
- Database schema exists
- Core entities and repositories exist

### Week 2: Spring Boot

Status: Finished

- REST controllers exist
- Service layer exists
- CRUD flows are implemented for the main modules

### Week 3: Database and Business Logic

Status: Finished

- JPA entities and repository layer exist
- Relationships are modeled
- Result grading and pass/fail rules are implemented

### Week 4: Testing and Regression Automation

Status: Partial

- Some unit tests exist
- Regression script exists
- Integration coverage and reliable automation are still missing

## Short Verdict

The project is past the core build stage. The remaining work is mostly quality and delivery work:

- make tests reliable
- make profiles production-safe
- improve data types and configuration
- add missing integration and documentation artifacts

