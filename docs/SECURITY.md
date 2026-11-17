# Security notes

## Threat model

`io.github.trysac:compose-desktop-kanban` is a pure library without I/O. 

- **Defended**: 
    - Supply chain attacks (pinned dependencies, locked lockfile, OSV-Scanner).
    - Secrets (gitleaks).
    - Input validation (explicit `BoardException` on all malformed data).
    - Insecure code (detekt, compiler warnings as errors, strict Semgrep rules).
- **Not defended**: 
    - Callers providing hostile/malicious file paths to `BoardStore` that exceed OS-level constraints.
    - Memory exhaustion if a user provides an excessively large file that passes the 5 MiB `MAX_FILE_BYTES` limit but consumes memory elsewhere in the application.
