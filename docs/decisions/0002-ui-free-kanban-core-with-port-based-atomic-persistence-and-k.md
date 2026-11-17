# UI-Free Kanban Core with Port-Based Atomic Persistence and Keyboard Drag State Machine

Status: accepted (generated, pending human review)

## Context

Desktop Kanban applications frequently couple drag-and-drop logic to mouse pointer events and write state directly to target files, excluding keyboard and screen reader users and risking data corruption on crash.

## Decision

Implement an immutable Board/Column/Card model paired with a keyboard-driven DragController that emits 1-based screen reader announcements, a bounded 100-entry undo/redo History over sealed Commands, and a versioned tab-separated line format (`kanban-board v1`) persisted through a FileSystemPort using writeTemp + atomicMove.

## Consequences

Every card movement is operable and announced via keyboard keys alone, undo/redo is deterministic and bounded in memory, and file saves are crash-safe and testable with an in-memory fake file system without requiring a GUI runtime in CI.
