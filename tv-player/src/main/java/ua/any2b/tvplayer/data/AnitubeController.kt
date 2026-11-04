package ua.any2b.tvplayer.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.any2b.core.anitube.AnitubeClient
import ua.any2b.core.anitube.AnitubeSearchResult
import ua.any2b.core.anitube.AnitubeVoice
import ua.any2b.core.anitube.playableVoices
import ua.any2b.core.extractor.StreamResolver
import ua.any2b.core.tmdb.TmdbMediaType

sealed interface AnitubeState {
    data object Idle : AnitubeState
    data object Searching : AnitubeState
    data class Found(
        val release: AnitubeSearchResult,
        val voices: List<AnitubeVoice>,
        val selectedVoiceIndex: Int = 0,
        val selectedEpisodeIndex: Int = 0,
        val resolvingStream: Boolean = false,
        val resumeProgress: Float = 0f,
        val catalog: AnitubeResolution? = null,
    ) : AnitubeState
    data class NotFound(val candidates: List<AnitubeSearchResult> = emptyList()) : AnitubeState
    data class Catalog(val resolution: AnitubeResolution) : AnitubeState
    data class Error(
        val message: String,
        val candidates: List<AnitubeSearchResult> = emptyList(),
    ) : AnitubeState
}

/** Owns AniTube lookup and playback state for a single TMDB title. */
class AnitubeController(
    context: Context,
    private val scope: CoroutineScope,
    private val resolver: AnitubeResolver = AnitubeResolver(context.applicationContext),
    private val anitube: AnitubeClient = AnitubeClient(),
    private val streamResolver: StreamResolver = StreamResolver(),
) {
    private val _state = MutableStateFlow<AnitubeState>(AnitubeState.Idle)
    val state: StateFlow<AnitubeState> = _state
    private val _aniListAvailability = MutableStateFlow(AniListAvailability.UNKNOWN)
    val aniListAvailability: StateFlow<AniListAvailability> = _aniListAvailability

    private var request: Lookup? = null
    private var job: Job? = null

    fun start(
        type: TmdbMediaType,
        tmdbId: Int,
        season: Int?,
        names: List<String>,
        format: String,
        resumeProgress: TmdbEpisodeProgress? = null,
    ) {
        _aniListAvailability.value = AniListAvailability.CHECKING
        request = Lookup(type, tmdbId, season, names, format, resumeProgress)
        search(request!!)
    }

    fun retry() {
        request?.let {
            _aniListAvailability.value = AniListAvailability.CHECKING
            search(it)
        }
    }

    fun addNames(names: List<String>) {
        val current = request ?: return
        val merged = (current.names + names).map(String::trim).filter(String::isNotBlank)
            .distinctBy(String::lowercase)
        if (merged.size == current.names.size) return
        val enriched = current.copy(names = merged)
        request = enriched
        if (_state.value !is AnitubeState.Idle && _state.value !is AnitubeState.Found) {
            _aniListAvailability.value = AniListAvailability.CHECKING
            search(enriched)
        }
    }

    fun selectRelease(release: AnitubeSearchResult) {
        val current = request ?: return
        val candidates = when (val currentState = _state.value) {
            is AnitubeState.NotFound -> currentState.candidates
            is AnitubeState.Error -> currentState.candidates
            is AnitubeState.Catalog -> currentState.resolution.candidates.map { it.release }
            is AnitubeState.Found -> currentState.catalog?.candidates?.map { it.release }.orEmpty()
            else -> emptyList()
        }
        if (candidates.isNotEmpty() && candidates.none { it.newsId == release.newsId }) return
        if ((_state.value as? AnitubeState.Found)?.release?.newsId != release.newsId)
            request = current.copy(resumeProgress = null)
        val catalog = when (val currentState = _state.value) {
            is AnitubeState.Catalog -> currentState.resolution
            is AnitubeState.Found -> currentState.catalog
            else -> null
        }
        val selectedFormat = catalog?.candidates?.firstOrNull { it.release.newsId == release.newsId }?.format
        job?.cancel()
        _state.value = AnitubeState.Searching
        job = scope.launch {
            loadVoices((request ?: current).let { lookup ->
                if (selectedFormat != null) lookup.copy(format = selectedFormat) else lookup
            }, release, catalog)
        }
    }

    fun selectSeason(number: Int) {
        val found = _state.value as? AnitubeState.Found ?: return
        val candidate = found.catalog?.seasons?.firstOrNull { it.number == number }
            ?.releases?.firstOrNull() ?: return
        if (candidate.release.newsId != found.release.newsId) selectRelease(candidate.release)
    }

    fun selectVoice(index: Int) = updateFound { found ->
        if (index !in found.voices.indices) found
        else found.copy(selectedVoiceIndex = index, selectedEpisodeIndex = 0, resumeProgress = 0f)
    }

    fun selectEpisode(index: Int) = updateFound { found ->
        val episodes = found.voices.getOrNull(found.selectedVoiceIndex)?.episodes.orEmpty()
        if (index !in episodes.indices) found
        else found.copy(selectedEpisodeIndex = index, resumeProgress = 0f)
    }

    fun restoreProgress(progress: TmdbEpisodeProgress?) = updateFound { found ->
        if (progress == null) return@updateFound found
        val voiceIndex = progress.voiceIndex.takeIf { it in found.voices.indices } ?: 0
        val episodes = found.voices[voiceIndex].episodes
        val episodeIndex = episodes.indexOfFirst { it.episodeNumber == progress.episodeNumber }
            .takeIf { it >= 0 }
            ?: progress.episodeIndex.takeIf { it in episodes.indices }
            ?: 0
        found.copy(selectedVoiceIndex = voiceIndex, selectedEpisodeIndex = episodeIndex,
            resumeProgress = progress.progress.takeIf { it in 0.01f..0.98f } ?: 0f)
    }

    fun resolveSelectedStream(
        onError: (String) -> Unit,
        onSuccess: (String, Int, Int, Int, Float) -> Unit,
    ) {
        val found = _state.value as? AnitubeState.Found ?: return
        val episode = found.voices.getOrNull(found.selectedVoiceIndex)
            ?.episodes?.getOrNull(found.selectedEpisodeIndex) ?: run {
            onError("Не вибрано озвучення або епізод")
            return
        }
        _state.value = found.copy(resolvingStream = true)
        scope.launch {
            try {
                val stream = withContext(Dispatchers.IO) { streamResolver.resolve(episode.fileUrl) }
                val latest = _state.value as? AnitubeState.Found
                if (latest?.release?.newsId == found.release.newsId)
                    _state.value = latest.copy(resolvingStream = false)
                onSuccess(stream.streamUrl, found.selectedVoiceIndex, found.selectedEpisodeIndex,
                    episode.episodeNumber, found.resumeProgress)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val latest = _state.value as? AnitubeState.Found
                if (latest?.release?.newsId == found.release.newsId)
                    _state.value = latest.copy(resolvingStream = false)
                onError(error.message?.takeIf(String::isNotBlank)
                    ?: "Не вдалося отримати відеопотік")
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    private fun search(lookup: Lookup) {
        job?.cancel()
        _state.value = AnitubeState.Searching
        job = scope.launch {
            try {
                val resolution = resolver.find(lookup.type, lookup.tmdbId, lookup.season, lookup.names) {
                    _aniListAvailability.value = it
                }
                _aniListAvailability.value = resolution.aniListAvailability
                if (resolution.aniListAvailability == AniListAvailability.MISSING) {
                    _state.value = AnitubeState.Catalog(resolution)
                    return@launch
                }
                val best = resolution.confidentBest
                if (best == null) {
                    _state.value = if (resolution.searchError != null)
                        AnitubeState.Error(resolution.searchError, resolution.candidates.map { it.release })
                    else AnitubeState.Catalog(resolution)
                } else {
                    loadVoices(best.format?.let { lookup.copy(format = it) } ?: lookup,
                        best.release, resolution)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _aniListAvailability.value = AniListAvailability.UNKNOWN
                _state.value = AnitubeState.Error(error.message?.takeIf(String::isNotBlank)
                    ?: "Не вдалося виконати пошук на AniTube")
            }
        }
    }

    private suspend fun loadVoices(
        lookup: Lookup,
        release: AnitubeSearchResult,
        catalog: AnitubeResolution? = null,
    ) {
        try {
            val voices = playableVoices(anitube.getVoices(release.pageUrl), lookup.format, streamResolver)
            if (voices.isEmpty()) {
                _state.value = if (catalog != null) AnitubeState.Catalog(catalog)
                    else AnitubeState.NotFound(listOf(release))
            } else {
                val found = AnitubeState.Found(release, voices, catalog = catalog)
                _state.value = request?.resumeProgress?.let { progress ->
                    restoreInto(found, progress)
                } ?: found
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _state.value = AnitubeState.Error(error.message?.takeIf(String::isNotBlank)
                ?: "Не вдалося завантажити список серій AniTube", listOf(release))
        }
    }

    private fun restoreInto(found: AnitubeState.Found, progress: TmdbEpisodeProgress): AnitubeState.Found {
        val voiceIndex = progress.voiceIndex.takeIf { it in found.voices.indices } ?: 0
        val episodes = found.voices[voiceIndex].episodes
        val episodeIndex = episodes.indexOfFirst { it.episodeNumber == progress.episodeNumber }
            .takeIf { it >= 0 }
            ?: progress.episodeIndex.takeIf { it in episodes.indices }
            ?: 0
        return found.copy(selectedVoiceIndex = voiceIndex, selectedEpisodeIndex = episodeIndex,
            resumeProgress = progress.progress.takeIf { it in 0.01f..0.98f } ?: 0f)
    }

    private inline fun updateFound(transform: (AnitubeState.Found) -> AnitubeState.Found) {
        val current = _state.value as? AnitubeState.Found ?: return
        _state.value = transform(current)
    }

    private data class Lookup(
        val type: TmdbMediaType,
        val tmdbId: Int,
        val season: Int?,
        val names: List<String>,
        val format: String,
        val resumeProgress: TmdbEpisodeProgress?,
    )
}
