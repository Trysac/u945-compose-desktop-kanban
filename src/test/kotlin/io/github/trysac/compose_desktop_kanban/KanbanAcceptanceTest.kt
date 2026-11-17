package io.github.trysac.compose_desktop_kanban

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Tests covering all acceptance criteria, edge cases, commands, and persistence invariants.
 */
class KanbanAcceptanceTest {
    private fun sampleBoard(doingWip: Int? = null): Board =
        Board(
            columns =
                listOf(
                    Column(
                        id = "todo",
                        title = "To do",
                        cards =
                            listOf(
                                Card("c1", "Task 1"),
                                Card("c2", "Task 2"),
                                Card("c3", "Task 3"),
                            ),
                    ),
                    Column(
                        id = "doing",
                        title = "Doing",
                        wipLimit = doingWip,
                        cards =
                            listOf(
                                Card("d1", "Doing 1"),
                            ),
                    ),
                ),
        )

    @Test
    fun `keyboard drag and drop moves c2 down then right and announces drop`() {
        val initial = sampleBoard()
        val controller = DragController(initial)
        controller.focus("c2")

        val pickUp = controller.handle(Key.SPACE)
        assertEquals("c2", pickUp.dragging)
        assertEquals("Picked up card c2 in To do, position 2 of 3", pickUp.announcement)

        val movedDown = controller.handle(Key.DOWN)
        assertEquals("Card c2, position 3 of 3 in To do", movedDown.announcement)

        val movedRight = controller.handle(Key.RIGHT)
        assertEquals("Card c2, position 2 of 2 in Doing", movedRight.announcement)

        val dropped = controller.handle(Key.SPACE)
        assertNull(dropped.dragging)
        assertEquals("Dropped card c2 in Doing, position 2 of 2", dropped.announcement)
        assertEquals("Dropped card c2 in Doing, position 2 of 2", controller.announcements.last())

        val todoIds =
            controller.board
                .column("todo")!!
                .cards
                .map { it.id }
        val doingIds =
            controller.board
                .column("doing")!!
                .cards
                .map { it.id }
        assertEquals(listOf("c1", "c3"), todoIds)
        assertEquals(listOf("d1", "c2"), doingIds)

        assertTrue(controller.history.canUndo())
        assertEquals(initial, controller.history.undo())
    }

    @Test
    fun `escape while dragging restores original board and announces cancellation`() {
        val initial = sampleBoard()
        val controller = DragController(initial)
        controller.focus("c2")
        controller.handle(Key.SPACE)
        controller.handle(Key.DOWN)
        controller.handle(Key.RIGHT)

        val cancelled = controller.handle(Key.ESCAPE)
        assertNull(cancelled.dragging)
        assertEquals(
            "Cancelled, card c2 returned to To do, position 2 of 3",
            cancelled.announcement,
        )
        assertEquals(initial, controller.board)
        assertEquals(
            listOf("c1", "c2", "c3"),
            controller.board
                .column("todo")!!
                .cards
                .map { it.id },
        )
    }

    @Test
    fun `moving right into a column at its WIP limit is refused and announced`() {
        val initial = sampleBoard(doingWip = 1)
        val controller = DragController(initial)
        controller.focus("c2")
        controller.handle(Key.SPACE)

        val refused = controller.handle(Key.RIGHT)
        assertEquals("Doing is at its limit of 1", refused.announcement)
        assertEquals(initial, refused.board)
        assertEquals(
            listOf("c1", "c2", "c3"),
            controller.board
                .column("todo")!!
                .cards
                .map { it.id },
        )
    }

    @Test
    fun `up moves card to position 1 of 3 in To do and clamps at ends`() {
        val initial = sampleBoard()
        val controller = DragController(initial)
        controller.focus("c2")
        controller.handle(Key.ENTER)

        val movedUp = controller.handle(Key.UP)
        assertEquals("Card c2, position 1 of 3 in To do", movedUp.announcement)

        val clampedUp = controller.handle(Key.UP)
        assertEquals("Card c2, position 1 of 3 in To do", clampedUp.announcement)

        val dropped = controller.handle(Key.ENTER)
        assertEquals("Dropped card c2 in To do, position 1 of 3", dropped.announcement)
    }

    @Test
    fun `history undo and redo work and capacity of 100 drops the oldest on 101st command`() {
        val initial = sampleBoard()
        val history = History(initial)
        assertFalse(history.canUndo())
        assertFalse(history.canRedo())

        val afterMove = history.apply(Command.MoveCard("c2", "doing", 1))
        assertEquals(listOf("d1", "c2"), afterMove.column("doing")!!.cards.map { it.id })
        assertTrue(history.canUndo())

        val undone = history.undo()
        assertEquals(initial, undone)
        assertTrue(history.canRedo())

        val redone = history.redo()
        assertEquals(afterMove, redone)

        val capHistory = History(initial, capacity = 100)
        val boardAfterFirstCommand = capHistory.apply(Command.RenameCard("c1", "Title #1"))
        for (i in 2..101) {
            capHistory.apply(Command.RenameCard("c1", "Title #$i"))
        }
        var steps = 0
        while (capHistory.canUndo()) {
            capHistory.undo()
            steps++
        }
        assertEquals(100, steps)
        assertEquals(boardAfterFirstCommand, capHistory.board)
    }

    @Test
    fun `BoardStore save and load round-trips special characters and preserves file on failed rename`() {
        val fs = FakeFileSystem()
        val store = BoardStore("board.kanban", fs)
        val trickyTitle = "Line1\tTabbed\nLine2\rCarriage 100% done"
        val board =
            Board(
                columns =
                    listOf(
                        Column(
                            id = "todo",
                            title = "To do\t100%",
                            wipLimit = 5,
                            cards =
                                listOf(
                                    Card("c1", trickyTitle),
                                ),
                        ),
                        Column(
                            id = "done",
                            title = "Done",
                            wipLimit = null,
                            cards = emptyList(),
                        ),
                    ),
            )

        store.save(board)
        val raw = fs.getFileString("board.kanban")!!
        assertEquals("kanban-board v1", raw.lineSequence().first())

        val loaded = store.load()
        assertEquals(board, loaded)

        // Simulate failure between writing temp file and atomic rename
        fs.failOnAtomicMove = true
        val modifiedBoard =
            Board(
                columns =
                    listOf(
                        Column(id = "todo", title = "Changed", cards = emptyList()),
                    ),
            )
        assertThrows<IllegalStateException> {
            store.save(modifiedBoard)
        }
        assertEquals(raw, fs.getFileString("board.kanban"))
        assertEquals(board, store.load())
    }

    @Test
    fun `BoardStore works with NioFileSystem on disk`(
        @TempDir tempDir: Path,
    ) {
        val filePath = tempDir.resolve("sub/board.kanban").toString()
        val store = BoardStore(filePath, NioFileSystem())
        val board = sampleBoard(doingWip = 2)
        store.save(board)
        assertEquals(board, store.load())
    }

    @Test
    fun `error codes match specification for invalid inputs and malformed files`() {
        val fs = FakeFileSystem()
        val store = BoardStore("test.kanban", fs)

        // 1. unsupported_version on 'kanban-board v2'
        fs.putFile("test.kanban", "kanban-board v2\ncolumn\ttodo\tTo do\t\n")
        val versionErr = assertThrows<BoardException> { store.load() }
        assertEquals("unsupported_version", versionErr.code)
        assertEquals(1, versionErr.line())

        // 2. malformed on line 3
        fs.putFile(
            "test.kanban",
            "kanban-board v1\ncolumn\ttodo\tTo do\t\nbad_record\tx\n",
        )
        val malformedErr = assertThrows<BoardException> { store.load() }
        assertEquals("malformed", malformedErr.code)
        assertEquals(3, malformedErr.line())

        // 3. file_too_large for 5_242_881 bytes
        fs.putBytes("test.kanban", ByteArray(5_242_881) { 'a'.code.toByte() })
        val sizeErr = assertThrows<BoardException> { store.load() }
        assertEquals("file_too_large", sizeErr.code)

        // 4. duplicate_id for repeated card id
        val dupErr =
            assertThrows<BoardException> {
                Board(
                    columns =
                        listOf(
                            Column(
                                id = "todo",
                                title = "To do",
                                cards = listOf(Card("c1", "First"), Card("c1", "Second")),
                            ),
                        ),
                )
            }
        assertEquals("duplicate_id", dupErr.code)

        // 5. title_too_long for 501 characters
        val longTitle = "x".repeat(501)
        val titleErr =
            assertThrows<BoardException> {
                Card("c1", longTitle)
            }
        assertEquals("title_too_long", titleErr.code)
    }

    @Test
    fun `edge cases - single column drag, empty column move, undo after load`() {
        // Dragging in a single-column board (LEFT and RIGHT stay in the only column)
        val singleColBoard =
            Board(
                columns =
                    listOf(
                        Column(
                            id = "only",
                            title = "Only",
                            cards = listOf(Card("c1", "One"), Card("c2", "Two")),
                        ),
                    ),
            )
        val singleCtrl = DragController(singleColBoard)
        singleCtrl.focus("c1")
        singleCtrl.handle(Key.SPACE)
        val leftInSingle = singleCtrl.handle(Key.LEFT)
        assertEquals("Card c1, position 1 of 2 in Only", leftInSingle.announcement)
        val rightInSingle = singleCtrl.handle(Key.RIGHT)
        assertEquals("Card c1, position 1 of 2 in Only", rightInSingle.announcement)
        singleCtrl.handle(Key.SPACE)

        // Moving to an empty column
        val emptyColBoard =
            Board(
                columns =
                    listOf(
                        Column(
                            id = "todo",
                            title = "To do",
                            cards = listOf(Card("c1", "One"), Card("c2", "Two")),
                        ),
                        Column(
                            id = "empty",
                            title = "Empty",
                            cards = emptyList(),
                        ),
                    ),
            )
        val emptyCtrl = DragController(emptyColBoard)
        emptyCtrl.focus("c2")
        emptyCtrl.handle(Key.SPACE)
        val intoEmpty = emptyCtrl.handle(Key.RIGHT)
        assertEquals("Card c2, position 1 of 1 in Empty", intoEmpty.announcement)
        val droppedInEmpty = emptyCtrl.handle(Key.SPACE)
        assertEquals("Dropped card c2 in Empty, position 1 of 1", droppedInEmpty.announcement)
        assertEquals(
            listOf("c2"),
            emptyCtrl.board
                .column("empty")!!
                .cards
                .map { it.id },
        )

        // Undo after load (empty history)
        val fs = FakeFileSystem()
        val store = BoardStore("board.kanban", fs)
        store.save(emptyCtrl.board)
        val loadedHistory = History(store.load())
        assertFalse(loadedHistory.canUndo())
        val undoErr = assertThrows<BoardException> { loadedHistory.undo() }
        assertEquals("nothing_to_undo", undoErr.code)
    }

    @Test
    fun `edge cases - file without trailing newline and card before column`() {
        val fs = FakeFileSystem()

        // File without a trailing newline
        fs.putFile(
            "no_newline.kanban",
            "kanban-board v1\ncolumn\ttodo\tTo do\t\ncard\tc1\tTask 1",
        )
        val noNewlineBoard = BoardStore("no_newline.kanban", fs).load()
        assertEquals(listOf("c1"), noNewlineBoard.column("todo")!!.cards.map { it.id })

        // Card line before any column ('malformed')
        fs.putFile(
            "card_first.kanban",
            "kanban-board v1\ncard\tc1\tOrphan\ncolumn\ttodo\tTo do\t\n",
        )
        val orphanErr =
            assertThrows<BoardException> {
                BoardStore("card_first.kanban", fs).load()
            }
        assertEquals("malformed", orphanErr.code)
        assertEquals(2, orphanErr.line())
    }

    @Test
    fun `commands AddCard, RenameCard, DeleteCard, AddColumn and validation failures`() {
        val history = History(Board())
        history.apply(Command.AddColumn("todo", "To do", wipLimit = 2))
        history.apply(Command.AddCard("todo", "c1", "First task"))
        history.apply(Command.AddCard("todo", Card("c2", "Second task")))

        val wipErr =
            assertThrows<BoardException> {
                history.apply(Command.AddCard("todo", "c3", "Third task"))
            }
        assertEquals("wip_limit_exceeded", wipErr.code)

        history.apply(Command.RenameCard("c1", "First task updated"))
        assertEquals(
            "First task updated",
            history.board
                .column("todo")!!
                .cards[0]
                .title,
        )

        history.apply(Command.DeleteCard("c2"))
        assertEquals(
            1,
            history.board
                .column("todo")!!
                .cards.size,
        )

        assertThrows<BoardException> {
            history.apply(Command.DeleteCard("missing"))
        }
        assertThrows<BoardException> {
            history.apply(Command.RenameCard("missing", "Title"))
        }
        assertThrows<BoardException> {
            history.apply(Command.AddCard("missing", "c9", "Title"))
        }
        assertThrows<BoardException> {
            history.apply(Command.MoveCard("missing", "todo", 0))
        }
        assertThrows<BoardException> {
            history.apply(Command.MoveCard("c1", "missing", 0))
        }
    }

    @Test
    fun `parser and model validation edge cases`() {
        assertThrows<BoardException> { Card("", "Valid") }
        assertThrows<BoardException> { Card("id", "   ") }
        assertThrows<BoardException> { Column("col", "Title", wipLimit = 0) }
        assertThrows<BoardException> {
            Column("col", "Title", wipLimit = 1, cards = listOf(Card("c1", "1"), Card("c2", "2")))
        }
        assertThrows<BoardException> { History(Board(), capacity = 0) }
        assertThrows<BoardException> { History(Board()).redo() }
        assertThrows<BoardException> { BoardStore.parse("") }
        assertThrows<BoardException> { BoardStore.parse("wrong header") }
        assertThrows<BoardException> { BoardStore.parse("kanban-board v1\ncolumn\tonly_two\tfields") }
        assertThrows<BoardException> { BoardStore.parse("kanban-board v1\ncolumn\tc\tT\tnot_an_int") }
        assertThrows<BoardException> { BoardStore.parse("kanban-board v1\ncolumn\tc\tT\t0") }
        assertThrows<BoardException> { BoardStore.parse("kanban-board v1\ncolumn\tc\tT%2\t") }
        assertThrows<BoardException> { BoardStore.parse("kanban-board v1\ncolumn\tc\tT%ZZ\t") }
    }
}
