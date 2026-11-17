package io.github.trysac.compose_desktop_kanban

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

/** Tests of the public API. */
class TextTest {
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = ["'  Hello World '|hello world", "ABC|abc", "'\tx\n'|x"])
    fun trimsAndLowercases(
        input: String,
        want: String,
    ) {
        assertEquals(want, Text.normalize(input))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "\t\n"])
    fun rejectsBlankInput(input: String) {
        val error = assertThrows<BlankInputException> { Text.normalize(input) }
        assertEquals("input is blank", error.message)
    }
}
