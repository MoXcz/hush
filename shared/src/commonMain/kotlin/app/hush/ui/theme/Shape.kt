// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2025-2026 Nishant Mishra (Tomato, https://github.com/nsh07/Tomato): adapted from ui/theme/Shape.kt and Color.kt.
package app.hush.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape

/** Rows of a grouped list: big outer corners, small inner ones, rounder while pressed or selected. */
@Composable
internal fun segmentedShapes(
    index: Int,
    count: Int,
): ListItemShapes {
    val shapes = MaterialTheme.shapes
    return ListItemDefaults.segmentedShapes(
        index,
        count,
        ListItemDefaults.shapes(
            shape = if (count == 1) shapes.large else shapes.extraSmall,
            selectedShape = shapes.extraLargeIncreased,
            pressedShape = shapes.extraLargeIncreased,
            focusedShape = shapes.large,
            hoveredShape = shapes.extraLarge,
            draggedShape = shapes.extraLargeIncreased,
        ),
    )
}

internal val segmentedColors: ListItemColors
    @Composable get() =
        ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceBright,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceBright,
        )

/** Static shape for a row that isn't clickable, matching [segmentedShapes]. */
@Composable
internal fun segmentShape(
    index: Int,
    count: Int,
): Shape {
    val shapes = MaterialTheme.shapes
    val outer = shapes.large.topStart
    val inner = shapes.extraSmall.topStart
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner,
    )
}
