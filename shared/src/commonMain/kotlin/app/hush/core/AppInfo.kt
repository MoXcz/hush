// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.core

// What the app says about itself; the platform knows the installed version.
data class AppInfo(
    val version: String,
)

object AppLinks {
    const val SOURCE = "https://github.com/MoXcz/hush"
    const val TOMATO = "https://github.com/nsh07/Tomato"
    const val CURBOX = "https://github.com/curbox-app/curbox-android"
}
