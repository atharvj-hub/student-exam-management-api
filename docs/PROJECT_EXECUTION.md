# Project Execution Checklist

## P0

- [x] BusinessRuleException
- [x] Swagger / OpenAPI

## P1

- [x] StudentController MockMvc tests
- [x] ResultController MockMvc tests
- [x] Repository tests

## P2

- [x] README upgrade
- [x] Postman collection

## P3

- [x] Entity validation cleanup
- [x] docker-compose cleanup

## Controlled Refactor Zone

- [ ] BigDecimal migration - analysis only, not implemented without approval
- [ ] DTO architecture refactor - analysis only, not implemented without approval

## Verification

- [x] `mvn test` - passed with 32 tests
- [x] Application starts - verified on port 8081 because port 8080 was already in use
- [x] Swagger UI accessible at `/swagger-ui.html` - HTTP 200 on port 8081

## Controlled Refactor Analysis

### BigDecimal Migration

- Impact: affects `Result`, `ResultRequest`, `ResultResponse`, `ResultService`, result tests, and JSON payload expectations.
- Benefit: improves decimal precision for marks and percentages.
- Risk: medium, because request/response numeric types and test assertions change.
- Recommendation: defer until after portfolio submission unless exact decimal precision becomes a grading requirement.

### DTO Architecture Refactor

- Current state: shared request DTOs are acceptable for Student, Subject, and Exam because current PUT semantics are full replacement.
- Main concern: `ResultRequest` is shared for create and update, which allows changing `studentId` and `examId` on update.
- Recommendation: split result DTOs into create/update DTOs in a separate approved refactor, with update accepting only `marks`.
