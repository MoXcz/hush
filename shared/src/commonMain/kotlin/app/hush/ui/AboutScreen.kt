// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.hush.ui.theme.segmentShape

// A page the About screen links to.
data class AboutLink(
    val label: String,
    val url: String,
)

// The version, the licence and where the app comes from.
@Composable
fun AboutScreen(
    title: String,
    name: String,
    version: String,
    licence: String,
    credits: String,
    links: List<AboutLink>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxSize().background(colors.surfaceContainer).then(modifier)) {
        ScreenTopBar(
            title = { ScreenTitle(title) },
            navigationIcon = { BackButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) },
        )
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .padding(bottom = 10.dp)
                        .fillMaxWidth()
                        .clip(segmentShape(0, 1))
                        .background(colors.surfaceBright)
                        .padding(16.dp),
            ) {
                Text(name, style = MaterialTheme.typography.titleLarge)
                Text(version, color = colors.onSurfaceVariant)
                Text(licence)
                Text(credits)
            }
            links.forEachIndexed { index, link -> LinkRow(link.label, link.url, index, links.size) }
        }
    }
}
