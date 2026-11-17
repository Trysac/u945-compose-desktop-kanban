package io.github.trysac.compose_desktop_kanban

/**
 * Deterministic in-memory [FileSystemPort] used in unit and property tests.
 *
 * Supports simulating a crash or failure between [writeTemp] and [atomicMove].
 */
class FakeFileSystem(
    var failOnAtomicMove: Boolean = false,
) : FileSystemPort {
    private val files: MutableMap<String, ByteArray> = mutableMapOf()

    fun putFile(
        path: String,
        content: String,
    ) {
        files[path] = content.toByteArray(Charsets.UTF_8)
    }

    fun putBytes(
        path: String,
        bytes: ByteArray,
    ) {
        files[path] = bytes.copyOf()
    }

    fun getFileString(path: String): String? = files[path]?.toString(Charsets.UTF_8)

    override fun read(path: String): ByteArray? = files[path]?.copyOf()

    override fun writeTemp(
        path: String,
        bytes: ByteArray,
    ): String {
        val tempPath = "$path.tmp"
        files[tempPath] = bytes.copyOf()
        return tempPath
    }

    override fun atomicMove(
        from: String,
        to: String,
    ) {
        check(!failOnAtomicMove) { "Simulated crash before atomic rename" }
        val data =
            files.remove(from) ?: error("Temporary file missing: $from")
        files[to] = data
    }
}
