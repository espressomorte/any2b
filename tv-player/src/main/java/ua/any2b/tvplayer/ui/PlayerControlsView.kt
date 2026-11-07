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

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_player_controls, this, true)
        seekBar = findViewById(R.id.playbackSeekBar)
        positionLabel = findViewById(R.id.tvPlaybackPosition)
        durationLabel = findViewById(R.id.tvPlaybackDuration)
        nowPlayingLabel = findViewById(R.id.tvNowPlaying)
    }
}
