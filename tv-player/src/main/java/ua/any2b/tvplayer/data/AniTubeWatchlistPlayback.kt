package ua.any2b.tvplayer.data

import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbTitle

/** Immutable watchlist inputs needed to resume one AniTube playback request. */
internal data class AniTubeWatchlistRequest(
    val mediaType: TmdbMediaType,
    val tmdbSeason: Int,
    val animeSeason: Int?,
    val episodeNumber: Int,
    val savedReleaseId: Int?,
    val savedReleasePageUrl: String?,
    val savedVoiceIndex: Int,
    val savedVoiceName: String?,
    val savedProgress: TmdbEpisodeProgress?,
)

/** Provider actions for the host to execute through AnitubeController / Android UI. */
internal sealed interface AniTubeWatchlistAction {
    data object None : AniTubeWatchlistAction
    data class SelectRelease(val candidate: AnitubeCandidate) : AniTubeWatchlistAction
    data class LoadSavedRelease(val releaseId: Int, val season: Int, val pageUrl: String?) : AniTubeWatchlistAction
    data class SelectPlayback(
        val voiceIndex: Int,
        val episodeIndex: Int,
        val progress: TmdbEpisodeProgress?,
    ) : AniTubeWatchlistAction
    data class Failed(val message: String) : AniTubeWatchlistAction
}

internal data class AniTubePlaybackSeasons(val tmdbSeason: Int?, val animeSeason: Int?)

/** AniTube-specific watchlist selection and fallback rules; contains no Android dependencies. */
internal class AniTubeWatchlistPlayback {
    private var targetReleaseId: Int? = null
    private var releaseRequested = false
    private var savedReleaseLookupStarted = false
    private var playbackStarted = false
    private var failed = false

    fun playbackSeasons(
        title: TmdbTitle,
        requestedTmdbSeason: Int,
        requestedAnimeSeason: Int?,
        releaseId: Int?,
        found: AnitubeState.Found?,
    ): AniTubePlaybackSeasons {
        val tmdbSeason = title.matchedSeasonNumber
            ?: requestedTmdbSeason.takeIf { title.type == TmdbMediaType.TV }
        val animeSeason = requestedAnimeSeason
            ?: title.matchedAnimeSeasonNumber
            ?: found?.catalog?.candidates?.firstOrNull { it.release.newsId == releaseId }?.seasonNumber
            ?: found?.catalog?.preferredSeason
        return AniTubePlaybackSeasons(tmdbSeason, animeSeason)
    }

    fun onState(request: AniTubeWatchlistRequest, state: AnitubeState): AniTubeWatchlistAction {
        if (playbackStarted || failed || request.episodeNumber <= 0) return AniTubeWatchlistAction.None
        if (targetReleaseId == null) targetReleaseId = request.savedReleaseId
        val preferredAnimeSeason = request.animeSeason

        return when (state) {
            is AnitubeState.Catalog -> {
                val seasonCandidate = if (request.mediaType == TmdbMediaType.MOVIE) {
                    state.resolution.confidentBest
                } else {
                    val season = preferredAnimeSeason ?: state.resolution.preferredSeason
                        ?: request.tmdbSeason
                    state.resolution.candidates.firstOrNull { it.seasonNumber == season }
                        ?: state.resolution.seasons.firstOrNull { it.number == season }
                            ?.releases?.firstOrNull()
                }
                val target = targetReleaseId
                val candidate = target?.let { id ->
                    state.resolution.candidates.firstOrNull { it.release.newsId == id }
                } ?: seasonCandidate.takeIf { target == null }
                if (candidate != null) {
                    targetReleaseId = candidate.release.newsId
                    releaseRequested = true
                    AniTubeWatchlistAction.SelectRelease(candidate)
                } else if (target != null) loadSaved(request, target)
                else fail("Не знайдено реліз AniTube для цього сезону")
            }
            is AnitubeState.Found -> onFound(request, state)
            is AnitubeState.NotFound -> fail("Не знайдено відео на AniTube")
            is AnitubeState.Error -> {
                val savedRelease = targetReleaseId ?: request.savedReleaseId
                if (savedRelease != null && !savedReleaseLookupStarted)
                    loadSaved(request, savedRelease)
                else fail(state.message)
            }
            AnitubeState.Idle, AnitubeState.Searching -> AniTubeWatchlistAction.None
        }
    }

    private fun onFound(
        request: AniTubeWatchlistRequest,
        state: AnitubeState.Found,
    ): AniTubeWatchlistAction {
        if (targetReleaseId == null) {
            targetReleaseId = if (request.mediaType == TmdbMediaType.MOVIE) {
                state.catalog?.confidentBest?.release?.newsId
            } else {
                val season = request.animeSeason ?: state.catalog?.preferredSeason ?: request.tmdbSeason
                state.catalog?.candidates?.firstOrNull { it.seasonNumber == season }?.release?.newsId
            }
        }
        val target = targetReleaseId
        val currentSeason = state.catalog?.candidates
            ?.firstOrNull { it.release.newsId == state.release.newsId }?.seasonNumber
        val preferredSeason = request.animeSeason ?: state.catalog?.preferredSeason ?: request.tmdbSeason
        if (request.mediaType != TmdbMediaType.MOVIE && target == null &&
            currentSeason != null && currentSeason != preferredSeason) {
            return fail("Не знайдено реліз AniTube для цього сезону")
        }
        if (target != null && state.release.newsId != target) {
            val candidate = state.catalog?.candidates?.firstOrNull { it.release.newsId == target }
            return if (candidate != null && !releaseRequested) {
                releaseRequested = true
                AniTubeWatchlistAction.SelectRelease(candidate)
            } else if (candidate == null) fail("Збережений реліз AniTube більше не знайдено")
            else AniTubeWatchlistAction.None
        }

        val voiceIndex = request.savedVoiceName?.let { name ->
            state.voices.indexOfFirst { it.name.equals(name, ignoreCase = true) }
        } ?: request.savedVoiceIndex
        if (voiceIndex !in state.voices.indices)
            return fail("Збережену озвучку AniTube більше не знайдено")
        val episodes = state.voices[voiceIndex].episodes
        val episodeIndex = episodes.indexOfFirst { it.episodeNumber == request.episodeNumber }
            .takeIf { it >= 0 } ?: if (request.mediaType == TmdbMediaType.MOVIE)
            episodes.indices.firstOrNull() ?: -1 else -1
        if (episodeIndex < 0) return fail("У вибраній озвучці не знайдено цю серію")

        val progress = request.savedProgress?.takeIf {
            it.voiceIndex == voiceIndex && it.episodeNumber == request.episodeNumber &&
                it.seasonNumber == request.tmdbSeason
        }
        playbackStarted = true
        return AniTubeWatchlistAction.SelectPlayback(voiceIndex, episodeIndex, progress)
    }

    private fun loadSaved(request: AniTubeWatchlistRequest, releaseId: Int): AniTubeWatchlistAction {
        if (savedReleaseLookupStarted || releaseId <= 0) return fail("Не знайдено реліз AniTube для цього сезону")
        savedReleaseLookupStarted = true
        return AniTubeWatchlistAction.LoadSavedRelease(releaseId,
            request.animeSeason ?: request.tmdbSeason, request.savedReleasePageUrl)
    }

    private fun fail(message: String): AniTubeWatchlistAction {
        failed = true
        return AniTubeWatchlistAction.Failed(message)
    }
}
