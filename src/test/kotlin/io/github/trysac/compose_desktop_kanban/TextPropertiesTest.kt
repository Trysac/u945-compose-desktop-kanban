package io.github.trysac.compose_desktop_kanban

import net.jqwik.api.ForAll
import net.jqwik.api.Property
import org.junit.jupiter.api.Assertions.assertEquals

/** Property tests (jqwik) of the invariants of [Text.normalize]. */
class TextPropertiesTest {
    @Property
    fun normalizingTwiceChangesNothing(
        @ForAll input: String,
    ) {
        if (input.isNotBlank()) {
            val once = Text.normalize(input)
            assertEquals(once, Text.normalize(once))
        }
    }

    @Property
    fun outputHasNoSurroundingWhitespace(
        @ForAll input: String,
    ) {
        if (input.isNotBlank()) {
            val out = Text.normalize(input)
            assertEquals(out.trim(), out)
        }
    }
}
