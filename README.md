# Student Exam Result Management REST API

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/projects/jdk17/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue.svg)](https://www.postgresql.org/)
[![Flyway](https://img.shields.io/badge/Flyway-9.22-red.svg)](https://flywaydb.org/)
[![Docker](https://img.shields.io/badge/Docker-Enabled-blue.svg)](https://www.docker.com/)

A production-grade, multi-module Spring Boot REST API for orchestrating the student academic lifecycle: student enrollment, subject/course ceiling management, exam timetabling, and automated grading computations.

This repository represents the final packaged deliverables of a Java + Spring Boot backend engineering internship.

---

## 📖 Table of Contents
1. [Project Overview](#-project-overview)
2. [System Architecture](#-system-architecture)
3. [Technology Stack](#-technology-stack)
4. [Database Design](#-database-design)
5. [Business Rules Engine](#-business-rules-engine)
6. [API Endpoint Reference](#-api-endpoint-reference)
7. [Local Setup & Installation](#-local-setup--installation)
8. [Docker & Containerization](#-docker--containerization)
9. [Testing Suite](#-testing-suite)
10. [API Documentation (Swagger & Postman)](#-api-documentation-swagger--postman)
11. [Future Improvements & GitHub Roadmap](#-future-improvements--github-roadmap)
12. [Visual Placeholders & Screenshots](#-visual-placeholders--screenshots)

---

## 🌟 Project Overview

The **Student Exam Result Management System** is a high-throughput REST API designed to eliminate manual grading efforts while ensuring complete data integrity. It automates:
* **Student Registers:** Managing student records with unique identifying fields.
* **Course Cataloging:** Setting custom maximum marks per subject.
* **Exam Registries:** Scheduling exams dynamically linked to existing subjects.
* **Automatic Grade Processing:** Auto-computing test score percentages, assigning final academic grades, and calculating pass/fail evaluations in real-time.
* **Graceful Exception Interceptors:** Formatting Spring Boot runtime violations into structured, consumer-ready JSON response frames.

---

## 📐 System Architecture

The application adheres to clean **Layered Architecture (3-Tier)** principles, ensuring a strict separation of concerns between HTTP serialization, domain validations, transaction scopes, and data persistence.

```text
       HTTP REQUEST (JSON)
              │
              ▼
    [ Controller Layer ]      <─── Handles Routing, JSON serialization, and @Valid validations
              │
              ▼
      [ Service Layer ]       <─── Handles Transaction Scopes (@Transactional), Maps DTO ↔ Entity
              │
              ▼
    [ Repository Layer ]      <─── Handles JPQL generation, Spring Data JPA, Hibernate mappings
              │
              ▼
     [ PostgreSQL Database ]  <─── Database Schema
```

### Key Architectural Patterns
* **DTO Separation:** Decouples the database models from HTTP payloads to prevent mass-assignment attacks and lazy-loading exceptions.
* **Global REST Exception Handler:** Intercepts runtime errors (e.g., `ResourceNotFoundException`, `BusinessRuleException`) and converts them into structured `ApiErrorResponse` responses with `422`, `404`, `409`, or `500` status codes.
* **Flyway Migration Engine:** Handles version-controlled database migrations on startup.

---

## 🛠️ Technology Stack

| Layer | Component | Version / Tooling |
| :--- | :--- | :--- |
| **Runtime Environment** | OpenJDK JVM | Java 17 |
| **Web Framework** | Spring Boot | 3.2.0 |
| **Object-Relational Mapper** | Hibernate / Spring Data JPA | Included with Starter Data JPA |
| **Database Migration** | Flyway | 9.22.3 |
| **Relational Database** | PostgreSQL | PostgreSQL 15 (Alpine) |
| **Testing - Unit & Mocking** | JUnit 5 & Mockito | 5.x / 3.x |
| **Testing - Integration** | Spring MockMvc & H2 | H2 In-Memory DB (isolated profile) |
| **Testing - Regression** | Python Requests | Python 3.x (`tests/test_student_exam_api.py`) |
| **API Documentation** | OpenAPI / Swagger UI | Springdoc-OpenAPI 2.3.0 |
| **Containerization** | Docker Engine & Compose | Multi-stage Dockerfile / Compose v3.8 |

---

## 💾 Database Design

The relational database is managed through Flyway version-controlled migrations (`src/main/resources/db/migration/V1__initial_schema.sql`). 

```
  ┌────────────────┐         ┌────────────────┐
  │    STUDENTS    │         │    SUBJECTS    │
  ├────────────────┤         ├────────────────┤
  │ PK id          │         │ PK id          │
  │    name        │         │    subject_name│
  │    email (U)   │         │    subject_code│
  │    roll_num (U)│         │    total_marks │
  └───────┬────────┘         └───────┬────────┘
          │ 1                        │ 1
          │                          │
          │ 1..*                     │ 1..*
  ┌───────▼────────┐         ┌───────▼────────┐
  │    RESULTS     │         │     EXAMS      │
  ├────────────────┤         ├────────────────┤
  │ PK id          │         │ PK id          │
  │ FK student_id  ├────────>│ FK subject_id  │
  │ FK exam_id     │ 1..*    │    exam_name   │
  │    marks       ├────────>│    exam_date   │
  │    percentage  │         └────────────────┘
  │    grade       │ 1
  │    status      │
  └────────────────┘
  (student_id, exam_id) UNIQUE
```

### Table Schema Summary
1. **`students`**: Handles enrollments. Unique constraints protect `email` and `roll_number`.
2. **`subjects`**: Holds course information. Defines `total_marks` which establishes the grading ceiling.
3. **`exams`**: Schedulers linked directly to `subjects(id)` through a Foreign Key relationship.
4. **`results`**: Evaluator mapping `students` to `exams`. Incorporates a database-level composite unique constraint on `(student_id, exam_id)` to prevent duplicate grading, along with audit tracking columns (`created_at`, `updated_at`).

---

## 🧮 Business Rules Engine

Automated grade outcomes are evaluated dynamically in `ResultService.java` according to these criteria:

* **Percentage Calculation:** $\text{percentage} = \frac{\text{marks}}{\text{subject.totalMarks}} \times 100$
* **Passing Threshold:** Percentage $\ge 40\%$ returns a `PASS` status, otherwise `FAIL`.
* **Academic Grades:**
  
| Percentage Range | Assigned Grade | Pass / Fail Status |
| :--- | :--- | :--- |
| $\ge 90.00\%$ | `A_PLUS` | PASS |
| $75.00\% - 89.99\%$ | `A` | PASS |
| $60.00\% - 74.99\%$ | `B` | PASS |
| $35.00\% - 59.99\%$ | `C` | PASS / FAIL (Depends on threshold) |
| $< 35.00\%$ | `FAIL` | FAIL |

* **Integrity Guard:** Attempts to save a student score exceeding the subject's maximum `total_marks` throws a `BusinessRuleException` (resolving to HTTP `400 Bad Request`).

---

## 📞 API Endpoint Reference

| Method | Endpoint | Payload / Params | Response Code | Description |
| :--- | :--- | :--- | :--- | :--- |
| **POST** | `/api/students` | `StudentRequest` | `201 Created` | Register a new student |
| **GET** | `/api/students` | None | `200 OK` | Fetch all registered students |
| **GET** | `/api/students/{id}` | Path variable `id` | `200 OK` / `404` | Get student profile details |
| **PUT** | `/api/students/{id}` | `StudentRequest` | `200 OK` / `404` | Modify a student's profile |
| **DELETE** | `/api/students/{id}` | Path variable `id` | `204 No Content` | Delete a student's history |
| **POST** | `/api/subjects` | `SubjectRequest` | `201 Created` | Create a new subject |
| **GET** | `/api/subjects` | None | `200 OK` | List all available subjects |
| **POST** | `/api/exams` | `ExamRequest` | `201 Created` | Schedule an exam event |
| **GET** | `/api/exams` | None | `200 OK` | List all scheduled exams |
| **POST** | `/api/results` | `ResultRequest` | `201 Created` | Calculate and record a score |
| **GET** | `/api/results` | None | `200 OK` | Fetch all exam results |
| **PUT** | `/api/results/{id}` | `ResultRequest` | `200 OK` / `404` | Revise an exam score |
| **GET** | `/api/results/student/{studentId}`| Path variable `studentId`| `200 OK` | List all results for a student |

---

## 🛠️ Local Setup & Installation

### Local Manual Run
1. **Prepare Database:** Create a local PostgreSQL database named `student_dev` on port `5432`.
2. **Configure properties:** Verify PostgreSQL credentials in `src/main/resources/application-dev.properties`.
3. **Execute Build:**
   ```bash
   # Windows PowerShell
   .\mvnw.cmd clean spring-boot:run
   
   # Linux / macOS
   ./mvnw clean spring-boot:run
   ```
4. Check startup logs to confirm Flyway migrations completed.

---

## 🐳 Docker & Containerization

A complete multi-container setup is orchestrated using Compose.

* **Multi-stage Dockerfile:** Builds the clean jar in a Maven environment, then copies it into a lightweight alpine JRE container to keep the image size small (~200MB).
* **Compose Orchestration:** Couples the application service with PostgreSQL 15, verifying database health checks before booting the API.

### Run Containers
```bash
# Build and run containers in detached mode
docker compose up --build -d
```
Check status using `docker compose ps` to ensure both containers are healthy.

### Prune and Stop
```bash
# Remove containers and clear persistence volumes
docker compose down -v
```

---

## 🧪 Testing Suite

### 1. Spring Boot Java Tests
Verifies MVC configurations, JPA repositories, mock services, and validation handling.
```bash
# Execute unit & integration tests
# Windows
.\mvnw.cmd test

# Linux/macOS
./mvnw test
```

### 2. Python Regression Script
Validates the full REST interface against 48 integration test scenarios.
```bash
# Run regression tests (with the server running)
python tests/test_student_exam_api.py
```

---

## 📖 API Documentation (Swagger & Postman)

* **Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
* **OpenAPI V3 Docs:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

### Importable Postman Collections
A comprehensive suite containing mock payloads, pre-request dynamic data binds, and path parameters is available at:
`docs/postman/Student_Exam_Result_API.postman_collection.json`.

---

## 🔮 Future Improvements & GitHub Roadmap

We maintain a roadmap for proposed enterprise enhancements. Check [docs/FUTURE_IMPROVEMENTS.md](docs/FUTURE_IMPROVEMENTS.md) for detailed scopes on:
1. **DTO Refactoring:** Separating POST and PUT payloads to avoid parameter pollution.
2. **BigDecimal Migrations:** Switching grades and marks storage to prevent float rounding errors.
3. **Spring Security Core:** Guarding endpoints with JWT stateless authentication filters and RBAC.
4. **CI/CD Automation Pipelines:** Building GitHub Actions for testing and staging deployments.
5. **Cloud Deployments:** Blueprints to run containers on AWS ECS Fargate + RDS.
6. **Observability Monitors:** Introducing Spring Actuator metrics exportable to Prometheus & Grafana.

---

## 📸 Visual Placeholders & Screenshots

> *Note: Before presenting this portfolio to recruiters, insert system execution screenshots below.*

### 1. Active Swagger UI documentation
`[Insert Screenshot of Swagger Endpoint Reference UI here]`

### 2. Maven Build Test Suite Output (BUILD SUCCESS)
`[Insert Screenshot of Spring boot Maven test execution CLI here]`

### 3. Python Regression Script output (48/48 PASS)
`[Insert Screenshot of Python script CLI pass run here]`
