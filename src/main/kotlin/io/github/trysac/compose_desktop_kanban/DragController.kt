package io.github.trysac.compose_desktop_kanban

/**
 * Keyboard keys handled by [DragController] for keyboard-only card movement.
 */
public enum class Key {
    /** Picks up the focused card or drops the currently dragged card. */
    SPACE,

    /** Picks up the focused card or drops the currently dragged card. */
    ENTER,

    /** Cancels an active drag and restores the board to its state at pick-up. */
    ESCAPE,

    /** Moves the dragged card one slot up within its current column. */
    UP,

    /** Moves the dragged card one slot down within its current column. */
    DOWN,

    /** Moves the dragged card to the previous column on the left. */
    LEFT,

    /** Moves the dragged card to the next column on the right. */
    RIGHT,
}

/**
 * Outcome of handling a single [Key] in [DragController].
 *
 * @property board The board state after handling the key.
 * @property dragging The identifier of the card currently being dragged, or `null` if not dragging.
 * @property announcement Screen reader announcement describing the action.
 */
public data class DragResult(
    public val board: Board,
    public val dragging: String?,
    public val announcement: String,
)

/**
 * Controller for keyboard-only Kanban drag and drop with screen reader announcements.
 *
 * When linked with a [History] instance (or using its built-in [history]), dropping a card
 * records a single [Command.MoveCard] entry in the history.
 *
 * @param board Initial board state.
 * @property history Optional [History] instance updated whenever a card is dropped.
 */
public class DragController(
    board: Board,
    public val history: History = History(board),
) {
    private var currentBoard: Board = board
    private var focusedCardId: String? = null
    private var draggingCardId: String? = null
    private var pickupBoard: Board? = null
    private var pickupColIndex: Int = -1
    private var pickupCardIndex: Int = -1
    private val announcementLog: ArrayDeque<String> = ArrayDeque()

    /**
     * Current [Board] state managed by this controller.
     */
    public val board: Board
        get() = currentBoard

    /**
     * Identifier of the currently focused card, or `null` if none.
     */
    public val focused: String?
        get() = focusedCardId

    /**
     * Identifier of the card currently being dragged, or `null` when idle.
     */
    public val dragging: String?
        get() = draggingCardId

    /**
     * Ordered list of screen reader announcements produced so far (bounded to the latest
     * [MAX_ANNOUNCEMENTS] messages).
     */
    public val announcements: List<String>
        get() = announcementLog.toList()

    /**
     * Sets keyboard focus on the card with [cardId].
     *
     * @throws BoardException with code `not_found` if [cardId] does not exist on the board,
     * or `already_dragging` if a drag is currently in progress.
     */
    public fun focus(cardId: String) {
        if (draggingCardId != null) {
            throw BoardException(
                code = "already_dragging",
                message = "Cannot change focus while dragging $draggingCardId",
            )
        }
        if (currentBoard.findCard(cardId) == null) {
            throw BoardException(
                code = "not_found",
                message = "Card not found: $cardId",
            )
        }
        focusedCardId = cardId
    }

    /**
     * Processes a keyboard [key] press and returns the resulting [DragResult].
     */
    public fun handle(key: Key): DragResult {
        val activeDrag = draggingCardId
        return if (activeDrag == null) {
            handleIdleKey(key)
        } else {
            handleDraggingKey(activeDrag, key)
        }
    }

    private fun handleIdleKey(key: Key): DragResult {
        if (key == Key.SPACE || key == Key.ENTER) {
            val targetId =
                focusedCardId
                    ?: throw BoardException(code = "no_focus", message = "No card is focused")
            val loc =
                currentBoard.findCard(targetId)
                    ?: throw BoardException(code = "not_found", message = "Card not found: $targetId")
            val (colIdx, cardIdx) = loc
            val col = currentBoard.columns[colIdx]
            draggingCardId = targetId
            pickupBoard = currentBoard
            pickupColIndex = colIdx
            pickupCardIndex = cardIdx
            val msg = "Picked up card $targetId in ${col.title}, position ${cardIdx + 1} of ${col.cards.size}"
            return recordResult(msg)
        }
        return recordResult("")
    }

    private fun handleDraggingKey(
        cardId: String,
        key: Key,
    ): DragResult {
        val loc =
            currentBoard.findCard(cardId)
                ?: throw BoardException(code = "not_found", message = "Card not found: $cardId")
        val (colIdx, cardIdx) = loc
        return when (key) {
            Key.SPACE, Key.ENTER -> dropCard(cardId, colIdx, cardIdx)
            Key.ESCAPE -> cancelDrag(cardId)
            Key.UP -> moveVertical(cardId, colIdx, cardIdx, delta = -1)
            Key.DOWN -> moveVertical(cardId, colIdx, cardIdx, delta = 1)
            Key.LEFT -> moveHorizontal(cardId, colIdx, cardIdx, delta = -1)
            Key.RIGHT -> moveHorizontal(cardId, colIdx, cardIdx, delta = 1)
        }
    }

    private fun dropCard(
        cardId: String,
        colIdx: Int,
        cardIdx: Int,
    ): DragResult {
        val col = currentBoard.columns[colIdx]
        val originBoard = pickupBoard ?: currentBoard
        val finalBoard =
            Command
                .MoveCard(
                    cardId = cardId,
                    toColumnId = col.id,
                    toIndex = cardIdx,
                ).execute(originBoard)
        history.recordTransition(originBoard, finalBoard)
        currentBoard = finalBoard
        draggingCardId = null
        pickupBoard = null
        focusedCardId = cardId
        val droppedCol = currentBoard.columns[colIdx]
        val msg = "Dropped card $cardId in ${droppedCol.title}, position ${cardIdx + 1} of ${droppedCol.cards.size}"
        return recordResult(msg)
    }

    private fun cancelDrag(cardId: String): DragResult {
        val restored = pickupBoard ?: currentBoard
        currentBoard = restored
        val col = restored.columns[pickupColIndex]
        val pos = pickupCardIndex + 1
        val total = col.cards.size
        draggingCardId = null
        pickupBoard = null
        focusedCardId = cardId
        val msg = "Cancelled, card $cardId returned to ${col.title}, position $pos of $total"
        return recordResult(msg)
    }

    private fun moveVertical(
        cardId: String,
        colIdx: Int,
        cardIdx: Int,
        delta: Int,
    ): DragResult {
        val col = currentBoard.columns[colIdx]
        val newCardIdx = (cardIdx + delta).coerceIn(0, col.cards.size - 1)
        if (newCardIdx != cardIdx) {
            currentBoard =
                Command
                    .MoveCard(
                        cardId = cardId,
                        toColumnId = col.id,
                        toIndex = newCardIdx,
                    ).execute(currentBoard)
        }
        val updatedCol = currentBoard.columns[colIdx]
        val msg = "Card $cardId, position ${newCardIdx + 1} of ${updatedCol.cards.size} in ${updatedCol.title}"
        return recordResult(msg)
    }

    private fun moveHorizontal(
        cardId: String,
        colIdx: Int,
        cardIdx: Int,
        delta: Int,
    ): DragResult {
        val targetColIdx = (colIdx + delta).coerceIn(0, currentBoard.columns.size - 1)
        val msg =
            if (targetColIdx == colIdx) {
                val col = currentBoard.columns[colIdx]
                "Card $cardId, position ${cardIdx + 1} of ${col.cards.size} in ${col.title}"
            } else {
                val targetCol = currentBoard.columns[targetColIdx]
                if (targetCol.wipLimit != null && targetCol.cards.size >= targetCol.wipLimit) {
                    "${targetCol.title} is at its limit of ${targetCol.wipLimit}"
                } else {
                    val targetCardIdx = cardIdx.coerceIn(0, targetCol.cards.size)
                    currentBoard =
                        Command
                            .MoveCard(
                                cardId = cardId,
                                toColumnId = targetCol.id,
                                toIndex = targetCardIdx,
                            ).execute(currentBoard)
                    val updatedTargetCol = currentBoard.columns[targetColIdx]
                    val pos = targetCardIdx + 1
                    val total = updatedTargetCol.cards.size
                    val title = updatedTargetCol.title
                    "Card $cardId, position $pos of $total in $title"
                }
            }
        return recordResult(msg)
    }

    private fun recordResult(announcement: String): DragResult {
        if (announcement.isNotEmpty()) {
            if (announcementLog.size == MAX_ANNOUNCEMENTS) {
                announcementLog.removeFirst()
            }
            announcementLog.addLast(announcement)
        }
        return DragResult(
            board = currentBoard,
            dragging = draggingCardId,
            announcement = announcement,
        )
    }

    /** Constants for [DragController]. */
    public companion object {
        /** Maximum number of announcements retained in memory. */
        public const val MAX_ANNOUNCEMENTS: Int = 1_000
    }
}
