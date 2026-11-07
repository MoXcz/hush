// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.hush.tags.TagColor

// Muted hues at the palette's chroma (about 24): tone 40 on light surfaces, tone 80 on dark ones, so
// each reads like the Dusk primary. Indigo is the primary itself.
private val light =
    mapOf(
        TagColor.Indigo to Color(0xFF555C88),
        TagColor.Teal to Color(0xFF2F6A73),
        TagColor.Sage to Color(0xFF4C6A45),
        TagColor.Sand to Color(0xFF7A5F2E),
        TagColor.Rose to Color(0xFF8C4A5A),
        TagColor.Plum to Color(0xFF74508A),
    )

private val dark =
    mapOf(
        TagColor.Indigo to Color(0xFFBDC3F6),
        TagColor.Teal to Color(0xFF96D0DA),
        TagColor.Sage to Color(0xFFB2D3A6),
        TagColor.Sand to Color(0xFFE5C38A),
        TagColor.Rose to Color(0xFFFFB1C1),
        TagColor.Plum to Color(0xFFE0B8F5),
    )

/** The tag colour for the current theme. */
@Composable
internal fun TagColor.color(): Color {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return (if (isDark) dark else light).getValue(this)
}
