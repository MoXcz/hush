// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.data.db

import android.content.Context
import androidx.room3.Room
import app.hush.core.AppDispatchers

fun createAppDatabase(
    context: Context,
    dispatchers: AppDispatchers,
): AppDatabase =
    buildAppDatabase(
        Room.databaseBuilder<AppDatabase>(context, context.getDatabasePath(DATABASE_FILE_NAME).absolutePath),
        dispatchers,
    )
