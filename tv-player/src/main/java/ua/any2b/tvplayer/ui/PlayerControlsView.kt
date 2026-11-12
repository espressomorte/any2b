package ua.any2b.tvplayer.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import ua.any2b.tvplayer.R

/** Shared TV timeline shell used by Media3 playback and embedded trailer playback. */
class PlayerControlsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {
    val seekBar: SeekBar
    val positionLabel: TextView
    val durationLabel: TextView
    val bufferIndicator: BufferTickIndicatorView
    val bufferContainer: android.view.View
    val nowPlayingLabel: TextView
    val nowPlayingRow: android.view.View
    val nextEpisodeButton: TextView
    val trackActions: LinearLayout
    val audioTrackButton: LinearLayout
    val subtitleTrackButton: LinearLayout
    val audioTrackChoice: TextView
    val subtitleTrackChoice: TextView

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_player_controls, this, true)
        seekBar = findViewById(R.id.playbackSeekBar)
        positionLabel = findViewById(R.id.tvPlaybackPosition)
        durationLabel = findViewById(R.id.tvPlaybackDuration)
        bufferIndicator = findViewById(R.id.ivPlaybackBuffer)
        bufferContainer = findViewById(R.id.playbackBufferContainer)
        nowPlayingLabel = findViewById(R.id.tvNowPlaying)
        nowPlayingRow = findViewById(R.id.nowPlayingRow)
        nextEpisodeButton = findViewById(R.id.btnNextEpisodeControl)
        trackActions = findViewById(R.id.playerTrackActions)
        audioTrackButton = findViewById(R.id.btnAudioTracks)
        subtitleTrackButton = findViewById(R.id.btnSubtitleTracks)
        audioTrackChoice = findViewById(R.id.tvAudioTrackChoice)
        subtitleTrackChoice = findViewById(R.id.tvSubtitleTrackChoice)
    }

    fun expandTrackActions(): Boolean {
        trackActions.animate().cancel()
        trackActions.alpha = 0f
        trackActions.translationY = -8f * resources.displayMetrics.density
        trackActions.visibility = VISIBLE
        trackActions.animate().alpha(1f).translationY(0f).setDuration(180L).start()
        when {
            audioTrackButton.isEnabled -> audioTrackButton.requestFocus()
            subtitleTrackButton.isEnabled -> subtitleTrackButton.requestFocus()
        }
        return true
    }

    fun collapseTrackActions(returnFocus: Boolean = true) {
        trackActions.animate().cancel()
        trackActions.alpha = 1f
        trackActions.translationY = 0f
        trackActions.visibility = GONE
        if (returnFocus && visibility == VISIBLE) seekBar.requestFocus()
    }

    fun areTrackActionsExpanded(): Boolean = trackActions.visibility == VISIBLE

    fun hasTrackActionFocus(): Boolean = audioTrackButton.hasFocus() || subtitleTrackButton.hasFocus()
}
