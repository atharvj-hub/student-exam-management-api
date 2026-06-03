# Student Exam Result API — Project Context

## What This Is

A production-grade Spring Boot REST API for managing students, subjects, exams, and results.
Built as a 4-week internship project. This document is the source of truth for any AI assistant
(Codex, Claude, Cursor, etc.) working on this codebase.

---

## Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 17 |
| Framework | Spring Boot | 3.2.0 |
| ORM | Hibernate (via Spring Data JPA) | 6.x |
| Database (prod) | PostgreSQL | 15 |
| Database (test) | H2 (in-memory) | — |
| Migrations | Flyway | — |
| Containerization | Docker + Docker Compose | — |
| Build Tool | Maven | 3.9.6 |
| Boilerplate Reduction | Lombok | — |
| Test Runner | Python `requests` | — |

---

## Project Structure

```
student-exam-api/
├── src/main/java/com/atharv/examapi/
│   ├── StudentExamApiApplication.java    ← Entry point (@SpringBootApplication)
│   ├── entity/
│   │   ├── Student.java                  ← @Entity → students table
│   │   ├── Subject.java                  ← @Entity → subjects table
│   │   ├── Exam.java                     ← @Entity → exams table (@ManyToOne → Subject)
│   │   └── Result.java                   ← @Entity → results table (@ManyToOne → Student, Exam)
│   ├── enums/
│   │   ├── Grade.java                    ← A_PLUS, A, B, C, FAIL
│   │   └── ResultStatus.java             ← PASS, FAIL
│   ├── dto/
│   │   ├── request/                      ← Input DTOs (what API accepts)
│   │   │   ├── StudentRequest.java
│   │   │   ├── SubjectRequest.java
│   │   │   ├── ExamRequest.java
│   │   │   └── ResultRequest.java
│   │   └── response/                     ← Output DTOs (what API returns)
│   │       ├── StudentResponse.java
│   │       ├── SubjectResponse.java
│   │       ├── ExamResponse.java         ← contains nested SubjectResponse
│   │       ├── ResultResponse.java       ← contains nested StudentResponse + ExamResponse
│   │       └── ApiErrorResponse.java     ← standard error shape
│   ├── repository/
│   │   ├── StudentRepository.java        ← JpaRepository<Student, Long>
│   │   ├── SubjectRepository.java        ← JpaRepository<Subject, Long>
│   │   ├── ExamRepository.java           ← JpaRepository<Exam, Long> + JOIN FETCH queries
│   │   └── ResultRepository.java         ← JpaRepository<Result, Long> + JOIN FETCH queries
│   ├── service/
│   │   ├── StudentService.java           ← CRUD + duplicate checks + DTO mapping
│   │   ├── SubjectService.java           ← CRUD + duplicate checks + DTO mapping
│   │   ├── ExamService.java              ← CREATE + LIST + DTO mapping
│   │   └── ResultService.java            ← Business logic: grade/status calculation
│   ├── controller/
│   │   ├── StudentController.java        ← HTTP → Service delegation
│   │   ├── SubjectController.java
│   │   ├── ExamController.java
│   │   └── ResultController.java
│   └── exception/
│       ├── ResourceNotFoundException.java   ← 404
│       ├── DuplicateResourceException.java  ← 409
│       ├── BusinessRuleException.java        ← 400
│       └── GlobalExceptionHandler.java       ← @RestControllerAdvice
├── src/main/resources/
│   ├── application.properties              ← Default (local dev, PostgreSQL localhost)
│   ├── application-docker.properties       ← Docker profile (host = "postgres")
│   ├── application-test.properties         ← Test profile (H2 in-memory)
│   └── db/migration/
│       └── V1__initial_schema.sql          ← Flyway: creates all tables + indexes
├── Dockerfile                              ← Multi-stage build
├── docker-compose.yml                      ← PostgreSQL + App
└── test_student_exam_api.py                ← 20 automated test cases
```

---

## Database Schema

```sql
students
  id           BIGSERIAL PK
  name         VARCHAR(100) NOT NULL
  email        VARCHAR(150) NOT NULL UNIQUE
  roll_number  VARCHAR(20) NOT NULL UNIQUE
  created_at   TIMESTAMP
  updated_at   TIMESTAMP

subjects
  id            BIGSERIAL PK
  subject_name  VARCHAR(100) NOT NULL
  subject_code  VARCHAR(20) NOT NULL UNIQUE
  total_marks   INTEGER NOT NULL CHECK(total_marks >= 1)
  created_at    TIMESTAMP

exams
  id          BIGSERIAL PK
  exam_name   VARCHAR(150) NOT NULL
  subject_id  BIGINT FK → subjects.id
  exam_date   DATE NOT NULL
  created_at  TIMESTAMP

results
  id          BIGSERIAL PK
  student_id  BIGINT FK → students.id
  exam_id     BIGINT FK → exams.id
  marks       DECIMAL(8,2) NOT NULL
  percentage  DECIMAL(5,2) NOT NULL
  grade       VARCHAR(10) CHECK(grade IN ('A_PLUS','A','B','C','FAIL'))
  status      VARCHAR(10) CHECK(status IN ('PASS','FAIL'))
  created_at  TIMESTAMP
  updated_at  TIMESTAMP
  UNIQUE(student_id, exam_id)
```

---

## Business Rules

```
percentage = (marks / subject.totalMarks) * 100

Grade:
  A+   → percentage >= 90
  A    → percentage >= 75
  B    → percentage >= 60
  C    → percentage >= 35
  FAIL → percentage < 35

Status:
  PASS → percentage >= 40
  FAIL → percentage < 40

Note: Grade and Status are INDEPENDENT calculations.
  40-59% = Grade C, Status PASS
  35-39% = Grade C, Status FAIL
  <35%   = Grade FAIL, Status FAIL
```

---

## REST API Reference

### Student APIs

| Method | Path | Body | Response | Status |
|---|---|---|---|---|
| POST | /api/students | StudentRequest | StudentResponse | 201 |
| GET | /api/students | — | List<StudentResponse> | 200 |
| GET | /api/students/{id} | — | StudentResponse | 200 |
| PUT | /api/students/{id} | StudentRequest | StudentResponse | 200 |
| DELETE | /api/students/{id} | — | — | 204 |

### Subject APIs

| Method | Path | Body | Response | Status |
|---|---|---|---|---|
| POST | /api/subjects | SubjectRequest | SubjectResponse | 201 |
| GET | /api/subjects | — | List<SubjectResponse> | 200 |
| GET | /api/subjects/{id} | — | SubjectResponse | 200 |
| PUT | /api/subjects/{id} | SubjectRequest | SubjectResponse | 200 |
| DELETE | /api/subjects/{id} | — | — | 204 |

### Exam APIs

| Method | Path | Body | Response | Status |
|---|---|---|---|---|
| POST | /api/exams | ExamRequest | ExamResponse | 201 |
| GET | /api/exams | — | List<ExamResponse> | 200 |
| GET | /api/exams/{id} | — | ExamResponse | 200 |

### Result APIs

| Method | Path | Body | Response | Status |
|---|---|---|---|---|
| POST | /api/results | ResultRequest | ResultResponse | 201 |
| GET | /api/results | — | List<ResultResponse> | 200 |
| PUT | /api/results/{id} | ResultRequest | ResultResponse | 200 |
| GET | /api/results/student/{id} | — | List<ResultResponse> | 200 |

---

## Error Response Shape

All errors return:
```json
{
  "status": 404,
  "error": "NOT_FOUND",
  "message": "Student not found with id: 5",
  "timestamp": "2024-03-15T10:30:00"
}
```

Validation errors (422) also include:
```json
{
  "status": 422,
  "error": "VALIDATION_FAILED",
  "message": "Validation failed. Check validationErrors for details.",
  "validationErrors": {
    "name": "Name is required",
    "email": "Invalid email format"
  }
}
```

---

## How to Run

### Option 1: Docker Compose (Recommended)
```bash
docker-compose up --build
# API available at http://localhost:8080
```

### Option 2: Local Maven (PostgreSQL must be running)
```bash
# Start PostgreSQL on localhost:5432 with database 'student_exam_db'
mvn spring-boot:run
```

### Run Tests
```bash
# Make sure API is running first
pip install requests
python test_student_exam_api.py
# Or against custom URL:
python test_student_exam_api.py --url http://localhost:8080
```

---

## Architecture Principles

1. **Entity layer**: Java class ↔ DB table mapping. No business logic.
2. **Repository layer**: DB I/O only. No business logic. Extends JpaRepository.
3. **Service layer**: Business logic + transactions (@Transactional). Entity ↔ DTO mapping.
4. **Controller layer**: HTTP in/out only. Delegates to service. Zero business logic.
5. **DTOs**: Never expose raw @Entity over HTTP. Request DTOs in, Response DTOs out.
6. **Exceptions**: Custom typed exceptions → GlobalExceptionHandler → clean JSON errors.
7. **Flyway**: All schema changes as versioned SQL migrations, never ddl-auto=create/update in prod.
