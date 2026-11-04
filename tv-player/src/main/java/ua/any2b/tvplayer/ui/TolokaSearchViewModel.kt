package ua.any2b.tvplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ua.any2b.core.toloka.TorrServerBasicAuth
import ua.any2b.core.toloka.TorrServerClient
import ua.any2b.core.toloka.TorrServerFailure
import ua.any2b.core.toloka.TorrServerTorrent
import ua.any2b.core.toloka.WorkerFailure
import ua.any2b.core.toloka.TolokaTmdbRanker
import ua.any2b.core.toloka.TolokaHit
import ua.any2b.core.toloka.TolokaSearchPlan
import ua.any2b.core.tmdb.TmdbDetails
import ua.any2b.tvplayer.data.SettingsManager
import ua.any2b.tvplayer.data.TorrServerAuthStore
import ua.any2b.tvplayer.data.TolokaConnections
import ua.any2b.tvplayer.data.TolokaRelevanceStore
import ua.any2b.tvplayer.data.TolokaTmdbMappingStore
import ua.any2b.tvplayer.data.AniListRomajiResolver

data class FreeTolokaState(
    val search: TolokaSearchState = TolokaSearchState.Idle,
    val transfer: TolokaTransferState = TolokaTransferState.Idle,
    val playbackStatus: String? = null,
    val queryOptions: List<Pair<String, String>> = emptyList(),
)

/** Manual Toloka search: no AniList match or release mapping is involved. */
class TolokaSearchViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = SettingsManager(application)
    private val serverAuth = TorrServerAuthStore(application)
    private val relevance = TolokaRelevanceStore(application)
    private val mappings = TolokaTmdbMappingStore(application)
    private val romajiResolver = AniListRomajiResolver(application)
    private val server = TorrServerClient()
    private val _state = MutableStateFlow(FreeTolokaState())
    val state: StateFlow<FreeTolokaState> = _state

    private var searchJob: Job? = null
    private var preparationJob: Job? = null
    private var aniListRomaji: String? = null
    private var activeSearchQuery: String? = null
    private var automaticQuery: String? = null
    private var automaticEnglishTitle: String? = null
    private var automaticDetails: TmdbDetails? = null
    private var currentDetails: TmdbDetails? = null
    private var transferJob: Job? = null
    private var uploadedTopicId: String? = null
    private var uploadedTorrent: TorrServerTorrent? = null

    fun search(query: String) {
        preparationJob?.cancel()
        clearAutomaticSearch()
        currentDetails = null
        searchInternal(query, null, null)
    }

    fun searchForTmdb(details: TmdbDetails, query: String) {
        preparationJob?.cancel()
        clearAutomaticSearch()
        currentDetails = details
        searchInternal(query, null, details)
    }

    fun searchAutomatically(details: TmdbDetails, query: String, englishTitle: String?) {
        currentDetails = details
        automaticEnglishTitle = englishTitle
        automaticDetails = details
        preparationJob?.cancel()
        _state.value = _state.value.copy(search = TolokaSearchState.Loading)
        preparationJob = viewModelScope.launch {
            aniListRomaji = romajiResolver.resolve(details)
            val saved = mappings.aliases(details.title.type, details.title.id)
            val primary = TolokaSearchPlan.primary(aniListRomaji, saved,
                details.title.ukrainianTitle, englishTitle, query)
            automaticQuery = primary.trim()
            _state.value = _state.value.copy(queryOptions =
                listOfNotNull(aniListRomaji?.let { "AniList · ромадзі" to it }) +
                    saved.map { "Підтверджений синонім" to it })
            searchInternal(primary, automaticEnglishTitle, details)
        }
    }

    fun updateAutomaticDetails(details: TmdbDetails) {
        val previous = automaticDetails ?: return
        if (previous.title.type != details.title.type || previous.title.id != details.title.id) return
        automaticDetails = details
        val english = details.title.englishTitle
            ?: details.title.originalTitle?.takeIf { details.title.originalLanguage == "en" }
        if (english.isNullOrBlank() || english.equals(automaticEnglishTitle, ignoreCase = true)) return
        automaticEnglishTitle = english
        val result = _state.value.search as? TolokaSearchState.Results ?: return
        val primary = automaticQuery ?: return
        if (result.hits.isNotEmpty() || english.equals(result.query, ignoreCase = true) ||
            english.equals(primary, ignoreCase = true)) return
        clearAutomaticSearch()
        searchInternal(english, english, details, result.weakHits)
    }

    private fun clearAutomaticSearch() {
        automaticQuery = null
        automaticEnglishTitle = null
        automaticDetails = null
    }

    fun promoteHit(topicId: String) {
        val results = _state.value.search as? TolokaSearchState.Results ?: return
        val hit = results.rejectedHits.firstOrNull { it.id == topicId }
            ?: results.weakHits.firstOrNull { it.id == topicId } ?: return
        val details = currentDetails ?: automaticDetails ?: return
        try {
            mappings.approve(details.title.type, details.title.id, hit.id, hit.title,
                aniListRomaji)
            relevance.approve(details.title.type, details.title.id, hit.title)
        } catch (_: Exception) {
            _state.value = _state.value.copy(playbackStatus =
                "Не вдалося зберегти відповідник. Повторіть підтвердження")
            return
        }
        _state.value = _state.value.copy(queryOptions = _state.value.queryOptions +
            mappings.aliases(details.title.type, details.title.id)
                .map { "Підтверджений синонім" to it })
        _state.value = _state.value.copy(search = results.copy(
            hits = (results.hits + hit).distinctBy { it.id },
            weakHits = results.weakHits.filterNot { it.id == topicId },
            rejectedHits = results.rejectedHits.filterNot { it.id == topicId },
        ))
    }

    /** Move a weak candidate into the reversible discarded bucket without blacklisting it. */
    fun discardWeakHit(topicId: String) {
        val results = _state.value.search as? TolokaSearchState.Results ?: return
        val hit = results.weakHits.firstOrNull { it.id == topicId } ?: return
        if (results.selectedId == topicId) {
            transferJob?.cancel()
            uploadedTopicId = null
            uploadedTorrent = null
        }
        _state.value = _state.value.copy(search = results.copy(
            weakHits = results.weakHits.filterNot { it.id == topicId },
            rejectedHits = (results.rejectedHits + hit).distinctBy { it.id },
            selectedId = results.selectedId.takeUnless { it == topicId },
        ), transfer = if (results.selectedId == topicId) TolokaTransferState.Idle
            else _state.value.transfer,
            playbackStatus = if (results.selectedId == topicId) null else _state.value.playbackStatus)
    }

    fun rejectHit(topicId: String) {
        val results = _state.value.search as? TolokaSearchState.Results ?: return
        val hit = (results.hits + results.weakHits + results.rejectedHits)
            .firstOrNull { it.id == topicId } ?: return
        val details = currentDetails ?: automaticDetails ?: return
        try {
            mappings.reject(details.title.type, details.title.id, hit.id)
            relevance.reject(details.title.type, details.title.id, hit.title)
        } catch (_: Exception) {
            _state.value = _state.value.copy(playbackStatus =
                "Не вдалося прибрати відповідник. Повторіть дію")
            return
        }
        _state.value = _state.value.copy(queryOptions =
            listOfNotNull(aniListRomaji?.let { "AniList · ромадзі" to it }) +
                mappings.aliases(details.title.type, details.title.id)
                    .map { "Підтверджений синонім" to it })
        transferJob?.cancel()
        if (uploadedTopicId == topicId) {
            uploadedTopicId = null
            uploadedTorrent = null
        }
        _state.value = _state.value.copy(search = results.copy(
            hits = results.hits.filterNot { it.id == topicId },
            weakHits = results.weakHits.filterNot { it.id == topicId },
            rejectedHits = results.rejectedHits.filterNot { it.id == topicId },
            selectedId = results.selectedId.takeUnless { it == topicId },
        ), transfer = TolokaTransferState.Idle, playbackStatus = null)
    }

    private fun searchInternal(query: String, englishTitle: String?, details: TmdbDetails?,
        retainedWeak: List<TolokaHit> = emptyList()) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _state.value = _state.value.copy(search = TolokaSearchState.Error("Введіть назву для пошуку"))
            return
        }
        if (!TolokaConnections.configured(getApplication())) {
            _state.value = _state.value.copy(search = TolokaSearchState.Error(
                "Налаштуйте Toloka в розділі «Інтеграції»"))
            return
        }
        if (_state.value.search is TolokaSearchState.Loading && activeSearchQuery == trimmed) return
        searchJob?.cancel()
        transferJob?.cancel()
        uploadedTopicId = null
        uploadedTorrent = null
        activeSearchQuery = trimmed
        _state.value = _state.value.copy(search = TolokaSearchState.Loading,
            transfer = TolokaTransferState.Idle, playbackStatus = null)
        searchJob = viewModelScope.launch {
            try {
                val first = TolokaConnections.search(getApplication(), trimmed)
                val attempts = mutableListOf(first)
                val firstDetails = if (automaticQuery == trimmed) automaticDetails ?: details else details
                val learned = firstDetails?.let {
                    relevance.approvedTitles(it.title.type, it.title.id)
                }.orEmpty()
                val ignored = firstDetails?.let {
                    relevance.rejectedTitles(it.title.type, it.title.id)
                }.orEmpty()
                val aliases = firstDetails?.let {
                    mappings.aliases(it.title.type, it.title.id)
                }.orEmpty()
                val additionalNames = listOfNotNull(aniListRomaji) + aliases
                var ranked = firstDetails?.let { TolokaTmdbRanker.rank(it, first.results,
                    learned, ignored, additionalNames) }
                val availableEnglish = if (automaticQuery == trimmed)
                    automaticEnglishTitle ?: englishTitle else englishTitle
                var searchedQuery = trimmed
                val fallbackQueries = TolokaSearchPlan.fallbacks(trimmed, aniListRomaji,
                    aliases, firstDetails?.title?.ukrainianTitle, availableEnglish)
                for (fallback in fallbackQueries) {
                    if (ranked?.visible?.isNotEmpty() == true) break
                    val response = TolokaConnections.search(getApplication(), fallback)
                    attempts += response
                    searchedQuery = fallback
                    val currentDetails = if (automaticQuery == trimmed)
                        automaticDetails ?: details else details
                    ranked = currentDetails?.let { TolokaTmdbRanker.rank(it, response.results,
                        learned, ignored, additionalNames) }
                }
                val latestDetails = if (automaticQuery == trimmed) automaticDetails ?: details else details
                val allHits = attempts.flatMap { it.results }.distinctBy { it.id }
                val aggregate = latestDetails?.let { TolokaTmdbRanker.rank(it, allHits,
                    learned, ignored, additionalNames) }
                val hits = aggregate?.visible ?: allHits.filter(TolokaTmdbRanker::isVideoCandidate)
                val weak = (retainedWeak + aggregate?.weak.orEmpty()).distinctBy { it.id }
                    .filterNot { hit -> hits.any { it.id == hit.id } }
                val rejected = aggregate?.rejected ?: allHits.filterNot(TolokaTmdbRanker::isVideoCandidate)
                val totalFound = allHits.size
                _state.value = _state.value.copy(
                    search = TolokaSearchState.Results(searchedQuery, hits,
                        maxOf(totalFound, hits.size + weak.size + rejected.size),
                        weakHits = weak, rejectedHits = rejected.filterNot { hit ->
                            hits.any { it.id == hit.id } || weak.any { it.id == hit.id }
                        }))
            } catch (error: CancellationException) {
                throw error
            } catch (error: WorkerFailure.RateLimited) {
                _state.value = _state.value.copy(search = TolokaSearchState.Error(
                    "Зачекайте ${error.waitSeconds} с перед повторним пошуком"))
            } catch (error: WorkerFailure) {
                _state.value = _state.value.copy(search = TolokaSearchState.Error(error.message.orEmpty()))
            } catch (_: Exception) {
                _state.value = _state.value.copy(search = TolokaSearchState.Error(
                    "Не вдалося виконати пошук. Спробуйте ще раз"))
            }
        }
    }

    fun selectHit(topicId: String) {
        if (_state.value.transfer is TolokaTransferState.Loading) return
        val results = _state.value.search as? TolokaSearchState.Results ?: return
        if ((results.hits + results.weakHits + results.rejectedHits).none { it.id == topicId }) return
        val serverUrl = settings.torrServerUrl()
        val cached = uploadedTorrent?.takeIf { uploadedTopicId == topicId }
        _state.value = _state.value.copy(
            search = results.copy(selectedId = topicId), playbackStatus = null)
        if (serverUrl.isBlank() || (cached == null && !TolokaConnections.configured(getApplication()))) {
            _state.value = _state.value.copy(transfer = TolokaTransferState.Error(
                "Налаштуйте Toloka і TorrServer у розділі «Інтеграції»"))
            return
        }
        _state.value = _state.value.copy(transfer = TolokaTransferState.Loading(
            if (cached == null) "Завантаження torrent із Toloka" else "Читання файлів TorrServer",
            if (cached == null) "Роздачу вибрано" else "Торент додано до TorrServer"))
        transferJob = viewModelScope.launch {
            var lastSuccess = if (cached == null) "Роздачу вибрано" else "Торент додано до TorrServer"
            try {
                val auth = serverAuth.read()?.let { TorrServerBasicAuth(it.username, it.password) }
                val torrent = cached ?: run {
                    val bytes = TolokaConnections.torrent(getApplication(), topicId)
                    lastSuccess = "Torrent отримано з Toloka"
                    _state.value = _state.value.copy(transfer = TolokaTransferState.Loading(
                        "Передавання torrent у TorrServer", lastSuccess))
                    server.upload(serverUrl, bytes, auth).also {
                        uploadedTopicId = topicId
                        uploadedTorrent = it
                    }
                }
                lastSuccess = "Торент додано до TorrServer"
                _state.value = _state.value.copy(transfer = TolokaTransferState.Loading(
                    "Читання списку файлів TorrServer", lastSuccess))
                val withFiles = if (torrent.files.isNotEmpty()) torrent else {
                    var found: TorrServerTorrent? = null
                    for (attempt in 1..30) {
                        delay(1000)
                        val status = try { server.get(serverUrl, torrent.hash, auth) }
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
                _state.value = _state.value.copy(transfer = TolokaTransferState.Files(withFiles))
            } catch (error: CancellationException) {
                throw error
            } catch (_: WorkerFailure.LinkExpired) {
                _state.value = _state.value.copy(transfer = TolokaTransferState.Error(
                    "Посилання на torrent протухло або відсутнє. Повторіть пошук Toloka.",
                    lastSuccess = lastSuccess, repeatSearch = true))
            } catch (error: WorkerFailure.RateLimited) {
                _state.value = _state.value.copy(transfer = TolokaTransferState.Error(
                    "Toloka обмежила запити. Повторіть через ${error.waitSeconds} с", lastSuccess))
            } catch (error: WorkerFailure) {
                _state.value = _state.value.copy(transfer = TolokaTransferState.Error(
                    error.message.orEmpty(), lastSuccess))
            } catch (error: TorrServerFailure) {
                _state.value = _state.value.copy(transfer = TolokaTransferState.Error(
                    error.message.orEmpty(), lastSuccess,
                    retryMetadata = uploadedTopicId == topicId))
            } catch (_: Exception) {
                _state.value = _state.value.copy(transfer = TolokaTransferState.Error(
                    "Помилка TorrServer. Перевірте його доступність і повторіть вручну.",
                    lastSuccess, retryMetadata = uploadedTopicId == topicId))
            }
        }
    }

    fun retryMetadata() {
        val selected = (_state.value.search as? TolokaSearchState.Results)?.selectedId ?: return
        if (uploadedTopicId == selected && uploadedTorrent != null) selectHit(selected)
    }

    fun playbackUrl(fileId: Int): String? {
        val files = _state.value.transfer as? TolokaTransferState.Files ?: return null
        val file = files.torrent.videoFiles.firstOrNull { it.id == fileId } ?: return null
        return try { server.playbackUrl(settings.torrServerUrl(), files.torrent, file) }
        catch (_: Exception) { null }
    }

    fun reportPlaybackStatus(message: String) {
        _state.value = _state.value.copy(playbackStatus = message)
    }
}
