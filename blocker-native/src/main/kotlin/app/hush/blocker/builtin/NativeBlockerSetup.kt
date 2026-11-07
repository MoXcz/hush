// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.blocker.builtin

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import app.hush.blocker.BlockableApp
import app.hush.blocker.BlockerSetup
import app.hush.core.AppDispatchers
import kotlinx.coroutines.withContext

/** Turning the service on, and the apps the picker offers. */
class NativeBlockerSetup(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) : BlockerSetup {
    override fun openSystemSettings(): Boolean {
        val component = ComponentName(context, AppBlockerService::class.java).flattenToString()
        // Android 11+ can open our own service's page directly; older versions get the list.
        val details =
            Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra(Intent.EXTRA_COMPONENT_NAME, component)
                .takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.R }
        return listOfNotNull(details, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)).any { open(it) }
    }

    // Writing the setting needs WRITE_SECURE_SETTINGS, granted once over adb. Without it the
    // service can still turn itself off, but only the user can turn it on again.
    override suspend fun setEnabled(on: Boolean): Boolean =
        withContext(dispatchers.io) {
            if (context.isBlockerServiceEnabled() == on) return@withContext true
            val granted = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
            when {
                granted -> writeEnabled(on)
                !on -> AppBlockerService.disableRunning()
                else -> false
            }
        }

    private fun writeEnabled(on: Boolean): Boolean {
        val resolver = context.contentResolver
        val ours = ComponentName(context, AppBlockerService::class.java)
        val current = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val next = withService(current, ours.flattenToString(), on) { ComponentName.unflattenFromString(it) == ours }
        return try {
            Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next)
            if (on) Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    override suspend fun blockableApps(): List<BlockableApp> =
        withContext(dispatchers.io) {
            val pm = context.packageManager
            val allowed = AlwaysAllowed.packages(context)
            val iconSize = (ICON_DP * context.resources.displayMetrics.density).toInt()
            pm
                .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .distinctBy { it.activityInfo.packageName }
                .filter { it.activityInfo.packageName !in allowed }
                .map { info ->
                    val app = info.activityInfo.applicationInfo
                    BlockableApp(
                        packageName = app.packageName,
                        label = app.loadLabel(pm).toString(),
                        icon = app.loadIcon(pm).toBitmap(iconSize, iconSize).asImageBitmap(),
                    )
                }.sortedBy { it.label.lowercase() }
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
        const val ICON_DP = 40
    }
}

/** [enabled] (colon-separated components) with ours added or removed; other services are kept. */
internal fun withService(
    enabled: String?,
    ours: String,
    on: Boolean,
    isOurs: (String) -> Boolean,
): String {
    val others = enabled.orEmpty().split(':').filter { it.isNotBlank() && !isOurs(it) }
    return (if (on) others + ours else others).joinToString(":")
}

/** Whether the user has turned [AppBlockerService] on in the accessibility settings. */
internal fun Context.isBlockerServiceEnabled(): Boolean {
    val ours = ComponentName(this, AppBlockerService::class.java)
    val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
}

internal fun PackageManager.labelOf(packageName: String): String? =
    try {
        getApplicationInfo(packageName, 0).loadLabel(this).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
