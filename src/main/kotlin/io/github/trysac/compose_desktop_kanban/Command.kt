package io.github.trysac.compose_desktop_kanban

/**
 * Reversible board mutation command executed through [History.apply].
 */
public sealed interface Command {
    /**
     * Applies this command to [board] and returns the updated [Board].
     *
     * @throws BoardException if the command violates board invariants or limits.
     */
    public fun execute(board: Board): Board

    /**
     * Moves a card identified by [cardId] to [toColumnId] at 0-based [toIndex].
     *
     * @property cardId Identifier of the card to move.
     * @property toColumnId Target column identifier.
     * @property toIndex 0-based index in the target column.
     */
    public data class MoveCard(
        public val cardId: String,
        public val toColumnId: String,
        public val toIndex: Int,
    ) : Command {
        override fun execute(board: Board): Board {
            val source =
                board.findCard(cardId)
                    ?: throw BoardException(code = "not_found", message = "Card not found: $cardId")
            val (fromColIdx, fromCardIdx) = source
            val toColIdx = board.columns.indexOfFirst { it.id == toColumnId }
            if (toColIdx < 0) {
                throw BoardException(code = "not_found", message = "Column not found: $toColumnId")
            }
            val card = board.columns[fromColIdx].cards[fromCardIdx]
            if (fromColIdx == toColIdx) {
                val col = board.columns[fromColIdx]
                val mutableCards = col.cards.toMutableList()
                mutableCards.removeAt(fromCardIdx)
                val targetIdx = toIndex.coerceIn(0, mutableCards.size)
                mutableCards.add(targetIdx, card)
                val updatedColumns = board.columns.toMutableList()
                updatedColumns[fromColIdx] = col.copy(cards = mutableCards)
                return Board(updatedColumns)
            } else {
                val fromCol = board.columns[fromColIdx]
                val toCol = board.columns[toColIdx]
                if (toCol.wipLimit != null && toCol.cards.size >= toCol.wipLimit) {
                    throw BoardException(
                        code = "wip_limit_exceeded",
                        message = "${toCol.title} is at its limit of ${toCol.wipLimit}",
                    )
                }
                if (toCol.cards.size >= MAX_CARDS_PER_COLUMN) {
                    throw BoardException(
                        code = "column_too_large",
                        message = "Column cannot hold more than $MAX_CARDS_PER_COLUMN cards",
                    )
                }
                val newFromCards = fromCol.cards.toMutableList().apply { removeAt(fromCardIdx) }
                val newToCards = toCol.cards.toMutableList()
                val targetIdx = toIndex.coerceIn(0, newToCards.size)
                newToCards.add(targetIdx, card)
                val updatedColumns = board.columns.toMutableList()
                updatedColumns[fromColIdx] = fromCol.copy(cards = newFromCards)
                updatedColumns[toColIdx] = toCol.copy(cards = newToCards)
                return Board(updatedColumns)
            }
        }
    }

    /**
     * Appends a new card to [columnId].
     *
     * @property columnId Target column identifier.
     * @property card Card to add.
     */
    public data class AddCard(
        public val columnId: String,
        public val card: Card,
    ) : Command {
        /**
         * Secondary constructor creating a [Card] from [cardId] and [title].
         */
        public constructor(columnId: String, cardId: String, title: String) : this(
            columnId = columnId,
            card = Card(id = cardId, title = title),
        )

        override fun execute(board: Board): Board {
            val colIdx = board.columns.indexOfFirst { it.id == columnId }
            if (colIdx < 0) {
                throw BoardException(code = "not_found", message = "Column not found: $columnId")
            }
            val col = board.columns[colIdx]
            if (col.wipLimit != null && col.cards.size >= col.wipLimit) {
                throw BoardException(
                    code = "wip_limit_exceeded",
                    message = "${col.title} is at its limit of ${col.wipLimit}",
                )
            }
            val updatedColumns = board.columns.toMutableList()
            updatedColumns[colIdx] = col.copy(cards = col.cards + card)
            return Board(updatedColumns)
        }
    }

    /**
     * Renames an existing card identified by [cardId] to [newTitle].
     *
     * @property cardId Identifier of the card to rename.
     * @property newTitle Replacement title.
     */
    public data class RenameCard(
        public val cardId: String,
        public val newTitle: String,
    ) : Command {
        override fun execute(board: Board): Board {
            validateTitle(newTitle)
            val loc =
                board.findCard(cardId)
                    ?: throw BoardException(code = "not_found", message = "Card not found: $cardId")
            val (colIdx, cardIdx) = loc
            val col = board.columns[colIdx]
            val updatedCards = col.cards.toMutableList()
            updatedCards[cardIdx] = updatedCards[cardIdx].copy(title = newTitle)
            val updatedColumns = board.columns.toMutableList()
            updatedColumns[colIdx] = col.copy(cards = updatedCards)
            return Board(updatedColumns)
        }
    }

    /**
     * Deletes a card identified by [cardId] from the board.
     *
     * @property cardId Identifier of the card to delete.
     */
    public data class DeleteCard(
        public val cardId: String,
    ) : Command {
        override fun execute(board: Board): Board {
            val loc =
                board.findCard(cardId)
                    ?: throw BoardException(code = "not_found", message = "Card not found: $cardId")
            val (colIdx, cardIdx) = loc
            val col = board.columns[colIdx]
            val updatedCards = col.cards.toMutableList().apply { removeAt(cardIdx) }
            val updatedColumns = board.columns.toMutableList()
            updatedColumns[colIdx] = col.copy(cards = updatedCards)
            return Board(updatedColumns)
        }
    }

    /**
     * Appends a new [Column] to the board.
     *
     * @property column Column to append.
     */
    public data class AddColumn(
        public val column: Column,
    ) : Command {
        /**
         * Secondary constructor creating a [Column] from [id], [title], and optional [wipLimit].
         */
        public constructor(id: String, title: String, wipLimit: Int? = null) : this(
            column = Column(id = id, title = title, wipLimit = wipLimit),
        )

        override fun execute(board: Board): Board = Board(board.columns + column)
    }
}
