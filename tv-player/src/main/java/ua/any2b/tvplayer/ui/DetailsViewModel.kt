package ua.any2b.tvplayer.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.any2b.core.anilist.AniListClient
import ua.any2b.core.anilist.AnimeDetails
import ua.any2b.core.anitube.AnitubeClient
import ua.any2b.core.anitube.AnitubeVoice
import ua.any2b.core.extractor.StreamResolver
import ua.any2b.core.matcher.TitleMatcher
import ua.any2b.tvplayer.data.VoiceCache
import ua.any2b.tvplayer.data.WatchHistoryEntry
import ua.any2b.tvplayer.data.WatchHistoryManager

sealed interface DetailsState {
    data object Loading : DetailsState
    data class Ready(
        val details: AnimeDetails,
        val voices: List<AnitubeVoice> = emptyList(),
        val selectedVoiceIndex: Int = 0,
        val selectedEpisodeIndex: Int = 0,
        val isLoadingVoices: Boolean = false,
        val resolvingStream: Boolean = false,
        val historyEntry: WatchHistoryEntry? = null,
    ) : DetailsState
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
                val historyEntry = withContext(Dispatchers.IO) { historyManager.getEntry(animeId) }
                val initialEpisodeIndex = historyEntry?.episodeIndex?.takeIf { it >= 0 } ?: 0
                val initialVoiceIndex = historyEntry?.voiceIndex?.takeIf { it >= 0 } ?: 0

                _state.value = DetailsState.Ready(
                    details = details,
                    selectedVoiceIndex = initialVoiceIndex,
                    selectedEpisodeIndex = initialEpisodeIndex,
                    isLoadingVoices = true,
                    historyEntry = historyEntry
                )

                val query = details.title.romaji ?: details.title.english ?: ""
                if (query.isNotBlank()) {
                    val searchResults = withContext(Dispatchers.IO) { anitubeClient.search(query) }
                    val matched = TitleMatcher.match(details.title, searchResults)
                    val best = matched.firstOrNull()?.result ?: searchResults.firstOrNull()

                    if (best != null) {
                        val voices = withContext(Dispatchers.IO) { anitubeClient.getVoices(best.pageUrl) }
                        VoiceCache.put(animeId, voices)
                        val currentState = _state.value
                        if (currentState is DetailsState.Ready) {
                            val history = currentState.historyEntry
                            val voiceIdx = if (history != null && history.voiceIndex in voices.indices) history.voiceIndex else currentState.selectedVoiceIndex
                            val epIdx = if (history != null && history.episodeIndex >= 0) history.episodeIndex else currentState.selectedEpisodeIndex
                            _state.value = currentState.copy(
                                voices = voices,
                                selectedVoiceIndex = voiceIdx.coerceIn(0, (voices.size - 1).coerceAtLeast(0)),
                                selectedEpisodeIndex = epIdx,
                                isLoadingVoices = false
                            )
                        }
                    } else {
                        updateReady { it.copy(isLoadingVoices = false) }
                    }
                } else {
                    updateReady { it.copy(isLoadingVoices = false) }
                }
            } catch (e: Exception) {
                _state.value = DetailsState.Error(e.message ?: "Не вдалося завантажити деталі")
            }
        }
    }

    fun selectVoice(index: Int) {
        updateReady { it.copy(selectedVoiceIndex = index) }
    }

    fun selectEpisode(index: Int) {
        updateReady { it.copy(selectedEpisodeIndex = index) }
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
