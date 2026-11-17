package io.github.trysac.compose_desktop_kanban

/** Text helpers of this library. */
public object Text {
    /**
     * Trims surrounding whitespace and lower-cases [input].
     *
     * @throws BlankInputException when nothing is left after trimming, instead of an empty string
     */
    public fun normalize(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            throw BlankInputException()
        }
        return trimmed.lowercase()
    }
}
