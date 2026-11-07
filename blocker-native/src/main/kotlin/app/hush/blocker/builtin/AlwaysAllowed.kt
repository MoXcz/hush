// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2024-2026 Nethical and Curbox contributors (Curbox: utils/PackageFinderTools.kt, blockers/FocusModeBlocker.kt), adapted.
package app.hush.blocker.builtin

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.telecom.TelecomManager

// Never blocked, so the user can always reach home, calls, the keyboard and the switch to turn blocking off.
internal object AlwaysAllowed {
    private val systemPackages =
        listOf("com.android.systemui", "com.android.settings", "com.android.phone", "com.android.server.telecom")

    fun packages(context: Context): Set<String> {
        val pm = context.packageManager
        return packages(
            ownPackage = context.packageName,
            launchers = pm.handlers(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)),
            settingsApps = pm.handlers(Intent(Settings.ACTION_SETTINGS)),
            defaultDialer = context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage,
            defaultInputMethod = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD),
        )
    }

    // defaultInputMethod is a flattened component, "pkg/.Service".
    fun packages(
        ownPackage: String,
        launchers: List<String>,
        settingsApps: List<String>,
        defaultDialer: String?,
        defaultInputMethod: String?,
    ): Set<String> =
        buildSet {
            add(ownPackage)
            addAll(systemPackages)
            addAll(launchers)
            addAll(settingsApps)
            defaultDialer?.let(::add)
            defaultInputMethod?.substringBefore('/')?.takeIf { it.isNotEmpty() }?.let(::add)
        }

    private fun PackageManager.handlers(intent: Intent): List<String> =
        queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).map { it.activityInfo.packageName }
}
