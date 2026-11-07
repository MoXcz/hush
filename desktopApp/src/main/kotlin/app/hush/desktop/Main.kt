// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import app.hush.desktop.di.desktopModule
import app.hush.desktop.sync.DesktopSyncHost
import app.hush.desktop.usage.FocusTracking
import app.hush.di.sharedModules
import app.hush.ui.App
import org.koin.core.context.startKoin
import java.awt.Toolkit
import javax.imageio.ImageIO
import javax.swing.JOptionPane
import kotlin.system.exitProcess

fun main() {
    // X11 takes the window class from the thread that starts AWT (HUSH_WINDOW_CLASS).
    Toolkit.getDefaultToolkit()
    val dataDirectory = dataDirectory()
    val configDirectory = configDirectory()
    // Both: an instance with only XDG_DATA_HOME changed would otherwise share the settings file.
    // Real paths, so one directory reached through a symlink isn't locked twice (a false "running").
    val locks =
        listOf(dataDirectory, configDirectory).map { it.also(::makePrivateDirectory).canonicalFile }.distinct().map { directory ->
            lockDirectory(directory) ?: run {
                JOptionPane.showMessageDialog(null, "Hush is already running.", "Hush", JOptionPane.INFORMATION_MESSAGE)
                exitProcess(1)
            }
        }
    val settings = settingsFile(dataDirectory, configDirectory)
    val koin = startKoin { modules(sharedModules + desktopModule(dataDirectory, settings)) }.koin
    val syncHost = koin.get<DesktopSyncHost>()
    syncHost.start()
    val focusTracking = koin.getOrNull<FocusTracking>()
    focusTracking?.start()

    application {
        // Phone-shaped, like the screens it shows.
        val windowState = rememberWindowState(size = DpSize(440.dp, 820.dp), position = WindowPosition.Aligned(Alignment.Center))
        val icon =
            remember {
                BitmapPainter(
                    ImageIO.read(Thread.currentThread().contextClassLoader.getResource("hush.png")).toComposeImageBitmap(),
                )
            }
        val viewModels =
            remember {
                object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                }
            }
        Window(onCloseRequest = ::exitApplication, state = windowState, title = "Hush", icon = icon) {
            CompositionLocalProvider(LocalViewModelStoreOwner provides viewModels) {
                App()
            }
        }
    }
    focusTracking?.stop()
    syncHost.stop()
    locks.forEach { it.release() }
}
