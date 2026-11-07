// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2025-2026 Nishant Mishra (Tomato, https://github.com/nsh07/Tomato): adapted from ui/theme/Type.kt.
package app.hush.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.hush.resources.Res
import app.hush.resources.manrope
import org.jetbrains.compose.resources.Font

private val Baseline = Typography()

@Immutable
data class AppFonts(
    /** Screen titles: semibold, quieter than a display face. */
    val title: FontFamily,
    /** Numbers that count (timer, countdowns); use with "tnum". */
    val clock: FontFamily,
)

internal val LocalAppFonts = staticCompositionLocalOf { AppFonts(FontFamily.Default, FontFamily.Default) }

/** Centered screen title style used by every top bar. */
internal val AppFonts.titleStyle: TextStyle
    get() = TextStyle(fontFamily = title, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp)

// Manrope is one variable file (wght 200 to 800), subset to Latin; each weight is an instance of it.
@Composable
private fun manrope(weight: Int) =
    Font(Res.font.manrope, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

@Composable
internal fun appFonts(): AppFonts {
    val semiBold = manrope(600)
    val medium = manrope(500)
    return remember(semiBold, medium) { AppFonts(title = FontFamily(semiBold), clock = FontFamily(medium)) }
}

@Composable
internal fun appTypography(): Typography {
    val regularFont = manrope(400)
    val mediumFont = manrope(500)
    val semiBoldFont = manrope(600)
    return remember(regularFont, mediumFont, semiBoldFont) {
        val regular = FontFamily(regularFont)
        val medium = FontFamily(mediumFont)
        val semiBold = FontFamily(semiBoldFont)

        fun TextStyle.with(family: FontFamily) = copy(fontFamily = family)
        Typography(
            displayLarge = Baseline.displayLarge.with(medium),
            displayMedium = Baseline.displayMedium.with(medium),
            displaySmall = Baseline.displaySmall.with(medium),
            headlineLarge = Baseline.headlineLarge.with(semiBold),
            headlineMedium = Baseline.headlineMedium.with(semiBold),
            headlineSmall = Baseline.headlineSmall.with(semiBold),
            titleLarge = Baseline.titleLarge.with(regular),
            titleMedium = Baseline.titleMedium.with(semiBold),
            titleSmall = Baseline.titleSmall.with(semiBold),
            bodyLarge = Baseline.bodyLarge.with(medium),
            bodyMedium = Baseline.bodyMedium.with(regular),
            bodySmall = Baseline.bodySmall.with(regular),
            labelLarge = Baseline.labelLarge.with(semiBold),
            labelMedium = Baseline.labelMedium.with(semiBold),
            labelSmall = Baseline.labelSmall.with(semiBold),
        )
    }
}
