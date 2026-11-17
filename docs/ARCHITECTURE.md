# Architecture

`compose-desktop-kanban` provides the UI-free domain, interaction, and persistence core for an accessible desktop Kanban application.

## Modules and Responsibilities

1. **Domain Model (`Models.kt`, `BoardException.kt`)**
   - `Card`, `Column`, and `Board` are immutable value types validated upon construction.
   - All IDs across columns and cards are unique across a `Board` (`duplicate_id`).
   - Input size limits are enforced at construction and parse time: 500-character titles (`title_too_long`), 1,000 cards per column (`column_too_large`), and 5,242,880 bytes per serialized file (`file_too_large`).

2. **Reversible Commands & History (`Command.kt`, `History.kt`)**
   - `Command` is a sealed interface (`MoveCard`, `AddCard`, `RenameCard`, `DeleteCard`, `AddColumn`).
   - `History` holds an undo deque bounded to `capacity` (default 100) and a redo deque cleared on every new transition.

3. **Keyboard Drag Controller (`DragController.kt`)**
   - State machine tracking `focused`, `dragging`, and the pick-up snapshot.
   - Moves cards vertically (`UP`/`DOWN`) and horizontally (`LEFT`/`RIGHT`), clamping indices and refusing moves into columns at their `wipLimit`.
   - Produces deterministic 1-based screen reader announcements for every pick-up, move, WIP refusal, cancellation (`ESCAPE`), and drop (`SPACE`/`ENTER`).
   - Dropping a card commits a single transition to `History` from the pick-up board to the final board.

4. **Crash-Safe Persistence (`BoardStore.kt`, `FileSystemPort.kt`)**
   - `BoardStore` serializes and parses the line-oriented `kanban-board v1` format.
   - `FileSystemPort` decouples disk I/O (`NioFileSystem` using `StandardOpenOption.SYNC` and `StandardCopyOption.ATOMIC_MOVE`) from deterministic in-memory tests (`FakeFileSystem`).
