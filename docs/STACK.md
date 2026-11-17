# Stack

## Used

| Tool | Why |
|---|---|
| Kotlin 2 (JVM 21) | The Kotlin Gradle plugin pinned in `build.gradle.kts`; JVM bytecode 21 (Java 21 LTS). |
| Gradle (Kotlin DSL) | The standard Kotlin build; the wrapper pins the distribution by SHA-256, `gradle.lockfile` locks every configuration. |
| ktlint (Spotless) | The `ktlint_official` layout, applied and checked by Spotless. |
| Kotlin compiler + detekt | Compiler warnings as errors; detekt's default rules plus KDoc on the public API, any finding fails. |
| JUnit 5 + jqwik | Example-based and property-based tests on the JUnit Platform. |
| Kover | Line coverage of the library; the gate reads its XML report. |
| Semgrep CE + OSV-Scanner | The factory's own rules; OSV advisories over the locked library dependencies and the whole lockfile. |
| gitleaks | Secret scanning locally and in CI. |
| GitHub Actions + Dependabot | Thin CI wrapper over the local gates; weekly dependency updates. |

## Discarded

| Tool | Why not |
|---|---|
| Maven | Gradle is the build the Kotlin tooling (compiler plugin, Kover, detekt) is made for. |
| Kotest | jqwik and JUnit 5 cover properties and examples with the conformance tag shared with Java. |
| Dokka | KDoc presence is enforced by detekt; a rendered site is not needed for a small library. |
| Explicit API mode | Visibility and return types everywhere add friction; KDoc on public declarations is required instead. |
| Shell scripts for gates | Not portable to Windows; the gates are a Java program in `scripts/Check.java`. |
