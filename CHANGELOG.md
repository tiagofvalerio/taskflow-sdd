# Changelog

## 1.0.0 (2026-07-08)


### Features

* **quarkus:** application layer — use cases and outbound ports ([26cb470](https://github.com/tiagofvalerio/taskflow-sdd/commit/26cb4704bb153ab7fadd891b3242dc4507f8b637))
* **quarkus:** domain layer — Project/Task entities with the 6 business rules ([a1e6e5d](https://github.com/tiagofvalerio/taskflow-sdd/commit/a1e6e5dc52415337110074cb2ee29390e9f62a6f))
* **quarkus:** domain layer with 6 business rules + unit tests; ArchUnit purity enforcement ([f99650d](https://github.com/tiagofvalerio/taskflow-sdd/commit/f99650d6aea94f646a13392ea25cdc141131e080))
* **quarkus:** persistence adapter — jpa entities, mappers, panache repos, flyway schema ([c317388](https://github.com/tiagofvalerio/taskflow-sdd/commit/c31738822173216ae8672f20c0cec58bdc245129))
* **quarkus:** rest adapter — resources, dtos, rfc 7807 error taxonomy ([188435c](https://github.com/tiagofvalerio/taskflow-sdd/commit/188435c11289001c22a0a8d37a911aea5a40c640))
* **quarkus:** use cases and ports with in-memory fakes; Task.changeStatusTo as single transition entry point ([a8b4fb6](https://github.com/tiagofvalerio/taskflow-sdd/commit/a8b4fb605d93024e32a267e59f4464e6a89ad8b5))
* **spec:** fix interpretation gaps found by contract suite — parse-failure type URI, canonical UUID form; rationale in decisoes.md ([f1e1039](https://github.com/tiagofvalerio/taskflow-sdd/commit/f1e10390344bb0e1989962efbe36af11d4ec08a0))
* **spec:** OpenAPI 3.0 contract for projects and tasks — source of truth ([9bfadc2](https://github.com/tiagofvalerio/taskflow-sdd/commit/9bfadc2efe1d4e0ce2ad101c1ea61f139647ba8a))


### Bug Fixes

* **quarkus:** reject scalar coercion in request bodies per spec type contract ([5d20b93](https://github.com/tiagofvalerio/taskflow-sdd/commit/5d20b936873098ba64ae1bba13c4fad387a5a1b4))
* **quarkus:** rfc 7807 for jackson body failures; unsigned uuid tie-break in fakes ([f1d66d9](https://github.com/tiagofvalerio/taskflow-sdd/commit/f1d66d9243a26062cd042a280e0ffc03c43862b4))
* **security:** pin postgresql 42.7.12 to resolve high-severity CVE (SNYK-JAVA-ORGPOSTGRESQL-17874248) ([ae5a826](https://github.com/tiagofvalerio/taskflow-sdd/commit/ae5a826cfb9dae8be482b2f951cecc73dd2fee6a))
