package ua.any2b.tvplayer.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.any2b.core.anilist.AniListClient
import ua.any2b.core.anilist.AnimeDetails
import ua.any2b.core.anitube.AnitubeClient
import ua.any2b.core.anitube.AnitubeSearchResult
import ua.any2b.core.anitube.AnitubeVoice
import ua.any2b.core.extractor.StreamResolver
import ua.any2b.core.matcher.TitleMatcher
import ua.any2b.tvplayer.data.VoiceCache
import ua.any2b.tvplayer.data.WatchHistoryEntry
import ua.any2b.tvplayer.data.WatchHistoryManager

sealed interface VideoSourceState {
    data object Loading : VideoSourceState
    data class Success(val voices: List<AnitubeVoice>) : VideoSourceState
    data class Ambiguous(val candidates: List<AnitubeSearchResult>) : VideoSourceState
    data class Error(val message: String, val candidates: List<AnitubeSearchResult> = emptyList()) : VideoSourceState
}

sealed interface DetailsState {
    data object Loading : DetailsState
    data class Ready(
        val details: AnimeDetails,
        val videoSource: VideoSourceState = VideoSourceState.Loading,
        val selectedVoiceIndex: Int = 0,
        val selectedEpisodeIndex: Int = 0,
        val resolvingStream: Boolean = false,
        val historyEntry: WatchHistoryEntry? = null,
    ) : DetailsState {
        val voices: List<AnitubeVoice> get() = (videoSource as? VideoSourceState.Success)?.voices.orEmpty()
    }
    data class Error(val message: String) : DetailsState
}

class DetailsViewModel(
    private val animeId: Int,
    context: Context,
    private val aniListClient: AniListClient = AniListClient(),
    private val anitubeClient: AnitubeClient = AnitubeClient(),
    private val streamResolver: StreamResolver = StreamResolver(),
) : ViewModel() {

    private val historyManager = WatchHistoryManager(context)

    private val _state = MutableStateFlow<DetailsState>(DetailsState.Loading)
    val state: StateFlow<DetailsState> = _state

    init {
        loadDetails()
    }

    fun loadDetails() {
        _state.value = DetailsState.Loading
        viewModelScope.launch {
            try {
                val details = withContext(Dispatchers.IO) { aniListClient.animeDetails(animeId) }
                val historyEntry = try {
                    withContext(Dispatchers.IO) { historyManager.getEntry(animeId) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                val initialEpisodeIndex = historyEntry?.episodeIndex?.takeIf { it >= 0 } ?: 0
                val initialVoiceIndex = historyEntry?.voiceIndex?.takeIf { it >= 0 } ?: 0

                _state.value = DetailsState.Ready(
                    details = details,
                    selectedVoiceIndex = initialVoiceIndex,
                    selectedEpisodeIndex = initialEpisodeIndex,
                    historyEntry = historyEntry
                )
                searchVideoSource(details)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = DetailsState.Error(e.message ?: "Не вдалося завантажити деталі")
            }
        }
    }

    fun retryVideoSource() {
        val details = (_state.value as? DetailsState.Ready)?.details ?: return
        updateReady { it.copy(videoSource = VideoSourceState.Loading) }
        viewModelScope.launch { searchVideoSource(details) }
    }

    private suspend fun searchVideoSource(details: AnimeDetails) {
        try {
            val query = details.title.romaji ?: details.title.english
            if (query.isNullOrBlank()) {
                updateReady { it.copy(videoSource = VideoSourceState.Error("Немає назви для пошуку на AniTube")) }
                return
            }
            val results = anitubeClient.search(query)
            val matched = TitleMatcher.match(details.title, results)
            val best = matched.firstOrNull()
            when {
                best == null -> updateReady {
                    it.copy(videoSource = VideoSourceState.Error("Відповідний реліз не знайдено", results))
                }
                best.score < 0.65 || (matched.size > 1 && best.score - matched[1].score <= 0.1) -> updateReady {
                    it.copy(videoSource = VideoSourceState.Ambiguous(results))
                }
                else -> loadVoices(best.result)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updateReady { it.copy(videoSource = VideoSourceState.Error(e.message ?: "Помилка пошуку відео")) }
        }
    }

    fun selectRelease(candidate: AnitubeSearchResult) {
        val current = _state.value as? DetailsState.Ready ?: return
        val candidates = when (val source = current.videoSource) {
            is VideoSourceState.Ambiguous -> source.candidates
            is VideoSourceState.Error -> source.candidates
            else -> emptyList()
        }
        if (candidate !in candidates) return
        updateReady { it.copy(videoSource = VideoSourceState.Loading) }
        viewModelScope.launch {
            try {
                loadVoices(candidate, restoreHistory = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateReady { it.copy(videoSource = VideoSourceState.Error(e.message ?: "Помилка завантаження озвучень", candidates)) }
            }
        }
    }

    private suspend fun loadVoices(candidate: AnitubeSearchResult, restoreHistory: Boolean = true) {
        val voices = anitubeClient.getVoices(candidate.pageUrl)
        if (voices.isEmpty()) {
            updateReady { it.copy(videoSource = VideoSourceState.Error("Для релізу немає доступних серій", listOf(candidate))) }
            return
        }
        VoiceCache.put(animeId, voices)
        updateReady { current ->
            val history = current.historyEntry.takeIf { restoreHistory }
            val voiceIndex = history?.voiceIndex?.takeIf { it in voices.indices } ?: 0
            val episodeIndex = history?.episodeIndex?.takeIf { it in voices[voiceIndex].episodes.indices } ?: 0
            current.copy(
                videoSource = VideoSourceState.Success(voices),
                selectedVoiceIndex = voiceIndex,
                selectedEpisodeIndex = episodeIndex,
            )
        }
    }

    fun selectVoice(index: Int) {
        updateReady { current ->
            if (index !in current.voices.indices) current
            else current.copy(selectedVoiceIndex = index, selectedEpisodeIndex = 0)
        }
    }

    fun selectEpisode(index: Int) {
        updateReady { current ->
            if (index !in current.voices.getOrNull(current.selectedVoiceIndex)?.episodes.orEmpty().indices) current
            else current.copy(selectedEpisodeIndex = index)
        }
    }

    fun resolveSelectedStream(
        onError: ((String) -> Unit)? = null,
        onSuccess: (String, Int, Int) -> Unit,
    ) {
        val currentState = _state.value as? DetailsState.Ready ?: return
        val voiceIndex = currentState.selectedVoiceIndex
        val episodeIndex = currentState.selectedEpisodeIndex
        val voice = currentState.voices.getOrNull(voiceIndex)
        if (voice == null) {
            onError?.invoke("Озвучення ще завантажується або не вибрано")
            return
        }
        val episode = voice.episodes.getOrNull(episodeIndex)
        if (episode == null) {
            onError?.invoke("Серію не вибрано")
            return
        }

        val existingProgress = currentState.historyEntry?.takeIf { it.episodeIndex == episodeIndex }?.progress ?: 0.0f
        historyManager.saveProgress(currentState.details, episodeIndex, voiceIndex, existingProgress)
        val updatedHistory = historyManager.getEntry(animeId)
        updateReady { it.copy(resolvingStream = true, historyEntry = updatedHistory) }

        viewModelScope.launch {
            try {
                val extracted = withContext(Dispatchers.IO) { streamResolver.resolve(episode.fileUrl) }
                updateReady { it.copy(resolvingStream = false) }
                onSuccess(extracted.streamUrl, voiceIndex, episodeIndex)
            } catch (e: Exception) {
                updateReady { it.copy(resolvingStream = false) }
                val errorMsg = e.message?.takeIf { it.isNotBlank() } ?: "Помилка отримання джерела"
                onError?.invoke("Не вдалося завантажити потік відео: $errorMsg")
            }
        }
    }

    private inline fun updateReady(block: (DetailsState.Ready) -> DetailsState.Ready) {
        val current = _state.value
        if (current is DetailsState.Ready) {
            _state.value = block(current)
        }
    }
}
