# compose-desktop-kanban

Kanban board core with keyboard-only drag and drop and screen reader announcements, WIP limits, undo and redo, and crash-safe persistence in a versioned line format.

## The Problem

Desktop kanban boards usually support moving cards only with a mouse, which shuts out keyboard and screen reader users, and they lose data when the app crashes while saving. Compose Multiplatform gives the widgets but not the move model. This library is the UI-free core: a board model, a keyboard drag controller with spoken announcements, undo and redo, and atomic local persistence, so a Compose Desktop window only renders state and forwards keys.

## Installation

Add the dependency to your `build.gradle.kts` (replace `x.y.z` with the latest version):

```kotlin
implementation("io.github.trysac:compose-desktop-kanban:x.y.z")
```

## Usage

```kotlin
import io.github.trysac.compose_desktop_kanban.*

val board = Board(listOf(
    Column("todo", "To do", cards = listOf(Card("c1", "Spec"), Card("c2", "Code"))),
    Column("doing", "Doing", wipLimit = 2),
))
val drag = DragController(board).apply { focus("c2") }
drag.handle(Key.SPACE)
drag.handle(Key.RIGHT)
val res = drag.handle(Key.SPACE) // Dropped card c2 in Doing, position 1 of 1
```

## Public API Summary

- **Models**: `Card(id, title)`, `Column(id, title, wipLimit, cards)`, `Board(columns)`.
- **Commands**: `Command` interface with implementations `MoveCard`, `AddCard`, `RenameCard`, `DeleteCard`, `AddColumn`.
- **History**: `History(initial, capacity)` for undo/redo via `apply(command)`.
- **DragController**: `DragController(board)` with `focus(cardId)`, `handle(key)` returning `DragResult`.
- **Persistence**: `BoardStore(path, fs)` with `load()` and `save(board)`.
- **Errors**: `BoardException(code, line)` thrown on validation failures, malformed files, or limit violations.
- **Limits**: 5 MiB file size, 500 characters for titles, 1,000 cards per column.

## Why not <best-known alternative>?

Every move is possible and announced from the keyboard, and saves are atomic.

See [docs/SECURITY.md](docs/SECURITY.md) for vulnerability reporting.
