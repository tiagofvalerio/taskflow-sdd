
# TaskFlow — SDD Challenge (Quarkus + Rails)

## Non-negotiable methodology
- This project follows Specification-Driven Development. `spec/openapi.yaml` is the
  SINGLE SOURCE OF TRUTH. Code conforms to the spec, never the reverse.
- NEVER write implementation code for behavior not yet defined in the spec.
  If asked to, stop and say the spec must be updated first.
- All error responses use RFC 7807 ProblemDetails. Every business-rule violation
  returns 422 with a specific `type` URI defined in the spec.

## Architecture
- Both stacks share a RICH domain model philosophy; each is idiomatic to its ecosystem:
  - Quarkus: hexagonal architecture. Domain layer has ZERO framework imports
    (no Jakarta/JPA/Quarkus). JPA entities live only in adapters, with mappers.
  - Rails: idiomatic Rails-way. Business rules live in ActiveRecord models
    (rich models), controllers stay thin, standard Rails conventions apply
    (validations, scopes, strong params, rubocop-rails-omakase style).
- In BOTH stacks: business rules live in the model/entity, never in controllers.
- Invariants enforced in the model/domain layer:
  1. Project can only be archived if no task is in_progress (422)
  2. Only pending tasks can be deleted (422)
  3. Task status is forward-only: pending → in_progress → done (422 on regression)
  4. completedAt is set internally by Task#complete — never accepted as input
  5. No new tasks in archived projects (422)

## Stacks
- `quarkus-impl/`: Java 25, Quarkus 3.x, JPA/Panache only in adapters, JUnit 5,
  RestAssured, Testcontainers (PostgreSQL), PIT mutation testing on domain+application.
- `rails-impl/`: Ruby on Rails (API-only, latest stable), rich ActiveRecord models,
  RSpec request specs, committee gem for OpenAPI conformance, FactoryBot,
  rubocop with rails-omakase style.

## Ruby mentoring mode (I am new to Ruby)
- In rails-impl/, whenever you write Ruby, add a short "Ruby notes" summary after
  the code: idioms used (blocks, symbols, bang methods, callbacks, concerns) and
  why they're conventional. Keep it brief — 3-5 bullets max.
- Prefer the boring, conventional Rails solution over clever metaprogramming.
- Flag anything a Rails interviewer would expect me to be able to explain.

## AI documentation discipline (challenge requirement)
- After any significant generation, remind me to run /log-ai.
- Whenever I correct, reject, or fix something you produced, remind me to run /revisar.
- Deliverable docs (`docs/decisoes.md`, `ai/*.md`) are written in Brazilian Portuguese.

## Commands
- Quarkus: `cd quarkus-impl && ./mvnw quarkus:dev` | test: `./mvnw verify`
- Rails: `cd rails-impl && bin/rails s` | test: `bundle exec rspec`

## Git
- Conventional commits ALWAYS (feat:, fix:, chore:, docs:, test:, ci:) —
  release-please derives versions and CHANGELOG from them.
- Spec changes commit before implementation changes. Small, story-telling commits.
