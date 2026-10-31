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
import ua.any2b.core.toloka.shortenedSearchNames
import ua.any2b.core.tmdb.TmdbDetails
import ua.any2b.tvplayer.data.SettingsManager
import ua.any2b.tvplayer.data.TorrServerAuthStore
import ua.any2b.tvplayer.data.TolokaConnections

data class FreeTolokaState(
    val search: TolokaSearchState = TolokaSearchState.Idle,
    val transfer: TolokaTransferState = TolokaTransferState.Idle,
    val playbackStatus: String? = null,
)

/** Manual Toloka search: no AniList match or release mapping is involved. */
class TolokaSearchViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = SettingsManager(application)
    private val serverAuth = TorrServerAuthStore(application)
    private val server = TorrServerClient()
    private val _state = MutableStateFlow(FreeTolokaState())
    val state: StateFlow<FreeTolokaState> = _state

    private var searchJob: Job? = null
    private var activeSearchQuery: String? = null
    private var automaticQuery: String? = null
    private var automaticEnglishTitle: String? = null
    private var automaticDetails: TmdbDetails? = null
    private var transferJob: Job? = null
    private var uploadedTopicId: String? = null
    private var uploadedTorrent: TorrServerTorrent? = null

    fun search(query: String) {
        clearAutomaticSearch()
        searchInternal(query, null, null)
    }

    fun searchForTmdb(details: TmdbDetails, query: String) {
        clearAutomaticSearch()
        searchInternal(query, null, details)
    }

    fun searchAutomatically(details: TmdbDetails, query: String, englishTitle: String?) {
        automaticQuery = query.trim()
        automaticEnglishTitle = englishTitle
        automaticDetails = details
        searchInternal(query, englishTitle, details)
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
        _state.value = FreeTolokaState(search = TolokaSearchState.Loading)
        searchJob = viewModelScope.launch {
            try {
                val first = TolokaConnections.search(getApplication(), trimmed)
                val attempts = mutableListOf(first)
                val firstDetails = if (automaticQuery == trimmed) automaticDetails ?: details else details
                var ranked = firstDetails?.let { TolokaTmdbRanker.rank(it, first.results) }
                val availableEnglish = if (automaticQuery == trimmed)
                    automaticEnglishTitle ?: englishTitle else englishTitle
                var searchedQuery = trimmed
                val fallbackQueries = availableEnglish?.trim()?.takeIf(String::isNotBlank)
                    ?.let { listOf(it) + shortenedSearchNames(it) }.orEmpty()
                    .distinctBy { it.lowercase() }
                    .filterNot { it.equals(trimmed, ignoreCase = true) }
                for (fallback in fallbackQueries) {
                    if (ranked?.visible?.isNotEmpty() == true) break
                    val response = TolokaConnections.search(getApplication(), fallback)
                    attempts += response
                    searchedQuery = fallback
                    val currentDetails = if (automaticQuery == trimmed)
                        automaticDetails ?: details else details
                    ranked = currentDetails?.let { TolokaTmdbRanker.rank(it, response.results) }
                }
                val latestDetails = if (automaticQuery == trimmed) automaticDetails ?: details else details
                val selected = attempts.last()
                val hits = ranked?.visible ?: selected.results.filter(TolokaTmdbRanker::isVideoCandidate)
                val weak = (retainedWeak + attempts.flatMap { attempt ->
                    latestDetails?.let { TolokaTmdbRanker.rank(it, attempt.results).weak }.orEmpty()
                }).distinctBy { it.id }.filterNot { hit -> hits.any { it.id == hit.id } }
                val totalFound = attempts.flatMap { it.results }.distinctBy { it.id }.size
                _state.value = _state.value.copy(
                    search = TolokaSearchState.Results(searchedQuery, hits,
                        maxOf(totalFound, hits.size + weak.size), weakHits = weak))
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
        if ((results.hits + results.weakHits).none { it.id == topicId }) return
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
