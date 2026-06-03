# Future Engineering Roadmap & Improvements

This document outlines the proposed architectural and operational enhancements for the **Student Exam Result Management REST API**. These items represent technical debt resolutions, security hardening, and devops extensions designed to elevate the project to production-grade standards.

Each item is structured for easy conversion into GitHub Issues.

---

## 1. DTO Architecture Refactor (POST vs PUT Payload Separation)

### Problem
The application currently shares request DTOs (such as `ResultRequest.java`) between creation (`POST`) and update (`PUT`) endpoints. In addition, string fields in DTOs do not have `@Size` validation limits matching database constraints. This causes:
* **Over-posting Vulnerabilities:** Clients can modify immutable identity columns (like reassigning a student’s result record to a different student or exam ID) during an update.
* **Database Crashes:** Oversized payloads pass DTO validation but fail at the Hibernate layer with a database truncation crash (returning HTTP 500 instead of a clean validation response).

### Motivation
Proper encapsulation of request payloads is a security best practice. Splitting POST and PUT payloads prevents unauthorized modifications to immutable fields, while size-bounding input prevents unexpected database-level exceptions.

### Proposed Solution
Separate input payloads into creation-specific and update-specific models, and apply explicit length restrictions to string attributes.

### Implementation Outline
1. **Create `ResultCreateRequest.java`:** Define `studentId` (mandatory), `examId` (mandatory), and `marks` (mandatory).
2. **Create `ResultUpdateRequest.java`:** Define only `marks` (mandatory). Marks are the only mutable attribute in a result profile once the identity binding is established.
3. **Delete `ResultRequest.java`:** Safely remove the shared payload class.
4. **Update Controllers & Services:** Alter method arguments in `ResultController` and `ResultService` to consume the new split DTOs.
5. **Apply Length Constraints:** Add `@Size(max = ...)` annotations to all string fields in `StudentRequest`, `SubjectRequest`, and `ExamRequest` to align with the database columns (`VARCHAR(100)`, `VARCHAR(20)`, etc.).

---

## 2. Numeric Precision Migration (Double to BigDecimal)

### Problem
Student marks and percentages are currently stored and processed using Java `Double` values. The `Double` type is based on the IEEE 754 floating-point standard, which is prone to rounding imprecision (e.g., calculations like `0.1 + 0.2` return `0.30000000000000004`). In academic grade thresholds, an rounding error (e.g. evaluating `75.0%` as `74.999999999997%`) can incorrectly assign a lower letter grade.

### Motivation
Academic evaluation engines require deterministic, exact decimal arithmetic to ensure calculations are completely accurate.

### Proposed Solution
Migrate all fractional fields from `Double` to `BigDecimal` in the Java codebase, and use corresponding `NUMERIC` types in the PostgreSQL database.

### Implementation Outline
1. **DB Schema Migration:** Create a Flyway migration `V2__migrate_to_bigdecimal.sql` to modify database columns:
   ```sql
   ALTER TABLE results ALTER COLUMN marks TYPE NUMERIC(8,2);
   ALTER TABLE results ALTER COLUMN percentage TYPE NUMERIC(5,2);
   ```
2. **Entity Modification:** Update `Result.java` fields from `Double` to `BigDecimal`.
3. **Service Logic Update:** Revise the grading logic in `ResultService.java`:
   * Use `.divide()` with explicit scaling and `RoundingMode.HALF_UP`:
     ```java
     BigDecimal calculatedPercentage = marks
         .divide(BigDecimal.valueOf(totalMarks), 4, RoundingMode.HALF_UP)
         .multiply(BigDecimal.valueOf(100))
         .setScale(2, RoundingMode.HALF_UP);
     ```
4. **DTO Refactoring:** Update request/response DTOs to utilize `BigDecimal` for marks and percentages.
5. **Test Updates:** Fix assertion values in tests to use `BigDecimal` types.

---

## 3. Spring Security & JWT Stateless Authentication

### Problem
All endpoints on the application are currently unsecured and open to the public. Any client can create, update, or delete student profiles, exams, and grades.

### Motivation
Access control must be enforced to protect data privacy. Administrators (teachers/registrars) should have full read/write privileges, while consumers (students/parents) should have read-only access.

### Proposed Solution
Integrate Spring Security with JSON Web Token (JWT) token verification, and define a Role-Based Access Control (RBAC) schema.

### Implementation Outline
1. **Add Security Dependencies:** Add `spring-boot-starter-security` and JWT libraries (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`) to `pom.xml`.
2. **Define User Entities:** Create a `User` entity with roles: `ROLE_STUDENT`, `ROLE_ADMIN`.
3. **Configure Authentication Endpoints:** Implement sign-up and login endpoints (`/api/auth/register`, `/api/auth/login`) returning JWT tokens.
4. **Configure JWT Filters:** Write a stateless request interceptor extending `OncePerRequestFilter` to validate Bearer tokens.
5. **Enforce Role Boundaries:** Define path permissions in a `SecurityFilterChain`:
   * `GET /api/**` -> Permits authenticated users (`ROLE_STUDENT`, `ROLE_ADMIN`).
   * `POST/PUT/DELETE /api/**` -> Permits only administrators (`ROLE_ADMIN`).

---

## 4. CI/CD DevOps Pipeline Automation

### Problem
Verifications like checking compilation, running tests, checking code coverage, and validating Docker builds are done manually. This increases the risk of code regressions and broken builds.

### Motivation
Implementing a Continuous Integration (CI) pipeline verifies code stability automatically on push and pull request actions.

### Proposed Solution
Build a GitHub Actions workflow to automate compilation, testing, and containerization.

### Implementation Outline
1. **Create Workflow File:** Write `.github/workflows/ci.yml`.
2. **Configure Pipeline Steps:**
   * Trigger on pushes or pull requests targeting the `main` branch.
   * Set up JDK 17 environments.
   * Execute `./mvnw clean test` to run the test suite.
   * Incorporate the JaCoCo plugin in `pom.xml` to output code coverage analysis.
   * Verify Docker multi-stage builds compile without errors.
3. **Add Build Status Badges:** Add the pipeline build status badge to `README.md`.

---

## 5. Containerized Cloud Deployment (AWS ECS & RDS)

### Problem
The project is configured for local runtimes and Docker Compose container topologies. It lacks configuration blueprints for production cloud hosting.

### Motivation
Hosting the system in a production environment requires migrating it to cloud infrastructure that provides scalability, high availability, and secure management of database credentials.

### Proposed Solution
Create deployment blueprints to host the PostgreSQL database on AWS RDS and run the containerized Spring Boot application on AWS ECS (Fargate).

### Implementation Outline
1. **AWS RDS Database Provisioning:** Define parameters to spin up a managed PostgreSQL 15 database instance inside a private subnet.
2. **AWS ECS Fargate Cluster Setup:** Create task definitions referencing the Docker image built from the project's root `Dockerfile`.
3. **Secrets Management Integration:** Replace hardcoded properties with environment variables:
   ```properties
   spring.datasource.url=${SPRING_DATASOURCE_URL}
   spring.datasource.username=${SPRING_DATASOURCE_USERNAME}
   spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}
   ```
   Pass credentials securely from AWS Secrets Manager.
4. **Load Balancer Configuration:** Configure an Application Load Balancer (ALB) to handle incoming requests on port 80 and forward them to the container instances.

---

## 6. Metrics Collection & Observability Dashboard

### Problem
There is no mechanism to track application metrics, JVM resource usage, query performance, or API response latencies in real time.

### Motivation
Production systems require active monitoring to detect resource saturation, memory leaks, and error spikes before they impact end-users.

### Proposed Solution
Expose application health and metric statistics using Spring Boot Actuator, and export them to Prometheus and Grafana.

### Implementation Outline
1. **Add Metrics Dependencies:** Add `spring-boot-starter-actuator` and `micrometer-registry-prometheus` dependencies.
2. **Enable Actuator Endpoints:** Expose metric targets in `application.properties`:
   ```properties
   management.endpoints.web.exposure.include=health,info,metrics,prometheus
   ```
3. **Configure Prometheus Scrape Configurations:** Establish a local or cloud Prometheus config file to scrape data from `/actuator/prometheus` at regular intervals.
4. **Provision Grafana Dashboards:** Load predefined JVM and Spring Boot dashboard templates in Grafana to display memory usage, active connection pools (Hikari), active threads, and API endpoint throughput.
