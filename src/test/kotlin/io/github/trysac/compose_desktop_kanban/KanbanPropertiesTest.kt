package io.github.trysac.compose_desktop_kanban

import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.Combinators
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * Property-based tests for `compose-desktop-kanban` invariants.
 */
class KanbanPropertiesTest {
    @Provide
    fun validTitles(): Arbitrary<String> =
        Arbitraries
            .strings()
            .withChars('a', 'z', 'A', 'Z', '0', '9', ' ', '\t', '\n', '\r', '%', '-', '_')
            .ofMinLength(1)
            .ofMaxLength(80)
            .filter { it.isNotBlank() }

    @Provide
    fun boards(): Arbitrary<Board> {
        val titleArb = validTitles()
        return titleArb.list().ofMinSize(1).ofMaxSize(5).flatMap { colTitles ->
            val cardListsArb =
                titleArb
                    .list()
                    .ofMinSize(0)
                    .ofMaxSize(6)
                    .list()
                    .ofSize(colTitles.size)
            Combinators.combine(Arbitraries.just(colTitles), cardListsArb).`as` { cols, cardGroups ->
                val columns =
                    cols.mapIndexed { colIdx, colTitle ->
                        val cards =
                            cardGroups[colIdx].mapIndexed { cardIdx, cardTitle ->
                                Card(id = "c_${colIdx}_$cardIdx", title = cardTitle)
                            }
                        val wip = if (cards.isEmpty()) null else cards.size + 2
                        Column(
                            id = "col_$colIdx",
                            title = colTitle,
                            wipLimit = wip,
                            cards = cards,
                        )
                    }
                Board(columns)
            }
        }
    }

    @Property(tries = 100)
    fun `BoardStore format and parse round-trips arbitrary boards`(
        @ForAll("boards") board: Board,
    ) {
        val encoded = BoardStore.format(board)
        val decoded = BoardStore.parse(encoded)
        assertEquals(board, decoded)
    }

    @Provide
    fun keys(): Arbitrary<List<Key>> = Arbitraries.of(Key::class.java).list().ofMaxSize(20)

    @Property(tries = 100)
    fun `ESCAPE after arbitrary drag movements always restores initial board`(
        @ForAll("boards") board: Board,
        @ForAll("keys") keys: List<Key>,
    ) {
        val firstCard = board.columns.flatMap { it.cards }.firstOrNull() ?: return
        val controller = DragController(board)
        controller.focus(firstCard.id)
        controller.handle(Key.SPACE)
        for (key in keys) {
            if (key != Key.SPACE && key != Key.ENTER && key != Key.ESCAPE) {
                controller.handle(key)
            }
        }
        val result = controller.handle(Key.ESCAPE)
        assertEquals(board, result.board)
    }
}
