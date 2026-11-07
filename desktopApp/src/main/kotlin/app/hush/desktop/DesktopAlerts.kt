// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop

import app.hush.core.AppDispatchers
import app.hush.core.ApplicationScope
import app.hush.detox.LevelAlerts
import app.hush.timer.Phase
import app.hush.timer.PhaseAlerts
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineUnavailableException
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/** libnotify's `notify-send` plus a short chime. */
class DesktopAlerts(
    private val scope: ApplicationScope,
    private val dispatchers: AppDispatchers,
) : PhaseAlerts,
    LevelAlerts {
    override fun phaseEnded(
        ended: Phase,
        next: Phase,
    ) {
        val title = if (ended == Phase.Work) "Work phase done" else "Break's over"
        alert(title, "Next: ${next.label()}. Start it in Hush.")
    }

    override fun levelPassed(
        levelMinutes: Int,
        advanced: Boolean,
    ) = alert("Level passed", if (advanced) "The next detox level is open." else "Your path stays where it was.")

    // Desktop notifications expire on their own; there's nothing to take back.
    override fun clear() = Unit

    private fun alert(
        title: String,
        body: String,
    ) {
        scope.launch {
            withContext(dispatchers.io) {
                notify(title, body)
                Chime.play()
            }
        }
    }

    private fun notify(
        title: String,
        body: String,
    ) {
        try {
            val process =
                ProcessBuilder("notify-send", "--app-name=Hush", title, body)
                    .redirectErrorStream(true)
                    .start()
            if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroy()
        } catch (e: IOException) {
            // libnotify isn't installed: the chime still plays.
        }
    }
}

private fun Phase.label(): String =
    when (this) {
        Phase.Work -> "Work"
        Phase.ShortBreak -> "Short break"
        Phase.LongBreak -> "Long break"
    }

// Generated, so no audio file or player library is needed.
internal object Chime {
    const val SAMPLE_RATE = 44_100
    const val PEAK = 0.3

    private val notes = listOf(784.0 to 0.25, 1046.5 to 0.6) // G5, then C6 (Hz to seconds)

    // 16-bit signed little-endian mono PCM.
    fun samples(): ByteArray {
        val counts = notes.map { (_, seconds) -> (SAMPLE_RATE * seconds).toInt() }
        val out = ByteArray(counts.sum() * 2)
        var index = 0
        for ((note, count) in notes.zip(counts)) {
            val (frequency, seconds) = note
            for (i in 0 until count) {
                val t = i.toDouble() / SAMPLE_RATE
                val envelope = min(1.0, t / 0.01) * exp(-4.0 * t / seconds)
                val value = (PEAK * envelope * sin(2 * PI * frequency * t) * Short.MAX_VALUE).toInt()
                out[index++] = (value and 0xFF).toByte()
                out[index++] = ((value shr 8) and 0xFF).toByte()
            }
        }
        return out
    }

    fun play() {
        val format = AudioFormat(SAMPLE_RATE.toFloat(), 16, 1, true, false)
        val data = samples()
        try {
            AudioSystem.getSourceDataLine(format).use { line ->
                line.open(format)
                line.start()
                line.write(data, 0, data.size)
                line.drain()
            }
        } catch (e: LineUnavailableException) {
            // No sound device: the notification is still shown.
        } catch (e: IllegalArgumentException) {
            // No line supports the format.
        }
    }
}
