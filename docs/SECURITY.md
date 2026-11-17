# Security notes

## Threat model

`io.github.trysac:compose-desktop-kanban` is a pure library without I/O. The main risks are:

- **Supply chain**: a compromised dependency, Gradle plugin or CI action. Mitigation: standard
  library only, every plugin and dependency pinned to an exact version in `build.gradle.kts`,
  every resolved configuration locked in `gradle.lockfile`, the Gradle distribution pinned by its
  SHA-256, OSV-Scanner over the locked library dependencies and over the whole lockfile in the
  gates, Dependabot weekly updates, actions pinned by commit SHA, minimal workflow permissions.
- **Leaked secrets**: tokens committed by mistake. Mitigation: `gitleaks` runs in the local gates
  and in CI; `.env` files are git-ignored.
- **Insecure code**: command injection, unsafe deserialization, disabled TLS verification.
  Mitigation: detekt and the Kotlin compiler with warnings as errors, and the factory's Semgrep
  rules (`.semgrep/factory-rules.yml`, `nosemgrep` ignored); fix the code instead of silencing it.
- **Untrusted input**: callers may pass hostile strings. Mitigation: validate input and throw
  explicit, documented exceptions; never deserialize untrusted bytes with `ObjectInputStream`;
  never start a shell with untrusted data.

## Publishing

Releases are git tags; publishing to Maven Central needs a namespace account and a signing key,
which are never stored in the repository.

To report a vulnerability, see [SECURITY.md](../SECURITY.md).
