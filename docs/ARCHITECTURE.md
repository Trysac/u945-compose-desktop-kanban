# Architecture

## Purpose

`io.github.trysac:compose-desktop-kanban` is a small, dependency-free Kotlin library: Kanban board core with keyboard-only drag and drop and screen reader announcements, WIP limits, undo and redo, and crash-safe persistence in a versioned line format

## Shape

```text
caller code --> package io.github.trysac.compose_desktop_kanban (src/main/kotlin)
                    |
                    +-- objects and top-level functions that are pure; no I/O, no global mutable state
                    +-- internal or private helpers (optional)
```

- **Public API**: every public declaration of the package. `internal` and `private` declarations
  may change without a major version.
- **Errors**: invalid input throws an `IllegalArgumentException` subclass of the library (such as
  `BlankInputException`) documented with `@throws`. The library never prints or exits.
- **Side effects**: none. If a feature needs I/O, accept an `InputStream`, a `Reader` or a small
  interface instead of opening resources, and record the choice in an ADR.

## Quality contract

`scripts/Check.java` is the single definition of the gates; `fabrica.json` tells the factory and CI
how to run it. See [TESTING.md](TESTING.md).

## Decisions

Architecture decisions are recorded in [decisions/](decisions/) as numbered ADRs.
