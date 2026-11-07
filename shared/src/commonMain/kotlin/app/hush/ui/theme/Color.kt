// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// "Dusk": one muted slate-indigo accent on near-neutral cool greys. Every role below is generated
// from the seed with Material Color Utilities (spec 2021, standard contrast) and these tonal palettes,
// so a retune means regenerating this file from new numbers, not hand-editing single colours:
//   seed #5B6395 (HCT hue 278.7)
//   primary   hue 278.7, chroma 30   (primary action, focus ring, selected tab)
//   secondary hue 278.7, chroma 8
//   tertiary  hue 200,   chroma 16   (breaks: a muted slate-teal)
//   neutral   hue 278.7, chroma 3    neutral variant chroma 6
// Checked contrast (WCAG): primary on surface 6.1 light / 10.9 dark, onPrimary on primary 6.4 / 7.7,
// container text 7.2, outline on surface 4.3 / 5.9 (used only for large text).

internal val LightColors =
    lightColorScheme(
        primary = Color(0xFF555C88),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDEE0FF),
        onPrimaryContainer = Color(0xFF3D446E),
        secondary = Color(0xFF5D5E67),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE3E1EC),
        onSecondaryContainer = Color(0xFF46464F),
        tertiary = Color(0xFF4A6364),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFCCE8E8),
        onTertiaryContainer = Color(0xFF324B4C),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF93000A),
        background = Color(0xFFFCF8FB),
        onBackground = Color(0xFF1B1B1E),
        surface = Color(0xFFFCF8FB),
        onSurface = Color(0xFF1B1B1E),
        surfaceVariant = Color(0xFFE4E1E9),
        onSurfaceVariant = Color(0xFF46464C),
        outline = Color(0xFF77767D),
        outlineVariant = Color(0xFFC7C5CD),
        scrim = Color(0xFF000000),
        inverseSurface = Color(0xFF303033),
        inverseOnSurface = Color(0xFFF3F0F3),
        inversePrimary = Color(0xFFBDC3F6),
        surfaceDim = Color(0xFFDCD9DC),
        surfaceBright = Color(0xFFFCF8FB),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF6F2F6),
        surfaceContainer = Color(0xFFF0EDF0),
        surfaceContainerHigh = Color(0xFFEAE7EA),
        surfaceContainerHighest = Color(0xFFE5E1E5),
    )

internal val DarkColors =
    darkColorScheme(
        primary = Color(0xFFBDC3F6),
        onPrimary = Color(0xFF262D56),
        primaryContainer = Color(0xFF3D446E),
        onPrimaryContainer = Color(0xFFDEE0FF),
        secondary = Color(0xFFC7C5D0),
        onSecondary = Color(0xFF2F3038),
        secondaryContainer = Color(0xFF46464F),
        onSecondaryContainer = Color(0xFFE3E1EC),
        tertiary = Color(0xFFB0CCCC),
        onTertiary = Color(0xFF1B3435),
        tertiaryContainer = Color(0xFF324B4C),
        onTertiaryContainer = Color(0xFFCCE8E8),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF131315),
        onBackground = Color(0xFFE5E1E5),
        surface = Color(0xFF131315),
        onSurface = Color(0xFFE5E1E5),
        surfaceVariant = Color(0xFF46464C),
        onSurfaceVariant = Color(0xFFC7C5CD),
        outline = Color(0xFF919097),
        outlineVariant = Color(0xFF46464C),
        scrim = Color(0xFF000000),
        inverseSurface = Color(0xFFE5E1E5),
        inverseOnSurface = Color(0xFF303033),
        inversePrimary = Color(0xFF555C88),
        surfaceDim = Color(0xFF131315),
        surfaceBright = Color(0xFF39393B),
        surfaceContainerLowest = Color(0xFF0E0E10),
        surfaceContainerLow = Color(0xFF1B1B1E),
        surfaceContainer = Color(0xFF201F22),
        surfaceContainerHigh = Color(0xFF2A2A2C),
        surfaceContainerHighest = Color(0xFF353437),
    )
