// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop

import app.hush.data.prefs.SETTINGS_FILE_NAME
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DataDirectoryTest {
    @Test
    fun `a new directory is private to the user, its parents are not changed`() {
        val root = Files.createTempDirectory("hush-test").toFile()
        Files.setPosixFilePermissions(root.toPath(), PosixFilePermissions.fromString("rwxr-xr-x"))
        val directory = root.resolve("share/hush")
        makePrivateDirectory(directory)
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(directory.toPath())))
        assertEquals("rwxr-xr-x", PosixFilePermissions.toString(Files.getPosixFilePermissions(root.toPath())))
        root.deleteRecursively()
    }

    @Test
    fun `an existing readable directory is made private and keeps its files`() {
        val directory = Files.createTempDirectory("hush-test").toFile()
        Files.setPosixFilePermissions(directory.toPath(), PosixFilePermissions.fromString("rwxr-xr-x"))
        directory.resolve("hush.db").writeText("data")
        makePrivateDirectory(directory)
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(directory.toPath())))
        assertEquals("data", directory.resolve("hush.db").readText())
        directory.deleteRecursively()
    }

    @Test
    fun `uses XDG_DATA_HOME when it is an absolute path`() {
        assertEquals(File("/data/hush"), dataDirectory(env = { "/data" }, home = "/home/me"))
    }

    @Test
    fun `falls back to the local share directory when XDG_DATA_HOME is unset, blank or relative`() {
        for (value in listOf(null, "", "relative/dir")) {
            assertEquals(File("/home/me/.local/share/hush"), dataDirectory(env = { value }, home = "/home/me"))
        }
    }

    @Test
    fun `uses XDG_CONFIG_HOME when it is an absolute path`() {
        val env = mapOf("XDG_CONFIG_HOME" to "/conf", "XDG_DATA_HOME" to "/data")
        assertEquals(File("/conf/hush"), configDirectory(env = env::get, home = "/home/me"))
    }

    @Test
    fun `falls back to the config directory when XDG_CONFIG_HOME is unset, blank or relative`() {
        for (value in listOf(null, "", "relative/dir")) {
            assertEquals(File("/home/me/.config/hush"), configDirectory(env = { value }, home = "/home/me"))
        }
    }

    @Test
    fun `settings move from the data directory to a new config directory`() {
        val root = Files.createTempDirectory("hush").toFile()
        val data = root.resolve("data").apply { mkdirs() }
        val config = root.resolve("config")
        data.resolve(SETTINGS_FILE_NAME).writeText("old")

        val file = settingsFile(data, config)

        assertEquals(config.resolve(SETTINGS_FILE_NAME), file)
        assertEquals("old", file.readText())
        assertFalse(data.resolve(SETTINGS_FILE_NAME).exists())
        root.deleteRecursively()
    }

    @Test
    fun `existing settings in the config directory are never overwritten`() {
        val root = Files.createTempDirectory("hush").toFile()
        val data = root.resolve("data").apply { mkdirs() }
        val config = root.resolve("config").apply { mkdirs() }
        data.resolve(SETTINGS_FILE_NAME).writeText("old")
        config.resolve(SETTINGS_FILE_NAME).writeText("new")

        val file = settingsFile(data, config)

        assertEquals("new", file.readText())
        assertEquals("old", data.resolve(SETTINGS_FILE_NAME).readText())
        root.deleteRecursively()
    }

    @Test
    fun `without old settings nothing is created`() {
        val root = Files.createTempDirectory("hush").toFile()
        val config = root.resolve("config")

        assertEquals(config.resolve(SETTINGS_FILE_NAME), settingsFile(root.resolve("data"), config))
        assertFalse(config.exists())
        root.deleteRecursively()
    }

    @Test
    fun `the old settings stay in use when the config directory can't be created`() {
        val root = Files.createTempDirectory("hush").toFile()
        val data = root.resolve("data").apply { mkdirs() }
        data.resolve(SETTINGS_FILE_NAME).writeText("old")
        val blocked = root.resolve("not-a-directory").apply { writeText("") }

        val file = settingsFile(data, blocked.resolve("hush"))

        assertEquals(data.resolve(SETTINGS_FILE_NAME), file)
        assertEquals("old", file.readText())
        root.deleteRecursively()
    }

    @Test
    fun `when the data and config directories are the same the settings file is left untouched`() {
        val root = Files.createTempDirectory("hush").toFile()
        root.resolve(SETTINGS_FILE_NAME).writeText("old")

        val file = settingsFile(root, root)

        assertEquals(root.resolve(SETTINGS_FILE_NAME), file)
        assertEquals("old", file.readText())
        assertEquals(listOf(SETTINGS_FILE_NAME), root.list()!!.toList())
        root.deleteRecursively()
    }

    @Test
    fun `settings are copied into place when an atomic move across filesystems isn't possible`() {
        val root = Files.createTempDirectory("hush").toFile()
        val data = root.resolve("data").apply { mkdirs() }
        val config = root.resolve("config")
        data.resolve(SETTINGS_FILE_NAME).writeText("old")

        val file = settingsFile(data, config, move = noAtomicMove)

        assertEquals(config.resolve(SETTINGS_FILE_NAME), file)
        assertEquals("old", file.readText())
        assertFalse(data.resolve(SETTINGS_FILE_NAME).exists())
        assertEquals(listOf(SETTINGS_FILE_NAME), config.list()!!.toList())
        root.deleteRecursively()
    }

    @Test
    fun `a failed copy across filesystems keeps the old settings in use`() {
        val root = Files.createTempDirectory("hush").toFile()
        val data = root.resolve("data").apply { mkdirs() }
        val config = root.resolve("config")
        data.resolve(SETTINGS_FILE_NAME).writeText("old")
        // A non-empty directory where the copy goes makes the copy fail.
        config
            .resolve("$SETTINGS_FILE_NAME.moving")
            .apply { mkdirs() }
            .resolve("x")
            .writeText("")

        val file = settingsFile(data, config, move = noAtomicMove)

        assertEquals(data.resolve(SETTINGS_FILE_NAME), file)
        assertEquals("old", file.readText())
        assertFalse(config.resolve(SETTINGS_FILE_NAME).exists())
        root.deleteRecursively()
    }

    @Test
    fun `a second lock on the same directory is refused until the first is released`() {
        val directory = Files.createTempDirectory("hush").toFile().resolve("data")
        val first = assertNotNull(lockDirectory(directory))
        assertNull(lockDirectory(directory))
        first.release()
        first.channel().close()
        assertNotNull(lockDirectory(directory)).release()
    }

    private val noAtomicMove: (Path, Path) -> Unit = { from, to ->
        throw AtomicMoveNotSupportedException(
            from.toString(),
            to.toString(),
            "test",
        )
    }
}
