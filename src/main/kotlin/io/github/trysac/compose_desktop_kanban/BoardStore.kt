package io.github.trysac.compose_desktop_kanban

private const val HEX_RADIX: Int = 16
private const val COLUMN_FIELD_COUNT: Int = 4
private const val CARD_FIELD_COUNT: Int = 3
private const val ESCAPE_HEX_LENGTH: Int = 2
private const val ESCAPE_TOTAL_LENGTH: Int = 3

/**
 * Crash-safe loader and saver for [Board] instances using the versioned `kanban-board v1`
 * line format.
 *
 * Format specification (`kanban-board v1`, UTF-8, LF line endings):
 * - Line 1: `kanban-board v1`
 * - Column record: `column<TAB><id><TAB><title><TAB><wip>` (empty `<wip>` when no limit)
 * - Card record: `card<TAB><id><TAB><title>` (belongs to the nearest preceding column)
 * - Field escaping: `%`, `\t`, `\r`, and `\n` are percent-encoded as `%25`, `%09`, `%0D`, `%0A`.
 *
 * @property path Target file path.
 * @property fs File system implementation used for reading, temporary writes, and atomic moves.
 */
public class BoardStore(
    public val path: String,
    public val fs: FileSystemPort = NioFileSystem(),
) {
    /**
     * Loads a [Board] from [path].
     *
     * @throws BoardException if the file is missing, exceeds [MAX_FILE_BYTES], uses an
     * unsupported header version, contains malformed lines, or fails board validation.
     */
    public fun load(): Board {
        val bytes =
            fs.read(path)
                ?: throw BoardException(code = "not_found", message = "Board file not found: $path")
        if (bytes.size > MAX_FILE_BYTES) {
            throw BoardException(
                code = "file_too_large",
                message = "File size ${bytes.size} exceeds maximum of $MAX_FILE_BYTES bytes",
            )
        }
        val content = bytes.toString(Charsets.UTF_8)
        return parse(content)
    }

    /**
     * Atomically saves [board] to [path] in `kanban-board v1` format.
     *
     * Writes to a temporary file first and then performs an atomic rename so that a crash
     * or error before the rename leaves any previous file untouched.
     */
    public fun save(board: Board) {
        val serialized = format(board)
        val bytes = serialized.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_FILE_BYTES) {
            throw BoardException(
                code = "file_too_large",
                message = "Serialized board size ${bytes.size} exceeds limit of $MAX_FILE_BYTES bytes",
            )
        }
        val tempPath = fs.writeTemp(path, bytes)
        fs.atomicMove(tempPath, path)
    }

    /** Constants and parser utilities for [BoardStore]. */
    public companion object {
        /** Version header line of the v1 board format. */
        public const val HEADER_V1: String = "kanban-board v1"

        /**
         * Serializes [board] into its `kanban-board v1` string representation.
         */
        public fun format(board: Board): String {
            val sb = StringBuilder()
            sb.append(HEADER_V1).append('\n')
            for (col in board.columns) {
                sb
                    .append("column\t")
                    .append(encodeField(col.id))
                    .append('\t')
                    .append(encodeField(col.title))
                    .append('\t')
                    .append(col.wipLimit?.toString() ?: "")
                    .append('\n')
                for (card in col.cards) {
                    sb
                        .append("card\t")
                        .append(encodeField(card.id))
                        .append('\t')
                        .append(encodeField(card.title))
                        .append('\n')
                }
            }
            return sb.toString()
        }

        /**
         * Parses a `kanban-board v1` string into a [Board].
         */
        public fun parse(content: String): Board {
            val rawLines = splitLines(content)
            validateHeader(rawLines.firstOrNull())
            val columns = mutableListOf<ColumnBuilder>()
            for (idx in 1 until rawLines.size) {
                val lineNumber = idx + 1
                val line = rawLines[idx]
                if (line.isEmpty() && idx == rawLines.lastIndex) {
                    continue
                }
                parseRecord(line, lineNumber, columns)
            }
            return Board(
                columns =
                    columns.map { builder ->
                        Column(
                            id = builder.id,
                            title = builder.title,
                            wipLimit = builder.wipLimit,
                            cards = builder.cards.toList(),
                        )
                    },
            )
        }

        private fun validateHeader(header: String?) {
            val code =
                when {
                    header == null || header.isEmpty() -> "malformed"
                    header == HEADER_V1 -> return
                    header.startsWith("kanban-board ") -> "unsupported_version"
                    else -> "malformed"
                }
            throw BoardException(code = code, line = 1, message = "Header error: $header")
        }

        private fun parseRecord(
            line: String,
            lineNumber: Int,
            columns: MutableList<ColumnBuilder>,
        ) {
            val parts = line.split('\t')
            when (parts[0]) {
                "column" -> {
                    parseColumnRecord(parts, lineNumber, columns)
                }

                "card" -> {
                    if (parts.size != CARD_FIELD_COUNT || columns.isEmpty()) {
                        throw BoardException(
                            code = "malformed",
                            line = lineNumber,
                            message = "Malformed card record at line $lineNumber",
                        )
                    }
                    val id = decodeField(parts[1], lineNumber)
                    val title = decodeField(parts[2], lineNumber)
                    validateIdAtLine(id, lineNumber)
                    validateTitleAtLine(title, lineNumber)
                    columns.last().cards.add(Card(id = id, title = title))
                }

                else -> {
                    throw BoardException(
                        code = "malformed",
                        line = lineNumber,
                        message = "Unknown record type at line $lineNumber",
                    )
                }
            }
        }

        private fun parseColumnRecord(
            parts: List<String>,
            lineNumber: Int,
            columns: MutableList<ColumnBuilder>,
        ) {
            if (parts.size != COLUMN_FIELD_COUNT) {
                throw BoardException(
                    code = "malformed",
                    line = lineNumber,
                    message = "Malformed column record at line $lineNumber",
                )
            }
            val id = decodeField(parts[1], lineNumber)
            val title = decodeField(parts[2], lineNumber)
            val wip = parseWipField(parts[COLUMN_FIELD_COUNT - 1], lineNumber)
            validateIdAtLine(id, lineNumber)
            validateTitleAtLine(title, lineNumber)
            columns.add(ColumnBuilder(id = id, title = title, wipLimit = wip))
        }

        private fun parseWipField(
            wipStr: String,
            lineNumber: Int,
        ): Int? {
            if (wipStr.isEmpty()) return null
            val parsedWip = wipStr.toIntOrNull()
            if (parsedWip == null || parsedWip < 1) {
                throw BoardException(
                    code = "malformed",
                    line = lineNumber,
                    message = "Invalid WIP limit at line $lineNumber",
                )
            }
            return parsedWip
        }

        private class ColumnBuilder(
            val id: String,
            val title: String,
            val wipLimit: Int?,
            val cards: MutableList<Card> = mutableListOf(),
        )
    }
}

private fun splitLines(content: String): List<String> {
    val normalized = content.replace("\r\n", "\n")
    return normalized.split('\n')
}

private fun validateIdAtLine(
    id: String,
    lineNumber: Int,
) {
    if (id.isBlank() || id.length > MAX_ID_LENGTH) {
        throw BoardException(
            code = "malformed",
            line = lineNumber,
            message = "Invalid identifier at line $lineNumber",
        )
    }
}

private fun validateTitleAtLine(
    title: String,
    lineNumber: Int,
) {
    if (title.isBlank()) {
        throw BoardException(
            code = "malformed",
            line = lineNumber,
            message = "Blank title at line $lineNumber",
        )
    }
    if (title.length > MAX_TITLE_LENGTH) {
        throw BoardException(
            code = "title_too_long",
            line = lineNumber,
            message = "Title too long at line $lineNumber",
        )
    }
}

internal fun encodeField(value: String): String {
    val sb = StringBuilder(value.length)
    for (ch in value) {
        when (ch) {
            '%' -> sb.append("%25")
            '\t' -> sb.append("%09")
            '\r' -> sb.append("%0D")
            '\n' -> sb.append("%0A")
            else -> sb.append(ch)
        }
    }
    return sb.toString()
}

internal fun decodeField(
    encoded: String,
    lineNumber: Int,
): String {
    val sb = StringBuilder(encoded.length)
    var i = 0
    while (i < encoded.length) {
        val ch = encoded[i]
        if (ch == '%') {
            if (i + ESCAPE_HEX_LENGTH >= encoded.length) {
                throw BoardException(
                    code = "malformed",
                    line = lineNumber,
                    message = "Incomplete percent escape at line $lineNumber",
                )
            }
            val hex = encoded.substring(i + 1, i + 1 + ESCAPE_HEX_LENGTH).uppercase()
            val decoded =
                when (hex) {
                    "25" -> {
                        '%'
                    }

                    "09" -> {
                        '\t'
                    }

                    "0D" -> {
                        '\r'
                    }

                    "0A" -> {
                        '\n'
                    }

                    else -> {
                        val code =
                            hex.toIntOrNull(HEX_RADIX)
                                ?: throw BoardException(
                                    code = "malformed",
                                    line = lineNumber,
                                    message = "Invalid percent escape at line $lineNumber",
                                )
                        code.toChar()
                    }
                }
            sb.append(decoded)
            i += ESCAPE_TOTAL_LENGTH
        } else {
            sb.append(ch)
            i += 1
        }
    }
    return sb.toString()
}
