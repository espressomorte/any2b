package ua.any2b.tvplayer

import ua.any2b.tvplayer.data.TmdbPlaybackSelection
import ua.any2b.core.tmdb.TmdbTitle
import ua.any2b.domain.playback.toloka.TolokaEpisodeContext
import ua.any2b.domain.playback.toloka.TolokaWatchlistPlayback
import ua.any2b.domain.playback.AutoPlaybackDecision
import ua.any2b.domain.playback.AutoPlaybackRequest
import ua.any2b.domain.playback.decideAutoPlayback as evaluateAutoPlayback
import java.util.concurrent.atomic.AtomicLong

@JvmInline
internal value class PlaybackScenarioId(val value: Long)

@JvmInline
internal value class PlaybackRequestId(val value: Long)

internal enum class PlaybackScenarioPhase {
    IDLE, WAITING_FOR_DETAILS, PREPARING_SOURCE, WAITING_FOR_TORRENT,
    READY_TO_LAUNCH, PLAYER_ACTIVE, RETURNING_FROM_PLAYER, FAILED, COMPLETE,
}

internal sealed interface PlaybackScenarioEvent {
    val id: PlaybackScenarioId
    data class DetailsReady(override val id: PlaybackScenarioId) : PlaybackScenarioEvent
    data class AniTubeStateChanged(override val id: PlaybackScenarioId) : PlaybackScenarioEvent
    data class WaitingForTorrent(override val id: PlaybackScenarioId) : PlaybackScenarioEvent
    data class TorrentFilesReady(override val id: PlaybackScenarioId) : PlaybackScenarioEvent
    data class PlaybackLaunched(
        override val id: PlaybackScenarioId,
        val source: TmdbPlaybackSelection.Source? = null,
    ) : PlaybackScenarioEvent
    data class PlayerReturned(override val id: PlaybackScenarioId) : PlaybackScenarioEvent
    data class RetryRequested(override val id: PlaybackScenarioId) : PlaybackScenarioEvent
    data class Failed(override val id: PlaybackScenarioId) : PlaybackScenarioEvent
    data class Finished(override val id: PlaybackScenarioId) : PlaybackScenarioEvent
}

/** Stateful, Android-free owner of a single playback scenario's lifecycle. */
internal class PlaybackCoordinator {
    private val anitubePlayback = ua.any2b.tvplayer.data.AniTubeWatchlistPlayback()
    private val tolokaPlayback = TolokaWatchlistPlayback()
    var scenarioId: PlaybackScenarioId? = null
        private set
    var phase: PlaybackScenarioPhase = PlaybackScenarioPhase.IDLE
        private set
    var activeSource: TmdbPlaybackSelection.Source? = null
        private set
    var launchPending: Boolean = false
        private set
    var currentRequestId: PlaybackRequestId? = null
        private set

    fun begin(): PlaybackScenarioId = PlaybackScenarioId(ids.incrementAndGet()).also {
        scenarioId = it
        phase = PlaybackScenarioPhase.WAITING_FOR_DETAILS
        activeSource = null
        launchPending = false
    }

    fun beginLaunch(): PlaybackRequestId? {
        if (launchPending || phase == PlaybackScenarioPhase.PLAYER_ACTIVE ||
            phase == PlaybackScenarioPhase.COMPLETE) return null
        if (phase == PlaybackScenarioPhase.FAILED ||
            phase == PlaybackScenarioPhase.RETURNING_FROM_PLAYER ||
            phase == PlaybackScenarioPhase.WAITING_FOR_DETAILS)
            phase = PlaybackScenarioPhase.PREPARING_SOURCE
        launchPending = true
        return PlaybackRequestId(requestIds.incrementAndGet()).also { currentRequestId = it }
    }

    fun isCurrentRequest(id: PlaybackRequestId): Boolean = launchPending && currentRequestId == id

    fun launchFailed(id: PlaybackRequestId) {
        if (currentRequestId == id) launchPending = false
    }

    fun playbackLaunched(
        scenarioId: PlaybackScenarioId,
        requestId: PlaybackRequestId,
        source: TmdbPlaybackSelection.Source?,
    ): Boolean {
        if (!isCurrentRequest(requestId)) return false
        launchPending = false
        return dispatch(PlaybackScenarioEvent.PlaybackLaunched(scenarioId, source))
    }

    fun playerReturned(id: PlaybackScenarioId): Boolean {
        launchPending = false
        return dispatch(PlaybackScenarioEvent.PlayerReturned(id))
    }

    fun decideAutoPlayback(
        request: AutoPlaybackRequest,
        detailsReady: Boolean,
        torrentFilesReady: Boolean,
        savedFileAvailable: Boolean,
        episodeFileId: Int?,
        alreadyStarted: Boolean,
        failed: Boolean,
    ): AutoPlaybackDecision = evaluateAutoPlayback(request, detailsReady, torrentFilesReady,
        savedFileAvailable, episodeFileId, alreadyStarted, failed)

    fun onAniTubeState(
        request: ua.any2b.tvplayer.data.AniTubeWatchlistRequest,
        state: ua.any2b.tvplayer.data.AnitubeState,
    ): ua.any2b.tvplayer.data.AniTubeWatchlistAction = anitubePlayback.onState(request, state)

    fun tolokaEpisodeContext(
        fileSeason: Int?, fileEpisodeStart: Int?, fileEpisodeEnd: Int?, folderSeason: Int?,
        requestedSeason: Int, requestedEpisode: Int?, matchedSeason: Int?,
        isTv: Boolean, mappedEpisodeForSeason: (Int) -> Int?,
    ): TolokaEpisodeContext = tolokaPlayback.episodeContext(
        fileSeason, fileEpisodeStart, fileEpisodeEnd, folderSeason, requestedSeason,
        requestedEpisode, matchedSeason, isTv, mappedEpisodeForSeason)

    fun aniTubePlaybackSeasons(
        title: TmdbTitle, requestedTmdbSeason: Int, requestedAnimeSeason: Int?,
        releaseId: Int?, found: ua.any2b.tvplayer.data.AnitubeState.Found?,
    ): ua.any2b.tvplayer.data.AniTubePlaybackSeasons = anitubePlayback.playbackSeasons(
        title, requestedTmdbSeason, requestedAnimeSeason, releaseId, found)

    /** Returns false for stale callbacks and duplicate events that must not restart playback. */
    fun dispatch(event: PlaybackScenarioEvent): Boolean {
        if (event.id != scenarioId) return false
        phase = when (event) {
            is PlaybackScenarioEvent.DetailsReady -> if (phase == PlaybackScenarioPhase.WAITING_FOR_DETAILS)
                PlaybackScenarioPhase.PREPARING_SOURCE else return false
            is PlaybackScenarioEvent.AniTubeStateChanged -> if (phase in setOf(
                PlaybackScenarioPhase.PREPARING_SOURCE, PlaybackScenarioPhase.WAITING_FOR_DETAILS))
                PlaybackScenarioPhase.PREPARING_SOURCE else return false
            is PlaybackScenarioEvent.WaitingForTorrent -> if (phase == PlaybackScenarioPhase.PREPARING_SOURCE)
                PlaybackScenarioPhase.WAITING_FOR_TORRENT else return false
            is PlaybackScenarioEvent.TorrentFilesReady -> if (phase in setOf(
                PlaybackScenarioPhase.PREPARING_SOURCE, PlaybackScenarioPhase.WAITING_FOR_TORRENT))
                PlaybackScenarioPhase.READY_TO_LAUNCH else return false
            is PlaybackScenarioEvent.PlaybackLaunched -> if (phase in setOf(
                PlaybackScenarioPhase.PREPARING_SOURCE, PlaybackScenarioPhase.READY_TO_LAUNCH))
                PlaybackScenarioPhase.PLAYER_ACTIVE.also {
                    activeSource = event.source
                    launchPending = false
                }
                else return false
            is PlaybackScenarioEvent.PlayerReturned -> if (phase == PlaybackScenarioPhase.PLAYER_ACTIVE)
                PlaybackScenarioPhase.RETURNING_FROM_PLAYER else return false
            is PlaybackScenarioEvent.RetryRequested -> if (phase == PlaybackScenarioPhase.FAILED)
                PlaybackScenarioPhase.PREPARING_SOURCE else return false
            is PlaybackScenarioEvent.Failed -> if (phase !in setOf(
                PlaybackScenarioPhase.COMPLETE, PlaybackScenarioPhase.FAILED))
                PlaybackScenarioPhase.FAILED else return false
            is PlaybackScenarioEvent.Finished -> if (phase in setOf(
                PlaybackScenarioPhase.PLAYER_ACTIVE, PlaybackScenarioPhase.RETURNING_FROM_PLAYER,
                PlaybackScenarioPhase.PREPARING_SOURCE, PlaybackScenarioPhase.READY_TO_LAUNCH))
                PlaybackScenarioPhase.COMPLETE else return false
        }
        return true
    }

    fun resolveExternalPlayerReturn(
        context: ExternalPlaybackContext,
        event: ExternalPlayerReturnEvent,
    ): ExternalPlaybackEffect {
        val position = event.positionMs
        val duration = event.durationMs
        return when {
            event.resultCode == 0 && position != null && position >= 0L &&
                duration != null && duration > 0L -> when (context.source) {
                TmdbPlaybackSelection.Source.TORRENT -> {
                    val topicId = context.topicId ?: return ExternalPlaybackEffect.Finish
                    val fileId = context.fileId ?: return ExternalPlaybackEffect.Finish
                    ExternalPlaybackEffect.SaveTorrentProgress(context.title, topicId, fileId,
                        position, duration, context.episodeNumber, context.seasonNumber)
                }
                TmdbPlaybackSelection.Source.ANITUBE -> ExternalPlaybackEffect.SaveEpisodeProgress(
                    context.title, context.voiceIndex, context.episodeIndex,
                    context.episodeNumber ?: 1, (position.toFloat() / duration).coerceIn(0f, 1f),
                    context.seasonNumber ?: 1, position, duration)
            }
            event.resultCode == 1 -> when (context.source) {
                TmdbPlaybackSelection.Source.TORRENT -> if (context.topicId != null && context.fileId != null)
                    ExternalPlaybackEffect.CompleteTorrent(context.title, context.topicId,
                        context.fileId, context.episodeNumber, context.seasonNumber)
                else ExternalPlaybackEffect.Finish
                TmdbPlaybackSelection.Source.ANITUBE -> ExternalPlaybackEffect.CompleteEpisode(
                    context.title, context.voiceIndex, context.episodeIndex,
                    context.episodeNumber ?: 1, context.seasonNumber ?: 1)
            }
            else -> ExternalPlaybackEffect.Finish
        }
    }

    private companion object {
        val ids = AtomicLong()
        val requestIds = AtomicLong()
    }
}

internal data class ExternalPlaybackContext(
    val title: ua.any2b.core.tmdb.TmdbTitle,
    val source: TmdbPlaybackSelection.Source,
    val topicId: String? = null,
    val fileId: Int? = null,
    val episodeNumber: Int? = null,
    val seasonNumber: Int? = null,
    val voiceIndex: Int = 0,
    val episodeIndex: Int = 0,
)

internal data class ExternalPlayerReturnEvent(
    val resultCode: Int,
    val positionMs: Long? = null,
    val durationMs: Long? = null,
)

internal sealed interface ExternalPlaybackEffect {
    data object Finish : ExternalPlaybackEffect
    data class SaveTorrentProgress(val title: ua.any2b.core.tmdb.TmdbTitle,
        val topicId: String, val fileId: Int, val positionMs: Long, val durationMs: Long,
        val episodeNumber: Int?, val seasonNumber: Int?) : ExternalPlaybackEffect
    data class CompleteTorrent(val title: ua.any2b.core.tmdb.TmdbTitle,
        val topicId: String, val fileId: Int, val episodeNumber: Int?, val seasonNumber: Int?) : ExternalPlaybackEffect
    data class SaveEpisodeProgress(val title: ua.any2b.core.tmdb.TmdbTitle,
        val voiceIndex: Int, val episodeIndex: Int, val episodeNumber: Int, val progress: Float,
        val seasonNumber: Int, val positionMs: Long, val durationMs: Long) : ExternalPlaybackEffect
    data class CompleteEpisode(val title: ua.any2b.core.tmdb.TmdbTitle,
        val voiceIndex: Int, val episodeIndex: Int, val episodeNumber: Int,
        val seasonNumber: Int) : ExternalPlaybackEffect
}
