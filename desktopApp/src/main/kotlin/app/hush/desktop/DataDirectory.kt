// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop

import app.hush.data.prefs.SETTINGS_FILE_NAME
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions

/** `$XDG_DATA_HOME/hush`, or `~/.local/share/hush`: the database and the sync identity. */
internal fun dataDirectory(
    env: (String) -> String? = System::getenv,
    home: String = System.getProperty("user.home"),
): File = xdgDirectory(env("XDG_DATA_HOME"), "$home/.local/share")

/** `$XDG_CONFIG_HOME/hush`, or `~/.config/hush`: the settings file. */
internal fun configDirectory(
    env: (String) -> String? = System::getenv,
    home: String = System.getProperty("user.home"),
): File = xdgDirectory(env("XDG_CONFIG_HOME"), "$home/.config")

// The XDG spec says a relative value is invalid and must be ignored.
private fun xdgDirectory(
    value: String?,
    default: String,
): File = File(value?.takeIf { it.isNotBlank() && File(it).isAbsolute } ?: default, "hush")

private val OWNER_ONLY = PosixFilePermissions.fromString("rwx------")

// Other users on the machine mustn't read the history or the settings. Only the hush directory is
// changed (not its parents); a filesystem without POSIX permissions is left as it is.
internal fun makePrivateDirectory(directory: File) {
    val path = directory.toPath()
    Files.createDirectories(path)
    try {
        if (Files.getPosixFilePermissions(path) != OWNER_ONLY) Files.setPosixFilePermissions(path, OWNER_ONLY)
    } catch (e: UnsupportedOperationException) {
        return
    } catch (e: IOException) {
        System.err.println("Hush: can't make $directory private: $e")
    }
}

// Moved from the data directory (0.1.0); on failure the old file stays in use, so the device id is never lost.
internal fun settingsFile(
    dataDirectory: File,
    configDirectory: File,
    move: (Path, Path) -> Unit = { from, to -> Files.move(from, to, StandardCopyOption.ATOMIC_MOVE) },
): File {
    val old = dataDirectory.resolve(SETTINGS_FILE_NAME)
    val new = configDirectory.resolve(SETTINGS_FILE_NAME)
    if (new.exists() || !old.exists()) return new
    return try {
        Files.createDirectories(configDirectory.toPath())
        try {
            move(old.toPath(), new.toPath())
        } catch (e: AtomicMoveNotSupportedException) {
            // Another filesystem: copy beside the target, rename into place, then drop the old file.
            val copy = configDirectory.resolve("$SETTINGS_FILE_NAME.moving").toPath()
            Files.copy(old.toPath(), copy, StandardCopyOption.REPLACE_EXISTING)
            Files.move(copy, new.toPath(), StandardCopyOption.ATOMIC_MOVE)
            Files.delete(old.toPath())
        }
        new
    } catch (e: IOException) {
        System.err.println("Hush: can't move settings to $new: $e")
        if (new.exists()) new else old
    }
}

/**
 * One app instance per directory: two processes on the same DataStore file and database would
 * corrupt them. Null if another instance holds the lock. Keep the lock until exit.
 */
internal fun lockDirectory(directory: File): FileLock? {
    directory.mkdirs()
    val channel = FileChannel.open(directory.resolve(".lock").toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
    val lock =
        try {
            channel.tryLock()
        } catch (e: OverlappingFileLockException) {
            null
        } catch (e: IOException) {
            null
        }
    if (lock == null) channel.close()
    return lock
}
