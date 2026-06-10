# Review Scope & Decision Framework

This project is an internship-grade Student Exam Management System being evolved into a strong portfolio project.

When reviewing the codebase:

## Primary Goal

Prioritize:

1. Security vulnerabilities
2. Production stability
3. Performance bottlenecks
4. Testability
5. Maintainability
6. AI feature readiness

## Do NOT optimize for:

* Enterprise-scale multi-tenancy
* Regulatory compliance (FERPA, HIPAA, GDPR)
* Global-scale deployment
* Microservice decomposition
* API versioning unless breaking changes exist
* Kubernetes-specific concerns
* Multi-region architecture

## Recommendation Rules

Before proposing a change, classify it as:

### Critical

Would cause:

* Security vulnerability
* Data loss
* Authentication/authorization bypass
* Production outage

### High

Would cause:

* Significant performance degradation
* Maintainability problems
* Scalability issues within expected project scope

### Medium

Useful improvement but not blocking.

### Low

Nice-to-have only.

Do not spend review effort on Low items until all Critical and High items are addressed.

## Authorization Architecture

Custom AOP authorization using:

* @RequirePermission
* AuthorizationAspect
* JwtRequestContext

is an intentional design decision.

Do not recommend replacing it with Spring Security @PreAuthorize.

Instead:

* identify weaknesses
* suggest safeguards
* suggest tests
* suggest architectural protections

while preserving the custom authorization framework.

## AI Roadmap

Planned AI features:

1. Academic Intelligence Suite
2. Backend Sentinel
3. Admin Natural Language Assistant

Recommendations should support these goals and not introduce unrelated platform complexity.

## Review Philosophy

Prefer:

* pragmatic improvements
* high ROI changes
* portfolio-quality architecture

Avoid:

* enterprise overengineering
* speculative future requirements
* changes with low impact and high complexity
## Decision Framework

This project is intended to become:

* Portfolio project
* Internship showcase
* AI-enabled application

Review recommendations should optimize for:

* Learning value
* Engineering quality
* Demonstrable architecture
* Realistic production practices

Avoid recommendations that significantly increase complexity without providing clear educational or portfolio value.
# REVIEW_RULES.md

## Project Stage

Current Stage:
Phase 0 Hardening

Upcoming Stages:

1. Frontend Integration
2. AI Sprint 1 - Academic Intelligence
3. AI Sprint 2 - Backend Sentinel
4. AI Sprint 3 - Admin Assistant

## Review Priorities

Priority Order:

1. Security vulnerabilities
2. Authentication / Authorization flaws
3. Data integrity risks
4. Performance bottlenecks
5. Scalability within expected project scope
6. Test coverage gaps

## Do Not Prioritize

Unless a Critical issue exists:

* API versioning
* Multi-tenancy
* Microservices
* Kubernetes
* Multi-region deployment
* Regulatory compliance frameworks
* Enterprise IAM redesigns
* Premature observability platforms

## Architecture Constraints

The following are intentional design decisions:

* Spring Boot monolith
* JWT Authentication
* Custom AOP Authorization Framework
* PostgreSQL
* Docker deployment

Do not recommend replacing these technologies.

Instead:

* identify weaknesses
* propose safeguards
* improve testing
* improve maintainability

## Recommendation Classification

Critical:

* Security vulnerability
* Auth bypass
* Data loss
* Production outage

High:

* Significant scalability issue
* Serious maintainability risk

Medium:

* Useful improvement

Low:

* Nice-to-have

Do not spend significant review effort on Low findings when Critical or High findings remain unresolved.
