# TaskFlow — SDD Challenge (Quarkus)

## Non-negotiable methodology
- This project follows Specification-Driven Development. `spec/openapi.yaml` is the
  SINGLE SOURCE OF TRUTH. Code conforms to the spec, never the reverse.
- NEVER write implementation code for behavior not yet defined in the spec.
  If asked to, stop and say the spec must be updated first.
- All error responses use RFC 7807 ProblemDetails. Every business-rule violation
  returns 422 with a specific `type` URI defined in the spec.

## Architecture
- RICH domain model, hexagonal architecture. Domain layer has ZERO framework
  imports (no Jakarta/JPA/Quarkus). JPA entities live only in adapters, with
  mappers.
- Business rules live in the domain entity, never in resources/controllers.
- Invariants enforced in the domain layer:
  1. Project can only be archived if no task is in_progress (422)
  2. Only pending tasks can be deleted (422)
  3. completedAt is set internally by Task#complete — never accepted as input
     (400, not 422 — malformed request, not a business rule violation)
  4. No new tasks in archived projects (422)
  5. Task status is forward-only and strictly sequential: pending → in_progress
     → done, one step at a time — no regression, no skipping steps (422)
  6. Task status cannot change while its project is archived; all other
     patchable fields (title, description, priority) remain editable (422)

## Stack
- `quarkus-impl/`: Java 25, Quarkus 3.x, JPA/Panache only in adapters, JUnit 5,
  RestAssured, Testcontainers (PostgreSQL), PIT mutation testing on domain+application.

## AI documentation discipline (challenge requirement)
- After any significant generation, remind me to run /log-ai.
- Whenever I correct, reject, or fix something you produced, remind me to run /revisar.
- Deliverable docs (`docs/decisoes.md`, `ai/*.md`) are written in Brazilian Portuguese.

## Commands
- Quarkus: `cd quarkus-impl && ./mvnw quarkus:dev` | test: `./mvnw verify`

## Git
- Conventional commits ALWAYS (feat:, fix:, chore:, docs:, test:, ci:) —
  release-please derives versions and CHANGELOG from them.
- Spec changes commit before implementation changes. Small, story-telling commits.
