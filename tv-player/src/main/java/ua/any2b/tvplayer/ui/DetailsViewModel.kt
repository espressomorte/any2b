package ua.any2b.tvplayer.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import ua.any2b.core.anilist.AniListClient
import ua.any2b.core.anilist.AnimeDetails
import ua.any2b.core.anitube.AnitubeClient
import ua.any2b.core.anitube.AnitubeSearchResult
import ua.any2b.core.anitube.AnitubeVoice
import ua.any2b.core.anitube.playableVoices
import ua.any2b.core.extractor.StreamResolver
import ua.any2b.core.matcher.TitleMatcher
import ua.any2b.core.toloka.TolokaHit
import ua.any2b.core.toloka.TolokaHitFilter
import ua.any2b.core.toloka.WorkerFailure
import ua.any2b.core.toloka.TorrServerClient
import ua.any2b.core.toloka.TorrServerFailure
import ua.any2b.core.toloka.TorrServerTorrent
import ua.any2b.core.toloka.TorrServerBasicAuth
import ua.any2b.tvplayer.data.VoiceCache
import ua.any2b.tvplayer.data.FavoritesManager
import ua.any2b.tvplayer.data.WatchHistoryEntry
import ua.any2b.tvplayer.data.WatchHistoryManager
import ua.any2b.tvplayer.data.ReleaseMappingStore
import ua.any2b.tvplayer.data.ReleaseSelection
import ua.any2b.tvplayer.data.SettingsManager
import ua.any2b.tvplayer.data.WorkerConnections
import ua.any2b.tvplayer.data.WorkerKeyStore
import ua.any2b.tvplayer.data.TorrServerAuthStore

sealed interface VideoSourceState {
    data object Loading : VideoSourceState
    data class Success(val release: AnitubeSearchResult, val voices: List<AnitubeVoice>) : VideoSourceState
    data class Ambiguous(val candidates: List<AnitubeSearchResult>) : VideoSourceState
    data class Error(val message: String, val candidates: List<AnitubeSearchResult> = emptyList()) : VideoSourceState
}

sealed interface ManualReleaseSearchState {
    data object Idle : ManualReleaseSearchState
    data object Loading : ManualReleaseSearchState
    data class Results(val candidates: List<AnitubeSearchResult>) : ManualReleaseSearchState
    data class Error(val message: String) : ManualReleaseSearchState
}

sealed interface TolokaSearchState {
    data object Idle : TolokaSearchState
    data object Loading : TolokaSearchState
    data class Results(
        val query: String,
        val hits: List<TolokaHit>,
        val totalFound: Int,
        val selectedId: String? = null,
    ) : TolokaSearchState
    data class Error(val message: String) : TolokaSearchState
}

sealed interface TolokaTransferState {
    data object Idle : TolokaTransferState
    data class Loading(val step: String, val lastSuccess: String) : TolokaTransferState
    data class Files(val torrent: TorrServerTorrent) : TolokaTransferState
    data class Error(
        val message: String,
        val lastSuccess: String = "Пошук роздачі",
        val repeatSearch: Boolean = false,
        val retryMetadata: Boolean = false,
    ) : TolokaTransferState
}

sealed interface DetailsState {
    data object Loading : DetailsState
    data class Ready(
        val details: AnimeDetails,
        val videoSource: VideoSourceState = VideoSourceState.Loading,
        val manualReleaseSearch: ManualReleaseSearchState = ManualReleaseSearchState.Idle,
        val tolokaSearch: TolokaSearchState = TolokaSearchState.Idle,
        val tolokaTransfer: TolokaTransferState = TolokaTransferState.Idle,
        val tolokaPlaybackStatus: String? = null,
        val isFavorite: Boolean = false,
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
    private val favoritesManager = FavoritesManager(context)
    private val releaseMappings = ReleaseMappingStore.getInstance(context)
    private val settingsManager = SettingsManager(context)
    private val workerKeyStore = WorkerKeyStore(context)
    private val torrServerAuthStore = TorrServerAuthStore(context)
    private val workerClient = WorkerConnections.client
    private val torrServerClient = TorrServerClient()
    private var manualSearchJob: Job? = null
    private var tolokaSearchJob: Job? = null
    private var tolokaTransferJob: Job? = null
    private var uploadedTopicId: String? = null
    private var uploadedTorrent: TorrServerTorrent? = null
    private var detailsJob: Job? = null
    private var videoSourceJob: Job? = null

    private val _state = MutableStateFlow<DetailsState>(DetailsState.Loading)
    val state: StateFlow<DetailsState> = _state

    init {
        loadDetails()
    }

    fun loadDetails() {
        detailsJob?.cancel()
        videoSourceJob?.cancel()
        tolokaSearchJob?.cancel()
        tolokaTransferJob?.cancel()
        _state.value = DetailsState.Loading
        detailsJob = viewModelScope.launch {
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
                    isFavorite = favoritesManager.isFavorite(animeId),
                    historyEntry = historyEntry
                )
                videoSourceJob = viewModelScope.launch { loadVideoSource(details) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = DetailsState.Error(e.message ?: "Не вдалося завантажити деталі")
            }
        }
    }

    fun toggleFavorite() {
        val current = _state.value as? DetailsState.Ready ?: return
        val isFavorite = favoritesManager.toggle(current.details)
        updateReady { it.copy(isFavorite = isFavorite) }
    }

    fun retryVideoSource() {
        val details = (_state.value as? DetailsState.Ready)?.details ?: return
        videoSourceJob?.cancel()
        updateReady { it.copy(videoSource = VideoSourceState.Loading) }
        videoSourceJob = viewModelScope.launch { loadVideoSource(details) }
    }

    private suspend fun loadVideoSource(details: AnimeDetails) {
        val saved = try {
            releaseMappings.get(animeId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("DetailsViewModel", "Cannot read saved release mapping", e)
            null
        }
        if (saved != null) {
            val release = saved.asSearchResult()
            try {
                loadVoices(release)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateReady {
                    it.copy(videoSource = VideoSourceState.Error(
                        e.message ?: "Не вдалося оновити збережений реліз",
                        listOf(release),
                    ))
                }
            }
            return
        }
        searchVideoSource(details)
    }

    private suspend fun searchVideoSource(details: AnimeDetails) {
        try {
            val query = details.title.romaji ?: details.title.english ?: details.title.native
            if (query.isNullOrBlank()) {
                updateReady { it.copy(videoSource = VideoSourceState.Error("Немає назви для пошуку на AniTube")) }
                return
            }
            val results = anitubeClient.search(query)
            val initialMatches = TitleMatcher.match(details, results)
            val initialChoice = TitleMatcher.confidentBest(initialMatches)
            if (initialChoice != null) {
                loadVoices(initialChoice, selection = ReleaseSelection.AUTOMATIC)
                return
            }

            // A year-qualified search can reveal better candidates when the first page
            // has only weak title matches or missing metadata.
            val refinedResults = details.seasonYear?.let { year ->
                try {
                    anitubeClient.search("$query $year")
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }
            }.orEmpty()
            val candidates = (results + refinedResults).groupBy { it.newsId }.values.map { hits ->
                hits.maxByOrNull { hit ->
                    listOf(hit.year, hit.airedEpisodes, hit.totalEpisodes, hit.durationMinutes)
                        .count { it != null }
                }!!
            }
            val matches = TitleMatcher.match(details, candidates)
            val choice = TitleMatcher.confidentBest(matches)
            when {
                choice != null -> loadVoices(choice, selection = ReleaseSelection.AUTOMATIC)
                matches.isNotEmpty() -> updateReady {
                    it.copy(videoSource = VideoSourceState.Ambiguous(matches.map { match -> match.result }))
                }
                else -> updateReady {
                    it.copy(videoSource = VideoSourceState.Error("Відповідний реліз не знайдено"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updateReady { it.copy(videoSource = VideoSourceState.Error(e.message ?: "Помилка пошуку відео")) }
        }
    }

    fun resetManualReleaseSearch() {
        manualSearchJob?.cancel()
        updateReady { it.copy(manualReleaseSearch = ManualReleaseSearchState.Idle) }
    }

    fun searchManualRelease(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            updateReady { it.copy(manualReleaseSearch = ManualReleaseSearchState.Error("Введіть українську назву")) }
            return
        }
        manualSearchJob?.cancel()
        updateReady { it.copy(manualReleaseSearch = ManualReleaseSearchState.Loading) }
        manualSearchJob = viewModelScope.launch {
            try {
                val results = anitubeClient.search(trimmed)
                updateReady { it.copy(manualReleaseSearch = ManualReleaseSearchState.Results(results)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateReady { it.copy(manualReleaseSearch = ManualReleaseSearchState.Error(e.message ?: "Помилка пошуку на AniTube")) }
            }
        }
    }

    fun searchToloka(query: String) {
        if ((_state.value as? DetailsState.Ready)?.tolokaSearch == TolokaSearchState.Loading) return
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            updateReady { it.copy(tolokaSearch = TolokaSearchState.Error("Введіть назву для пошуку")) }
            return
        }
        val workerUrl = settingsManager.workerUrl()
        val key = workerKeyStore.read()
        if (workerUrl.isBlank() || key.isNullOrBlank()) {
            updateReady { it.copy(tolokaSearch = TolokaSearchState.Error(
                "Налаштуйте URL і ключ Worker у налаштуваннях застосунку",
            )) }
            return
        }
        tolokaTransferJob?.cancel()
        uploadedTopicId = null
        uploadedTorrent = null
        updateReady { it.copy(tolokaSearch = TolokaSearchState.Loading,
            tolokaTransfer = TolokaTransferState.Idle, tolokaPlaybackStatus = null) }
        tolokaSearchJob = viewModelScope.launch {
            try {
                val results = workerClient.search(workerUrl, key, trimmed)
                updateReady { ready ->
                    val ukrainian = (ready.videoSource as? VideoSourceState.Success)?.release?.title
                    ready.copy(tolokaSearch = TolokaSearchState.Results(trimmed,
                        TolokaHitFilter.filter(ready.details, ukrainian, results.results),
                        results.results.size))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: WorkerFailure.RateLimited) {
                updateReady { it.copy(tolokaSearch = TolokaSearchState.Error(
                    "Зачекайте ${error.waitSeconds} с перед повторним пошуком",
                )) }
            } catch (error: WorkerFailure) {
                updateReady { it.copy(tolokaSearch = TolokaSearchState.Error(error.message.orEmpty())) }
            } catch (_: Exception) {
                updateReady { it.copy(tolokaSearch = TolokaSearchState.Error(
                    "Не вдалося виконати пошук. Спробуйте ще раз",
                )) }
            }
        }
    }

    fun selectTolokaHit(topicId: String) {
        val ready = _state.value as? DetailsState.Ready ?: return
        if (ready.tolokaTransfer is TolokaTransferState.Loading) return
        val results = ready.tolokaSearch as? TolokaSearchState.Results ?: return
        if (results.hits.none { it.id == topicId }) return
        val serverUrl = settingsManager.torrServerUrl()
        val cached = uploadedTorrent?.takeIf { uploadedTopicId == topicId }
        val workerUrl = settingsManager.workerUrl()
        val key = workerKeyStore.read()
        updateReady { it.copy(tolokaSearch = results.copy(selectedId = topicId), tolokaPlaybackStatus = null) }
        if (serverUrl.isBlank() || (cached == null && (workerUrl.isBlank() || key.isNullOrBlank()))) {
            updateReady { it.copy(tolokaTransfer = TolokaTransferState.Error(
                "Налаштуйте Worker і TorrServer у налаштуваннях застосунку",
            )) }
            return
        }
        updateReady { it.copy(tolokaTransfer = TolokaTransferState.Loading(
            if (cached == null) "Завантаження torrent із Worker" else "Читання файлів TorrServer",
            if (cached == null) "Роздачу вибрано" else "Торент додано до TorrServer",
        )) }
        tolokaTransferJob = viewModelScope.launch {
            var lastSuccess = if (cached == null) "Роздачу вибрано" else "Торент додано до TorrServer"
            try {
                val auth = torrServerAuthStore.read()?.let { TorrServerBasicAuth(it.username, it.password) }
                val torrent = if (cached != null) cached else {
                    val torrentBytes = workerClient.torrent(workerUrl, key!!, topicId)
                    lastSuccess = "Torrent отримано з Worker"
                    updateReady { it.copy(tolokaTransfer = TolokaTransferState.Loading(
                        "Передавання torrent у TorrServer", lastSuccess)) }
                    torrServerClient.upload(serverUrl, torrentBytes, auth).also {
                        uploadedTopicId = topicId
                        uploadedTorrent = it
                    }
                }
                lastSuccess = "Торент додано до TorrServer"
                updateReady { it.copy(tolokaTransfer = TolokaTransferState.Loading(
                    "Читання списку файлів TorrServer", lastSuccess)) }
                val withFiles = if (torrent.files.isNotEmpty()) torrent else {
                    var found: TorrServerTorrent? = null
                    for (attempt in 1..30) {
                        delay(1000)
                        val status = try { torrServerClient.get(serverUrl, torrent.hash, auth) }
                        catch (error: TorrServerFailure.Http) {
                            if (error.status == 404) continue else throw error
                        }
                        if (status.hash != torrent.hash) throw TorrServerFailure.InvalidResponse()
                        if (status.files.isNotEmpty()) {
                            found = status
                            break
                        }
                    }
                    found ?: throw TorrServerFailure.FilesUnavailable()
                }
                uploadedTorrent = withFiles
                updateReady { it.copy(tolokaTransfer = TolokaTransferState.Files(withFiles)) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: WorkerFailure.LinkExpired) {
                updateReady { it.copy(tolokaTransfer = TolokaTransferState.Error(
                    "Посилання на torrent протухло або відсутнє. Повторіть пошук Toloka.",
                    lastSuccess = lastSuccess,
                    repeatSearch = true,
                )) }
            } catch (error: WorkerFailure.RateLimited) {
                updateReady { it.copy(tolokaTransfer = TolokaTransferState.Error(
                    "Worker обмежив запити. Повторіть через ${error.waitSeconds} с",
                    lastSuccess = lastSuccess,
                )) }
            } catch (error: WorkerFailure) {
                updateReady { it.copy(tolokaTransfer = TolokaTransferState.Error(error.message.orEmpty(), lastSuccess)) }
            } catch (error: TorrServerFailure) {
                updateReady { it.copy(tolokaTransfer = TolokaTransferState.Error(error.message.orEmpty(), lastSuccess,
                    retryMetadata = uploadedTopicId == topicId)) }
            } catch (_: Exception) {
                updateReady { it.copy(tolokaTransfer = TolokaTransferState.Error(
                    "Помилка TorrServer. Перевірте його доступність і повторіть вручну.",
                    lastSuccess = lastSuccess,
                    retryMetadata = uploadedTopicId == topicId,
                )) }
            }
        }
    }

    fun retryTolokaMetadata() {
        val results = (_state.value as? DetailsState.Ready)?.tolokaSearch as? TolokaSearchState.Results ?: return
        val id = results.selectedId ?: return
        if (uploadedTopicId == id && uploadedTorrent != null) selectTolokaHit(id)
    }

    fun reportTolokaPlaybackStatus(message: String) {
        updateReady { it.copy(tolokaPlaybackStatus = message) }
    }

    fun tolokaPlaybackUrl(fileId: Int): String? {
        val files = ((_state.value as? DetailsState.Ready)?.tolokaTransfer as? TolokaTransferState.Files)
            ?: return null
        val file = files.torrent.videoFiles.firstOrNull { it.id == fileId } ?: return null
        return try { torrServerClient.playbackUrl(settingsManager.torrServerUrl(), files.torrent, file) }
        catch (_: Exception) { null }
    }

    fun selectRelease(candidate: AnitubeSearchResult) {
        val current = _state.value as? DetailsState.Ready ?: return
        val candidates = when (val source = current.videoSource) {
            is VideoSourceState.Ambiguous -> source.candidates
            is VideoSourceState.Error -> source.candidates
            else -> emptyList()
        }
        val manualCandidates = (current.manualReleaseSearch as? ManualReleaseSearchState.Results)?.candidates.orEmpty()
        if (candidate !in candidates && candidate !in manualCandidates) return
        videoSourceJob?.cancel()
        updateReady { it.copy(videoSource = VideoSourceState.Loading) }
        videoSourceJob = viewModelScope.launch {
            try {
                loadVoices(candidate, restoreHistory = false, selection = ReleaseSelection.MANUAL)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateReady { it.copy(videoSource = VideoSourceState.Error(e.message ?: "Помилка завантаження озвучень", candidates)) }
            }
        }
    }

    private suspend fun loadVoices(
        candidate: AnitubeSearchResult,
        restoreHistory: Boolean = true,
        selection: ReleaseSelection? = null,
    ) {
        val format = (_state.value as? DetailsState.Ready)?.details?.format
        val voices = playableVoices(anitubeClient.getVoices(candidate.pageUrl), format, streamResolver)
        if (voices.isEmpty()) {
            updateReady { it.copy(videoSource = VideoSourceState.Error("Для релізу немає доступного відео", listOf(candidate))) }
            return
        }
        if (selection != null) {
            try {
                releaseMappings.put(animeId, candidate, selection)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("DetailsViewModel", "Cannot save release mapping", e)
            }
        }
        currentCoroutineContext().ensureActive()
        VoiceCache.put(animeId, voices)
        updateReady { current ->
            val history = current.historyEntry.takeIf { restoreHistory }
            val voiceIndex = history?.voiceIndex?.takeIf { it in voices.indices } ?: defaultVoiceIndex(voices)
            val episodeIndex = history?.let { saved ->
                voices[voiceIndex].episodes.indexOfFirst { it.episodeNumber == saved.episodeNumber }
                    .takeIf { it >= 0 }
                    ?: saved.episodeIndex.takeIf { it in voices[voiceIndex].episodes.indices }
            } ?: 0
            current.copy(
                videoSource = VideoSourceState.Success(candidate, voices),
                selectedVoiceIndex = voiceIndex,
                selectedEpisodeIndex = episodeIndex,
            )
        }
    }

    /** Refreshes episode availability after playback while keeping the current details visible. */
    fun refreshVoicesAfterPlayback() {
        val current = _state.value as? DetailsState.Ready ?: return
        val release = (current.videoSource as? VideoSourceState.Success)?.release ?: return
        videoSourceJob?.cancel()
        videoSourceJob = viewModelScope.launch {
            try {
                val history = withContext(Dispatchers.IO) { historyManager.getEntry(animeId) }
                val voices = playableVoices(
                    anitubeClient.getVoices(release.pageUrl),
                    current.details.format,
                    streamResolver,
                )
                if (voices.isEmpty()) return@launch
                currentCoroutineContext().ensureActive()
                updateReady { latest ->
                    val previous = latest.videoSource as? VideoSourceState.Success
                    if (previous?.release?.pageUrl != release.pageUrl) return@updateReady latest
                    val selected = previous.voices.getOrNull(latest.selectedVoiceIndex)
                    val voiceIndex = voices.indexOfFirst { voice ->
                        selected != null && voice.name == selected.name &&
                            voice.category == selected.category &&
                            voice.playerName == selected.playerName
                    }.takeIf { it >= 0 } ?: 0
                    val episodeNumber = history?.takeIf { it.voiceIndex == latest.selectedVoiceIndex }
                        ?.episodeNumber
                        ?: selected?.episodes?.getOrNull(latest.selectedEpisodeIndex)?.episodeNumber
                    val episodeIndex = voices[voiceIndex].episodes.indexOfFirst {
                        it.episodeNumber == episodeNumber
                    }.takeIf { it >= 0 } ?: 0
                    VoiceCache.put(animeId, voices)
                    latest.copy(
                        videoSource = VideoSourceState.Success(release, voices),
                        selectedVoiceIndex = voiceIndex,
                        selectedEpisodeIndex = episodeIndex,
                        historyEntry = history,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("DetailsViewModel", "Cannot refresh release after playback", e)
            }
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
        onSuccess: (String, Int, Int, Int) -> Unit,
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
            onError?.invoke("Відео не вибрано")
            return
        }

        val existingProgress = currentState.historyEntry?.takeIf {
            it.episodeNumber == episode.episodeNumber && it.voiceIndex == voiceIndex
        }?.progress ?: 0.0f
        historyManager.saveProgress(currentState.details, episodeIndex, voiceIndex, existingProgress, episode.episodeNumber)
        val updatedHistory = historyManager.getEntry(animeId)
        updateReady { it.copy(resolvingStream = true, historyEntry = updatedHistory) }

        viewModelScope.launch {
            try {
                val extracted = withContext(Dispatchers.IO) { streamResolver.resolve(episode.fileUrl) }
                updateReady { it.copy(resolvingStream = false) }
                onSuccess(extracted.streamUrl, voiceIndex, episodeIndex, episode.episodeNumber)
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
