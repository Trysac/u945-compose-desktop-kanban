package io.github.trysac.compose_desktop_kanban

/**
 * Exception thrown when board validation, command execution, or persistence fails.
 *
 * @property code Stable machine-readable error code (for example `duplicate_id`, `title_too_long`,
 * `malformed`, `unsupported_version`, `file_too_large`).
 * @property line 1-based line number in a serialized board file where the failure occurred, or `0`
 * when the error is not tied to a specific line.
 * @param message Human-readable error description.
 * @param cause Optional underlying cause.
 */
public class BoardException(
    public val code: String,
    public val line: Int = 0,
    message: String = if (line > 0) "$code at line $line" else code,
    cause: Throwable? = null,
) : IllegalArgumentException(message.take(MAX_MESSAGE_LENGTH), cause) {
    /**
     * Returns the 1-based line number associated with this error, or `0` if not line-specific.
     */
    public fun line(): Int = line

    /**
     * Returns the stable machine-readable error code.
     */
    public fun code(): String = code

    /** Constants for [BoardException]. */
    public companion object {
        private const val MAX_MESSAGE_LENGTH: Int = 200
    }
}
