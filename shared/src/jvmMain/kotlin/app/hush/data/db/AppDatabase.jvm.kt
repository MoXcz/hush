// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.data.db

import androidx.room3.Room
import app.hush.core.AppDispatchers
import java.io.File

/** [directory] is the desktop app's data directory; it is created if missing. */
fun createAppDatabase(
    directory: File,
    dispatchers: AppDispatchers,
): AppDatabase {
    directory.mkdirs()
    return buildAppDatabase(
        Room.databaseBuilder<AppDatabase>(name = directory.resolve(DATABASE_FILE_NAME).absolutePath),
        dispatchers,
    )
}
