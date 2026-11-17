package io.github.trysac.compose_desktop_kanban

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * Abstraction over file system operations required for crash-safe atomic board persistence.
 */
public interface FileSystemPort {
    /**
     * Reads the entire content of [path], or returns `null` if the file does not exist.
     */
    public fun read(path: String): ByteArray?

    /**
     * Writes [bytes] durably to a temporary sibling file for [path] and returns the temporary path.
     */
    public fun writeTemp(
        path: String,
        bytes: ByteArray,
    ): String

    /**
     * Atomically moves/renames the file at [from] to [to], replacing any existing file at [to].
     */
    public fun atomicMove(
        from: String,
        to: String,
    )
}

/**
 * Production [FileSystemPort] backed by `java.nio.file.Files`.
 *
 * Writes temporary files with [StandardOpenOption.SYNC] and renames them using
 * [StandardCopyOption.ATOMIC_MOVE] and [StandardCopyOption.REPLACE_EXISTING].
 */
public class NioFileSystem : FileSystemPort {
    override fun read(path: String): ByteArray? {
        val target = Paths.get(path)
        if (!Files.exists(target)) {
            return null
        }
        val size = Files.size(target)
        if (size > MAX_FILE_BYTES) {
            throw BoardException(
                code = "file_too_large",
                message = "File size $size exceeds maximum of $MAX_FILE_BYTES bytes",
            )
        }
        return Files.readAllBytes(target)
    }

    override fun writeTemp(
        path: String,
        bytes: ByteArray,
    ): String {
        val target = Paths.get(path).toAbsolutePath()
        val parent = target.parent
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent)
        }
        val tempPath = target.resolveSibling("${target.fileName}.tmp")
        Files.write(
            tempPath,
            bytes,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
            StandardOpenOption.SYNC,
        )
        return tempPath.toString()
    }

    override fun atomicMove(
        from: String,
        to: String,
    ) {
        val sourcePath = Paths.get(from)
        val targetPath = Paths.get(to)
        Files.move(
            sourcePath,
            targetPath,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }
}
