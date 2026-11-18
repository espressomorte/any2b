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
    val nowPlayingLabel: TextView
    val nextEpisodeButton: TextView
    val nowPlayingRow: android.view.View
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
        nowPlayingLabel = findViewById(R.id.tvNowPlaying)
        nextEpisodeButton = findViewById(R.id.btnNextEpisodeControl)
        nowPlayingRow = findViewById(R.id.nowPlayingRow)
        trackActions = findViewById(R.id.playerTrackActions)
        audioTrackButton = findViewById(R.id.btnAudioTracks)
        subtitleTrackButton = findViewById(R.id.btnSubtitleTracks)
        audioTrackChoice = findViewById(R.id.tvAudioTrackChoice)
        subtitleTrackChoice = findViewById(R.id.tvSubtitleTrackChoice)
    }

    fun expandTrackActions(): Boolean {
        if (trackActions.visibility != VISIBLE) return false
        when {
            audioTrackButton.isEnabled -> audioTrackButton.requestFocus()
            subtitleTrackButton.isEnabled -> subtitleTrackButton.requestFocus()
            else -> return false
        }
        return true
    }

    fun collapseTrackActions(returnFocus: Boolean = true) {
        if (returnFocus && visibility == VISIBLE) seekBar.requestFocus()
    }

    fun areTrackActionsExpanded(): Boolean = trackActions.visibility == VISIBLE

    fun hasTrackActionFocus(): Boolean = audioTrackButton.hasFocus() || subtitleTrackButton.hasFocus()
}
