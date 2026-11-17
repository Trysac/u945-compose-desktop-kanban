package io.github.trysac.compose_desktop_kanban

import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.constraints.NotBlank
import org.junit.jupiter.api.Assertions.assertEquals

/** Property-based tests of [Text]. */
class TextPropertiesTest {
    @Property
    fun normalizeIsIdempotent(
        @ForAll @NotBlank input: String,
    ) {
        val once = Text.normalize(input)
        assertEquals(once, Text.normalize(once))
    }
}
