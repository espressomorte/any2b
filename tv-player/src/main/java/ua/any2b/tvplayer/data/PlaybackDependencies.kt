package ua.any2b.tvplayer.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import ua.any2b.core.anitube.AnitubeClient
import ua.any2b.core.extractor.StreamResolver

/** App composition point for source-specific playback dependencies. */
class PlaybackDependencies(context: Context) {
    private val appContext = context.applicationContext

    fun createAnitubeController(scope: CoroutineScope): AnitubeController {
        val anitubeClient = AnitubeClient()
        return AnitubeController(
            scope = scope,
            resolver = AnitubeResolver(appContext, anitube = anitubeClient),
            anitube = anitubeClient,
            streamResolver = StreamResolver(),
        )
    }
}
