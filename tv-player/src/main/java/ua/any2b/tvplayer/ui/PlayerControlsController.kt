package ua.any2b.tvplayer.ui

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.SeekBar
import androidx.media3.common.Player

interface PlayerTransport {
    fun currentPositionMs(): Long
    fun durationMs(): Long
    fun isPlaying(): Boolean
    fun play()
    fun pause()
    fun togglePlayback()
    fun seekTo(positionMs: Long)
}

class Media3PlayerTransport(private val player: Player) : PlayerTransport {
    override fun currentPositionMs() = player.currentPosition
    override fun durationMs() = player.duration
    override fun isPlaying() = player.isPlaying
    override fun play() = player.play()
    override fun pause() = player.pause()
    override fun togglePlayback() {
        if (player.isPlaying) player.pause() else player.play()
    }
    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)
}

/** TV remote and on-screen controls for the internal Media3 player. */
class PlayerControlsController(
    private val controls: PlayerControlsView,
    private val onHidden: () -> Unit = {},
) {
    private companion object {
        const val SCRUB_INCREMENT_SECONDS = 10
        const val HIDE_DELAY_MS = 5_000L
        const val PROGRESS_UPDATE_MS = 500L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val seekBar = controls.seekBar
    private val positionLabel = controls.positionLabel
    private val durationLabel = controls.durationLabel
    private var transport: PlayerTransport? = null
    private var isScrubbing = false

    private val hideControls = Runnable {
        controls.collapseTrackActions(returnFocus = false)
        controls.visibility = View.GONE
        onHidden()
    }
    private val updateProgress = object : Runnable {
        override fun run() {
            if (controls.visibility != View.VISIBLE) return
            refreshProgress()
            handler.postDelayed(this, PROGRESS_UPDATE_MS)
        }
    }

    init {
        seekBar.keyProgressIncrement = SCRUB_INCREMENT_SECONDS

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(bar: SeekBar) {
                isScrubbing = true
                show()
            }

            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) positionLabel.text = formatTime(progress.toLong() * 1_000L)
            }

            override fun onStopTrackingTouch(bar: SeekBar) {
                transport?.seekTo(bar.progress.toLong() * 1_000L)
                isScrubbing = false
                refreshProgress()
                scheduleHide()
            }
        })
    }

    fun attach(transport: PlayerTransport?) {
        this.transport = transport
        refreshProgress()
    }

    fun hideImmediately() {
        handler.removeCallbacks(hideControls)
        controls.collapseTrackActions(returnFocus = false)
        controls.visibility = View.GONE
        onHidden()
    }

    fun refresh() = refreshProgress()

    /** Reveals controls on the first remote input and handles transport shortcuts. */
    fun onRemoteKey(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val current = transport ?: return false
        if (controls.visibility == View.VISIBLE) scheduleHide()

        if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN &&
            controls.visibility == View.VISIBLE && seekBar.hasFocus() &&
            controls.expandTrackActions()) {
            scheduleHide()
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP &&
            controls.visibility == View.VISIBLE && controls.areTrackActionsExpanded() &&
            controls.hasTrackActionFocus()) {
            controls.collapseTrackActions()
            scheduleHide()
            return true
        }

        when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY -> current.play()
            KeyEvent.KEYCODE_MEDIA_PAUSE -> current.pause()
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                current.togglePlayback()
                show()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                show()
                scrubBy(-SCRUB_INCREMENT_SECONDS)
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                show()
                scrubBy(SCRUB_INCREMENT_SECONDS)
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (!seekBar.hasFocus()) return false
                show()
                scrubBy(-SCRUB_INCREMENT_SECONDS)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!seekBar.hasFocus()) return false
                show()
                scrubBy(SCRUB_INCREMENT_SECONDS)
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (controls.visibility != View.VISIBLE) {
                    show()
                    return true
                }
            }
            else -> return false
        }

        refreshProgress()
        show()
        return event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
            event.keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE ||
            event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
            event.keyCode == KeyEvent.KEYCODE_MEDIA_REWIND ||
            event.keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
    }

    fun show() {
        controls.visibility = View.VISIBLE
        if (!controls.hasFocus()) seekBar.requestFocus()
        refreshProgress()
        handler.removeCallbacks(updateProgress)
        handler.post(updateProgress)
        scheduleHide()
    }

    fun release() {
        handler.removeCallbacks(hideControls)
        handler.removeCallbacks(updateProgress)
        transport = null
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideControls)
        if (!isScrubbing) handler.postDelayed(hideControls, HIDE_DELAY_MS)
    }

    private fun scrubBy(incrementSeconds: Int) {
        val current = transport ?: return
        val duration = current.durationMs()
        val durationSeconds = (duration / 1_000L)
            .coerceIn(0L, Int.MAX_VALUE.toLong())
        val baseSeconds = if (controls.visibility == View.VISIBLE) {
            seekBar.progress.toLong()
        } else {
            current.currentPositionMs().coerceAtLeast(0L) / 1_000L
        }
        val targetSeconds = (baseSeconds + incrementSeconds)
            .coerceAtLeast(0)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .let { if (durationSeconds > 0) it.coerceAtMost(durationSeconds) else it }
            .toInt()
        seekBar.progress = targetSeconds
        positionLabel.text = formatTime(targetSeconds.toLong() * 1_000L)
        current.seekTo(targetSeconds.toLong() * 1_000L)
    }

    private fun refreshProgress() {
        val current = transport ?: return
        val position = current.currentPositionMs().coerceAtLeast(0L)
        val duration = current.durationMs().coerceAtLeast(0L)
        val durationSeconds = (duration / 1_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        seekBar.max = durationSeconds
        if (!isScrubbing) {
            seekBar.progress = (position / 1_000L).coerceAtMost(durationSeconds.toLong()).toInt()
            positionLabel.text = formatTime(position)
        }
        durationLabel.text = formatTime(duration)
    }

    private fun formatTime(timeMs: Long): String {
        val totalSeconds = (timeMs / 1_000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3_600L
        val minutes = totalSeconds % 3_600L / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%02d:%02d".format(minutes, seconds)
    }
}
