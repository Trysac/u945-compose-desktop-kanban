package io.github.trysac.compose_desktop_kanban

/**
 * Bounded undo/redo history stack over immutable [Board] states.
 *
 * Keeps at most [capacity] applied commands (default 100); applying a command when full
 * evicts the oldest entry and clears the redo stack.
 *
 * @param initial Initial board state.
 * @property capacity Maximum number of undo steps retained.
 */
public class History(
    initial: Board,
    public val capacity: Int = DEFAULT_CAPACITY,
) {
    private val undoStack: ArrayDeque<Board> = ArrayDeque()
    private val redoStack: ArrayDeque<Board> = ArrayDeque()

    /**
     * Current [Board] state.
     */
    public var board: Board = initial
        private set

    init {
        if (capacity < 1) {
            throw BoardException(
                code = "invalid_capacity",
                message = "History capacity must be at least 1",
            )
        }
    }

    /**
     * Applies [command] to the current [board], records the previous state for [undo],
     * clears any pending [redo] states, and returns the new [Board].
     */
    public fun apply(command: Command): Board {
        val next = command.execute(board)
        recordTransition(board, next)
        return board
    }

    internal fun recordTransition(
        previous: Board,
        next: Board,
    ) {
        if (undoStack.size == capacity) {
            undoStack.removeFirst()
        }
        undoStack.addLast(previous)
        redoStack.clear()
        board = next
    }

    /**
     * Returns `true` if there is at least one state available to undo.
     */
    public fun canUndo(): Boolean = undoStack.isNotEmpty()

    /**
     * Returns `true` if there is at least one state available to redo.
     */
    public fun canRedo(): Boolean = redoStack.isNotEmpty()

    /**
     * Reverts to the state before the most recent command and returns that [Board].
     *
     * If [canUndo] is `false`, throws [BoardException] with code `nothing_to_undo`.
     */
    public fun undo(): Board {
        if (undoStack.isEmpty()) {
            throw BoardException(code = "nothing_to_undo", message = "Nothing to undo")
        }
        redoStack.addLast(board)
        board = undoStack.removeLast()
        return board
    }

    /**
     * Reapplies the most recently undone state and returns that [Board].
     *
     * If [canRedo] is `false`, throws [BoardException] with code `nothing_to_redo`.
     */
    public fun redo(): Board {
        if (redoStack.isEmpty()) {
            throw BoardException(code = "nothing_to_redo", message = "Nothing to redo")
        }
        if (undoStack.size == capacity) {
            undoStack.removeFirst()
        }
        undoStack.addLast(board)
        board = redoStack.removeLast()
        return board
    }

    /** Constants for [History]. */
    public companion object {
        /** Default history retention capacity (100 entries). */
        public const val DEFAULT_CAPACITY: Int = 100
    }
}
