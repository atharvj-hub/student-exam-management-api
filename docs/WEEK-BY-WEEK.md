# WEEK-BY-WEEK.md — Concept Breakdown by Week

This document maps the full project to your 4-week training plan.
Use this to study the code systematically, not all at once.

---

## Week 1: Java Foundations → Project Setup

**Focus files:**
- `entity/Student.java` — classes, fields, constructors
- `enums/Grade.java`, `enums/ResultStatus.java` — enums
- `exception/ResourceNotFoundException.java` — extends RuntimeException
- `V1__initial_schema.sql` — SQL: CREATE TABLE, constraints, foreign keys

**Java concepts to internalize from this project:**

### Classes & Objects
```java
// Student.java is a class. Student s = new Student(...) is an object.
public class Student {
    private Long id;           // field
    private String name;

    public Student() {}        // no-arg constructor (required by JPA)
    public Student(String name, ...) { ... }   // convenience constructor

    public String getName() { return name; }   // getter method
    public void setName(String name) { this.name = name; } // setter method
}
```

### Inheritance (RuntimeException chain)
```java
// ResourceNotFoundException IS-A RuntimeException IS-A Exception IS-A Throwable
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " not found with id: " + id);
        // super() calls the parent class constructor
    }
}
```

### Enums
```java
// An enum is a class with a fixed set of instances
public enum Grade { A_PLUS, A, B, C, FAIL }
// Grade.A_PLUS is the only instance of A_PLUS — ever
// You can't do: Grade g = new Grade() — compile error
```

### Collections
```java
// Service layer returns List<StudentResponse>
// Stream API: transform List<Student> → List<StudentResponse>
return studentRepository.findAll()       // List<Student>
    .stream()                            // turns List into a Stream
    .map(this::toResponse)               // transforms each Student → StudentResponse
    .collect(Collectors.toList());       // collects back to List<StudentResponse>
```

### Exception Handling
```java
try {
    Student s = findStudentOrThrow(id); // throws ResourceNotFoundException if not found
} catch (ResourceNotFoundException e) {
    // handle it
}
// In our project: we DON'T catch it here. We let it propagate up to GlobalExceptionHandler.
```

---

## Week 2: Spring Boot → Student & Subject APIs

**Focus files:**
- `controller/StudentController.java` — @RestController, @GetMapping, @PostMapping, etc.
- `service/StudentService.java` — @Service, @Transactional, business logic
- `repository/StudentRepository.java` — @Repository, JpaRepository
- `dto/request/StudentRequest.java` — @Valid, @NotBlank
- `dto/response/StudentResponse.java` — @Builder
- `exception/GlobalExceptionHandler.java` — @RestControllerAdvice, @ExceptionHandler
- `application.properties` — datasource config

**Spring concepts from Week 2:**

### Dependency Injection (DI)
```java
// Spring creates ONE instance of StudentRepository and ONE instance of StudentService.
// When StudentService needs StudentRepository, Spring injects the existing instance.
@Service
public class StudentService {
    private final StudentRepository studentRepository; // injected by Spring

    public StudentService(StudentRepository studentRepository) { // constructor injection
        this.studentRepository = studentRepository;
    }
}
```

### Request/Response Cycle
```
POST /api/students { "name": "Atharv", "email": "a@b.com", "rollNumber": "CS001" }
     ↓
@RestController catches it
     ↓
@Valid validates StudentRequest (name not blank, email valid)
     ↓ if valid:
@RequestBody deserializes JSON → StudentRequest object
     ↓
studentService.createStudent(request) called
     ↓
@Transactional proxy opens transaction
     ↓
existsByEmail check → save() → transaction commits
     ↓
StudentResponse built → Jackson serializes → JSON response
     ↓
HTTP 201 Created { "id": 1, "name": "Atharv", ... }
```

### HTTP Status Codes Used in This Project
```
200 OK         → GET, PUT (successful read/update, with body)
201 Created    → POST (resource was created, body contains new resource)
204 No Content → DELETE (success, nothing to return)
400 Bad Request → Business rule violation (marks > totalMarks)
404 Not Found  → Resource doesn't exist (student id 999 not in DB)
409 Conflict   → Duplicate (email already taken)
422 Unprocessable → @Valid failed (blank name, invalid email format)
500 Server Error → Unexpected exception (caught by global handler)
```

---

## Week 3: Database & Business Logic → Exam & Result APIs

**Focus files:**
- `entity/Exam.java` — @ManyToOne, @JoinColumn, FetchType.LAZY
- `entity/Result.java` — two @ManyToOne, @Enumerated(STRING), @UniqueConstraint
- `repository/ExamRepository.java` — @Query, JOIN FETCH, N+1 prevention
- `repository/ResultRepository.java` — complex JOIN FETCH with 3-level navigation
- `service/ResultService.java` — calculatePercentage(), calculateGrade(), calculateStatus()
- `db/migration/V1__initial_schema.sql` — full schema with indexes

**Key concepts for Week 3:**

### @ManyToOne Relationship
```java
// Many Exams belong to ONE Subject
// In DB: exams table has a subject_id FK column
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "subject_id")
private Subject subject;
```

### Cascade Rules
- We use NO cascade in this project (intentional)
- Deleting a Subject does NOT auto-delete its Exams (DB FK constraint will throw)
- This is safe — you should never silently cascade deletes in an exam system

### JOIN FETCH (N+1 Prevention)
```java
// BAD: N+1 — 1 query for exams + N queries for each exam's subject
examRepo.findAll();  // then exam.getSubject() fires N more queries

// GOOD: 1 query total
@Query("SELECT e FROM Exam e JOIN FETCH e.subject")
List<Exam> findAllWithSubject();
```

### Business Logic (ResultService)
```java
// Pure functions — easy to unit test without Spring or DB
double percentage = calculatePercentage(75.0, 100.0); // 75.0
Grade grade = calculateGrade(75.0);                   // Grade.A
ResultStatus status = calculateStatus(75.0);          // ResultStatus.PASS
```

---

## Week 4: Testing & Automation

**Focus files:**
- `test_student_exam_api.py` — 20 automated test cases
- Business logic pure function tests (can be unit tested without Spring)

**Test categories in the Python suite:**

### Happy Path Tests (expect success)
- T01: Create student → 201
- T08: Create subject → 201
- T09: Create exam → 201
- T10–T14: Grade boundary tests (90%, 75%, 60%, 40%, 30%)

### Validation Tests (expect 422)
- T03: Blank name + invalid email
- T18: Empty result body

### Business Rule Tests (expect 400)
- T15: Duplicate student+exam result
- T16: Marks exceed totalMarks

### Conflict Tests (expect 409)
- T02: Duplicate student email

### Not Found Tests (expect 404)
- T06: Get student that doesn't exist
- T20: Get student after deletion

### Grade Boundary Coverage
```
Marks  | Percentage | Expected Grade | Expected Status | Test
 90    |  90.0%     | A_PLUS         | PASS            | T10
 75    |  75.0%     | A              | PASS            | T11
 60    |  60.0%     | B              | PASS            | T12
 40    |  40.0%     | C              | PASS (boundary) | T13
 30    |  30.0%     | FAIL           | FAIL            | T14
```

---

## Reverse Engineering Exercise

For each file, answer these questions:

1. **What problem does this file solve?** (without it, what breaks?)
2. **What annotations does it use?** (what does Spring/JPA do when it sees each?)
3. **What does it depend on?** (constructor parameters, @Autowired fields)
4. **What depends on it?** (who calls it / injects it)
5. **What would break if you removed one annotation?** (test by commenting it out)

### Exercise: Trace a POST /api/results request
1. What HTTP method + path maps to which controller method?
2. What validations fire before the service is called?
3. What repository calls does the service make?
4. What SQL does Hibernate generate for each call?
5. How is grade calculated? Write the logic in pseudocode.
6. What happens if the student doesn't exist?
7. What happens if marks = 150 and totalMarks = 100?
8. What HTTP status and body does the client receive in each case?
