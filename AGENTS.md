# AGENTS.md

Instructions for coding agents (and humans) working on the Kotlin library
`io.github.trysac:compose-desktop-kanban` (package `io.github.trysac.compose_desktop_kanban`).

## Commands

`java scripts/Gradle.java <tasks>` runs the Gradle wrapper without a shell (the same as
`./gradlew <tasks>`), on Windows and Linux.

| Task | Command |
|---|---|
| Fetch dependencies | `java scripts/Gradle.java dependencies` |
| Run every gate (must pass before a PR) | `java scripts/Check.java` |
| Tests only | `java scripts/Gradle.java test` |
| Format | `java scripts/Gradle.java spotlessApply` |
| Lint | `java scripts/Gradle.java testClasses detekt` (compiler warnings are errors; detekt) |
| Coverage | `java scripts/Gradle.java test koverHtmlReport`, then `build/reports/kover/html/index.html` |

The gate sequence is: ktlint (`spotlessCheck`), the Kotlin compiler with warnings as errors plus
detekt (KDoc required on the public API), Semgrep (the factory's rules in `.semgrep/`), JUnit 5 and
jqwik tests under Kover with line coverage >= 80 % of the library, documentation manifest
(`docs-manifest.json`), gitleaks, OSV-Scanner over the library's locked dependencies, and
OSV-Scanner over the whole `gradle.lockfile`. It stops at the first failure.

## Folder map

| Path | Content |
|---|---|
| `src/main/kotlin/io/github/trysac/compose_desktop_kanban/` | The library; its public declarations are the public API. |
| `src/test/kotlin/io/github/trysac/compose_desktop_kanban/` | JUnit 5 tests (`*Test.kt`) and jqwik property tests. |
| `src/test/resources/fixtures/` | Optional fixtures and golden files used by tests. |
| `scripts/` | The single definition of the quality gates (`Check.java`), the Gradle launcher and the factory's dependency tool. |
| `docs/` | Architecture, decisions (ADRs), testing, security, project record, stack. |
| `gradle/wrapper/` | The Gradle wrapper, pinned by its distribution SHA-256. |
| `fabrica.json` | Install and check commands used by the factory and CI. |
| `docs-manifest.json` | Documentation files the docs gate requires. |
| `.github/` | CI wrapper and Dependabot configuration. |

## Conventions

- Kotlin 2 on the JVM 21, formatted by ktlint (`ktlint_official`), clean under the compiler with
  warnings as errors and detekt.
- Standard library only unless an ADR in `docs/decisions/` justifies a dependency.
- Every public declaration has a KDoc comment; invalid input throws a documented
  `IllegalArgumentException` subclass, never returns null or a sentinel value.
- Write the failing test first; tests use every literal of the acceptance items verbatim, and
  jqwik properties (`@Property`) cover invariants.
- CI-only conformance tests (external tools or downloads) carry `@Tag("conformance")`; only CI runs
  them.
- Every behavior change updates `README.md` usage, `CHANGELOG.md` (Unreleased) and, if it is a
  design choice, a new ADR.
- Conventional Commits (`feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `chore:`).

## Limits

- Never push to `main`; work on a branch and open a pull request.
- Do NOT edit `.github/`, `gradle/`, `fabrica.json`, `docs-manifest.json`, `scripts/`,
  `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle.lockfile`, `detekt.yml`,
  `.editorconfig` or any tool configuration. They are the quality contract; changes to them are
  made by a human.
- Never lower the coverage threshold, add `@Suppress` to silence a lint, or disable a test to make
  a gate pass.
- Never commit secrets, tokens or `.env` files; gitleaks blocks them.
