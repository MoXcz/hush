// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/** Material 3 Expressive with the fixed "Dusk" palette ([LightColors], [DarkColors]), light or dark from the system. */
@Composable
fun HushTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAppFonts provides appFonts()) {
        MaterialExpressiveTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = appTypography(),
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}
