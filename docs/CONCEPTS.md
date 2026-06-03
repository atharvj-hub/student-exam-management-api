# CONCEPTS.md — Java & Spring Boot Concept Map

This document explains EVERY annotation, pattern, and decision in this codebase.
Read this alongside the source code to understand the "why" behind every line.

---

## 1. Spring Core: The IoC Container

**The problem without Spring:**
```java
// You wire everything manually
StudentRepository repo = new StudentRepositoryImpl(dataSource);
StudentService service = new StudentService(repo);
StudentController controller = new StudentController(service);
// 50 classes → 200 lines of wiring code
```

**With Spring's IoC Container:**
```java
@Service
public class StudentService {
    private final StudentRepository repo;
    public StudentService(StudentRepository repo) { this.repo = repo; }
}
```
Spring scans for `@Component` / `@Service` / `@Repository` → creates instances → stores in ApplicationContext → injects wherever needed. You never call `new` on Spring-managed classes.

**ApplicationContext** = the central registry. A `Map<Class, Object>` conceptually.
On startup: fills this map. At runtime: serves instances on request.

---

## 2. Annotations Quick Reference

### Stereotype Annotations (Bean Registration)

| Annotation | What Spring Does | Used On |
|---|---|---|
| `@Component` | Base annotation: creates a Bean | Any class |
| `@Service` | Same as @Component + semantic: business logic | Service classes |
| `@Repository` | Same + enables exception translation | Repository interfaces |
| `@Controller` | Same + registers as MVC handler | Web controllers |
| `@RestController` | @Controller + @ResponseBody | REST controllers |

### HTTP Mapping Annotations

| Annotation | HTTP Method | Full Path Example |
|---|---|---|
| `@RequestMapping("/api/students")` | All methods | Base prefix for class |
| `@GetMapping` | GET | GET /api/students |
| `@PostMapping` | POST | POST /api/students |
| `@PutMapping("/{id}")` | PUT | PUT /api/students/5 |
| `@DeleteMapping("/{id}")` | DELETE | DELETE /api/students/5 |

### Parameter Extraction

| Annotation | Extracts From | Example |
|---|---|---|
| `@RequestBody` | HTTP body JSON | `@RequestBody StudentRequest req` |
| `@PathVariable` | URL path segment | `@PathVariable Long id` from `/{id}` |
| `@RequestParam` | Query string | `@RequestParam String name` from `?name=Atharv` |

### JPA / Hibernate

| Annotation | Meaning |
|---|---|
| `@Entity` | This class maps to a DB table |
| `@Table(name="students")` | Which table specifically |
| `@Id` | This is the primary key column |
| `@GeneratedValue(IDENTITY)` | DB auto-increments the ID |
| `@Column(name="roll_no", nullable=false)` | Column config |
| `@ManyToOne(fetch=LAZY)` | FK relationship, don't load eagerly |
| `@JoinColumn(name="subject_id")` | The FK column name |
| `@Enumerated(EnumType.STRING)` | Store enum as string in DB |
| `@CreationTimestamp` | Auto-set on INSERT |
| `@UpdateTimestamp` | Auto-set on every UPDATE |

### Transaction Management

| Annotation | Behavior |
|---|---|
| `@Transactional` | Wrap method in DB transaction; rollback on RuntimeException |
| `@Transactional(readOnly=true)` | Read-only optimization; Hibernate skips dirty-checking |

### Validation

| Annotation | Validates |
|---|---|
| `@NotNull` | Value is not null |
| `@NotBlank` | String is not null AND not empty AND not whitespace |
| `@Email` | Valid email format |
| `@Min(1)` | Numeric value >= 1 |
| `@Valid` | On controller param: triggers validation; failure → 422 |

### Lombok

| Annotation | Generated Code |
|---|---|
| `@Getter` | Generates `getFieldName()` for all fields |
| `@Setter` | Generates `setFieldName(value)` for all fields |
| `@NoArgsConstructor` | Generates `ClassName()` no-arg constructor |
| `@AllArgsConstructor` | Generates constructor with ALL fields |
| `@Builder` | Generates builder pattern: `ClassName.builder().field(v).build()` |
| `@Slf4j` | Generates `private static final Logger log = ...` |

---

## 3. The Request Lifecycle

Every HTTP request travels through these layers in order:

```
Browser/Postman: POST /api/students { "name": "Atharv", ... }
       │
       ▼
1. Tomcat (embedded HTTP server) — receives TCP bytes, parses HTTP
       │
       ▼
2. DispatcherServlet — Spring's front controller (every request goes here)
       │
       ▼
3. HandlerMapping — "Which @PostMapping handles POST /api/students?"
   → StudentController.createStudent()
       │
       ▼
4. @Valid fires (before method is called)
   — checks @NotBlank, @Email on StudentRequest
   — If fails: throws MethodArgumentNotValidException
   — GlobalExceptionHandler catches → 422 response
       │
       ▼
5. @RequestBody — Jackson reads JSON body → StudentRequest object
       │
       ▼
6. StudentController.createStudent(request) — your code starts here
       │
       ▼
7. Spring proxy intercepts studentService.createStudent(request)
   — @Transactional → proxy opens DB transaction (BEGIN)
       │
       ▼
8. studentRepository.existsByEmail() → SELECT COUNT(*) FROM students WHERE email = ?
   studentRepository.save(student)   → INSERT INTO students ...
       │
       ▼
9. Proxy: no exception → COMMIT
   DB connection released back to HikariCP pool
       │
       ▼
10. Returns Student entity → Service maps to StudentResponse
        │
        ▼
11. Jackson serializes StudentResponse → JSON
        │
        ▼
12. HTTP 201 Created { "id": 1, "name": "Atharv", ... }
```

---

## 4. Entity Lifecycle (Hibernate State Machine)

```
new Student()      → TRANSIENT  (Hibernate doesn't know it exists)
       │
repository.save()  → MANAGED    (Inside Hibernate Session, every change auto-tracked)
       │                        (dirty checking: setName() → Hibernate detects → UPDATE SQL on commit)
session ends       → DETACHED   (Session closed, changes no longer tracked)
       │
repo.delete()      → REMOVED    (Scheduled for DELETE, executed on commit)
```

**Dirty Checking** (how UPDATE works without calling save() explicitly):
```java
@Transactional
public Student update(Long id, StudentRequest req) {
    Student s = repo.findById(id).orElseThrow(); // s is now MANAGED
    s.setName(req.getName()); // Hibernate detects this field change
    // No save() needed — on @Transactional commit:
    // Hibernate sees name changed → runs UPDATE students SET name = ? WHERE id = ?
    return s;
}
```

**LazyInitializationException** — the most common Hibernate trap:
```java
// OUTSIDE @Transactional — session is closed
Exam exam = examRepo.findById(1L); // Exam loaded
exam.getSubject(); // BOOM — lazy load attempted after session closed
```
Fix: Use `JOIN FETCH` in your JPQL query to load everything in one query while the session is open.

---

## 5. DTOs vs Entities — Why You Never Expose @Entity Over HTTP

**Problem with exposing entities:**
- Mass assignment: `{"id": 999, ...}` → overwrite someone else's record
- LazyInitializationException during JSON serialization
- DB schema change = API contract change (tight coupling)
- Hibernate metadata leaks into response

**Pattern used in this project:**
```
API Request JSON → RequestDTO (@Valid validation) → Entity (save to DB)
DB Entity → ResponseDTO (controlled fields) → API Response JSON
```

Mapping happens in the SERVICE layer. Controller only sees DTOs.

---

## 6. @Transactional Deep Dive

### How the Proxy Works
```java
// You write:
@Transactional
public Student createStudent(StudentRequest req) {
    return repo.save(new Student(req.getName()));
}

// Spring generates (conceptually):
public Student createStudent(StudentRequest req) {    // proxy method
    entityManager.getTransaction().begin();           // BEGIN
    try {
        Student result = super.createStudent(req);    // your actual code
        entityManager.getTransaction().commit();      // COMMIT
        return result;
    } catch (RuntimeException e) {
        entityManager.getTransaction().rollback();    // ROLLBACK
        throw e;
    }
}
```

### The 3 Traps

**Trap 1: Private method — transaction silently ignored**
```java
@Transactional  // DOES NOTHING — proxy can't intercept private methods
private void doSomething() { ... }
```

**Trap 2: Self-invocation — bypasses proxy**
```java
@Service
public class StudentService {
    @Transactional
    public void createStudent() { ... }

    public void bulkCreate() {
        createStudent(); // Calls this.createStudent() → bypasses proxy → no transaction
    }
}
```

**Trap 3: Wrong exception type — doesn't rollback**
```java
@Transactional
public void doWork() throws Exception {
    repo.save(entity);
    throw new Exception("oops"); // Checked exception → @Transactional does NOT rollback by default
}
// Fix:
@Transactional(rollbackFor = Exception.class)
// Or: only throw RuntimeException (our project's approach)
```

---

## 7. Grade Calculation (Business Logic in ResultService)

```
BRD Rules:
  percentage = (marks / totalMarks) * 100

  Grade:
    A+   ← percentage >= 90
    A    ← percentage >= 75  (and < 90)
    B    ← percentage >= 60  (and < 75)
    C    ← percentage >= 35  (and < 60)
    FAIL ← percentage < 35

  Status:
    PASS ← percentage >= 40
    FAIL ← percentage < 40

Edge case: Grade C overlaps with both PASS and FAIL statuses.
  40% = Grade C, Status PASS
  38% = Grade C, Status FAIL
  34% = Grade FAIL, Status FAIL
These are independently calculated in calculateGrade() and calculateStatus().
```

---

## 8. Exception Handling Architecture

```
Any layer throws:
  ResourceNotFoundException   → 404 Not Found
  DuplicateResourceException  → 409 Conflict
  BusinessRuleException       → 400 Bad Request
  MethodArgumentNotValidException (auto, from @Valid) → 422 Unprocessable Entity
  Exception (anything else)   → 500 Internal Server Error

GlobalExceptionHandler (@RestControllerAdvice):
  Intercepts ALL exceptions thrown in any @Controller / @Service
  Maps exception type → HTTP status + ApiErrorResponse JSON
  Logs errors with appropriate level (warn for 4xx, error for 5xx)
```

---

## 9. N+1 Problem and JOIN FETCH

**N+1 Problem:**
```java
List<Exam> exams = examRepo.findAll(); // 1 query: SELECT * FROM exams
for (Exam exam : exams) {
    exam.getSubject(); // N queries: SELECT * FROM subjects WHERE id = ?
}
// 10 exams → 11 total queries. 1000 exams → 1001 queries.
```

**Fix with JOIN FETCH:**
```java
@Query("SELECT e FROM Exam e JOIN FETCH e.subject")
List<Exam> findAllWithSubject();
// 1 query: SELECT e.*, s.* FROM exams e JOIN subjects s ON e.subject_id = s.id
// Always 1 query regardless of count.
```

This is used in `ExamRepository.findAllWithSubject()` and all `ResultRepository` queries.

---

## 10. Flyway Migration Versioning

Files must follow: `V{version}__{description}.sql`
- `V1__initial_schema.sql` → runs once, creates all tables
- `V2__add_marks_index.sql` → hypothetical future migration, runs after V1

Flyway tracks in `flyway_schema_history` table:
```
version | description      | installed_on | success
1       | initial schema   | 2024-03-15   | true
2       | add marks index  | 2024-04-01   | true
```

**Never modify a migration that has already run.** Flyway checksums each file.
Changing V1 after it ran → Flyway detects checksum mismatch → app refuses to start.
Add a new V2 migration instead.
