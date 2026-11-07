// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.android.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import app.hush.core.AppDispatchers
import app.hush.usage.AppTime
import app.hush.usage.AppUsageReader
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** Reads the system's usage events; needs usage access (PACKAGE_USAGE_STATS), granted by the user. */
class UsageStatsAppUsageReader(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) : AppUsageReader {
    override suspend fun foregroundTime(
        start: Instant,
        end: Instant,
    ): List<AppTime>? =
        withContext(dispatchers.io) {
            if (!isPermitted()) return@withContext null
            val manager = context.getSystemService(UsageStatsManager::class.java) ?: return@withContext null
            val from = start.toEpochMilliseconds()
            val to = end.toEpochMilliseconds()
            val events = readEvents(manager, from - LOOKBACK.inWholeMilliseconds, to)
            ForegroundTime
                .perApp(events, from, to, excluded = excludedPackages())
                .entries
                .sortedByDescending { it.value }
                .map { (pkg, millis) -> AppTime(pkg, label(pkg), millis.milliseconds) }
        }

    override fun isPermitted(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    // Some versions open Hush's own page when given its package; the rest ignore it or need the list.
    override fun openSettings(): Boolean {
        val ours = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        return listOf(ours, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)).any { open(it) }
    }

    private fun readEvents(
        manager: UsageStatsManager,
        from: Long,
        to: Long,
    ): List<UsageEvent> {
        val resumed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) UsageEvents.Event.ACTIVITY_RESUMED else MOVE_TO_FOREGROUND
        val paused = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) UsageEvents.Event.ACTIVITY_PAUSED else MOVE_TO_BACKGROUND
        val events = manager.queryEvents(from, to) ?: return emptyList()
        val result = mutableListOf<UsageEvent>()
        val event = UsageEvents.Event()
        while (events.getNextEvent(event)) {
            when (event.eventType) {
                resumed -> result += UsageEvent(event.packageName, event.timeStamp, foreground = true, event.className)
                paused -> result += UsageEvent(event.packageName, event.timeStamp, foreground = false, event.className)
            }
        }
        return result
    }

    // Only the default launcher: on AOSP-based ROMs Settings also handles HOME (FallbackHome).
    private fun excludedPackages(): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val defaultHome =
            context.packageManager
                .resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo
                ?.packageName
        return ForegroundTime.excluded(context.packageName, defaultHome)
    }

    private fun label(packageName: String): String =
        try {
            val pm = context.packageManager
            pm
                .getApplicationInfo(packageName, 0)
                .loadLabel(pm)
                .toString()
                .ifBlank { packageName }
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }

    private fun open(intent: Intent): Boolean =
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }

    private companion object {
        // Far enough back to see which app was already in front when the phase started.
        val LOOKBACK = 1.hours

        // UsageEvents.Event.MOVE_TO_FOREGROUND/BACKGROUND, deprecated in API 29 for the same values.
        const val MOVE_TO_FOREGROUND = 1
        const val MOVE_TO_BACKGROUND = 2
    }
}
