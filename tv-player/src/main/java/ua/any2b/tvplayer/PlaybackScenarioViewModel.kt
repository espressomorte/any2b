package ua.any2b.tvplayer

import androidx.lifecycle.ViewModel
import ua.any2b.tvplayer.data.PlaybackSourcePorts

/** Retains scenario state and provider adapters across Activity configuration changes. */
internal class PlaybackScenarioViewModel : ViewModel() {
    val coordinator = PlaybackCoordinator()
    val sourcePorts = PlaybackSourcePorts()
}
