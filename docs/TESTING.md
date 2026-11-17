# Testing

## Existing Tests

- `KanbanAcceptanceTest.kt`: Covers acceptance criteria including drag and drop behavior (and announcements), ESCAPE cancellation, WIP limit enforcement, undo/redo history capacity, and `BoardStore` persistence (including atomic move failure simulation). Also covers error codes (`unsupported_version`, `malformed`, `file_too_large`, `duplicate_id`, `title_too_long`) and edge cases like file formats and empty columns.
- `KanbanPropertiesTest.kt`: Property-based testing using `jqwik` to verify that `BoardStore` round-trips arbitrary boards and that `ESCAPE` during drag operations always restores the board state correctly.

## Policy

- Line coverage of the library must stay at or above 80 %.
- Tests are deterministic, excluding network/wall clock dependencies.
- Each bug fix starts with a failing test.
