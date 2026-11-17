package io.github.trysac.compose_desktop_kanban

/** Maximum allowed character length for card and column titles. */
public const val MAX_TITLE_LENGTH: Int = 500

/** Maximum allowed character length for card and column identifiers. */
public const val MAX_ID_LENGTH: Int = 200

/** Maximum number of cards allowed in a single column. */
public const val MAX_CARDS_PER_COLUMN: Int = 1_000

/** Maximum serialized board file size in bytes (5 MiB). */
public const val MAX_FILE_BYTES: Int = 5_242_880

internal fun validateId(id: String) {
    if (id.isBlank()) {
        throw BoardException(code = "invalid_id", message = "Identifier must not be blank")
    }
    if (id.length > MAX_ID_LENGTH) {
        throw BoardException(code = "invalid_id", message = "Identifier exceeds maximum length")
    }
}

internal fun validateTitle(title: String) {
    if (title.isBlank()) {
        throw BoardException(code = "invalid_title", message = "Title must not be blank")
    }
    if (title.length > MAX_TITLE_LENGTH) {
        throw BoardException(
            code = "title_too_long",
            message = "Title length ${title.length} exceeds limit of $MAX_TITLE_LENGTH",
        )
    }
}

/**
 * An immutable task card on a Kanban board.
 *
 * @property id Unique identifier of the card across the board.
 * @property title Human-readable title (at most [MAX_TITLE_LENGTH] characters).
 */
public data class Card(
    public val id: String,
    public val title: String,
) {
    init {
        validateId(id)
        validateTitle(title)
    }
}

/**
 * An immutable column holding an ordered list of [Card] instances and an optional WIP limit.
 *
 * @property id Unique identifier of the column across the board.
 * @property title Human-readable column heading (at most [MAX_TITLE_LENGTH] characters).
 * @property wipLimit Optional work-in-progress limit; when non-null, must be at least 1.
 * @property cards Ordered list of cards currently in this column.
 */
public data class Column(
    public val id: String,
    public val title: String,
    public val wipLimit: Int? = null,
    public val cards: List<Card> = emptyList(),
) {
    init {
        validateId(id)
        validateTitle(title)
        if (wipLimit != null && wipLimit < 1) {
            throw BoardException(
                code = "invalid_wip_limit",
                message = "WIP limit must be at least 1",
            )
        }
        if (cards.size > MAX_CARDS_PER_COLUMN) {
            throw BoardException(
                code = "column_too_large",
                message = "Column cannot hold more than $MAX_CARDS_PER_COLUMN cards",
            )
        }
        if (wipLimit != null && cards.size > wipLimit) {
            throw BoardException(
                code = "wip_limit_exceeded",
                message = "$title is at its limit of $wipLimit",
            )
        }
    }
}

/**
 * An immutable Kanban board composed of ordered [Column] instances.
 *
 * All column and card identifiers must be unique across the entire board.
 *
 * @property columns Ordered list of columns on the board.
 */
public data class Board(
    public val columns: List<Column> = emptyList(),
) {
    init {
        val seenColumnIds = HashSet<String>()
        val seenCardIds = HashSet<String>()
        for (col in columns) {
            if (!seenColumnIds.add(col.id) || seenCardIds.contains(col.id)) {
                throw BoardException(
                    code = "duplicate_id",
                    message = "Duplicate identifier: ${col.id}",
                )
            }
            for (card in col.cards) {
                if (!seenCardIds.add(card.id) || seenColumnIds.contains(card.id)) {
                    throw BoardException(
                        code = "duplicate_id",
                        message = "Duplicate identifier: ${card.id}",
                    )
                }
            }
        }
    }

    /**
     * Finds a column by its [columnId], or returns `null` if no such column exists.
     */
    public fun column(columnId: String): Column? = columns.firstOrNull { it.id == columnId }

    /**
     * Finds the location of [cardId] as `(columnIndex, cardIndex)` or `null` if absent.
     */
    internal fun findCard(cardId: String): Pair<Int, Int>? {
        for (colIdx in columns.indices) {
            val cards = columns[colIdx].cards
            for (cardIdx in cards.indices) {
                if (cards[cardIdx].id == cardId) {
                    return colIdx to cardIdx
                }
            }
        }
        return null
    }
}
