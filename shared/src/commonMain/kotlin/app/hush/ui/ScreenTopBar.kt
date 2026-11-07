// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.ui

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import app.hush.resources.Res
import app.hush.resources.ic_arrow_back
import app.hush.ui.theme.LocalAppFonts
import app.hush.ui.theme.titleStyle
import org.jetbrains.compose.resources.painterResource

/** Centered screen title (Tomato layout, quieter semibold face). [title] can animate its own text. */
@Composable
internal fun ScreenTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable () -> Unit = {},
    containerColor: Color = Color.Transparent,
) {
    TopAppBar(
        title = title,
        subtitle = {},
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = { actions() },
        titleHorizontalAlignment = Alignment.CenterHorizontally,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor, scrolledContainerColor = containerColor),
    )
}

@Composable
internal fun ScreenTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
) {
    val style = LocalAppFonts.current.titleStyle
    Text(
        text,
        modifier = modifier,
        style = style,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
        // Long titles shrink instead of clipping.
        autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = style.fontSize),
    )
}

@Composable
internal fun BackButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalIconButton(onClick = onClick, shapes = IconButtonDefaults.shapes(), modifier = modifier) {
        Icon(painterResource(Res.drawable.ic_arrow_back), contentDescription = contentDescription)
    }
}
