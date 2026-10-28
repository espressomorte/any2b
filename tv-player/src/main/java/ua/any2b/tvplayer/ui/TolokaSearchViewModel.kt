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
import ua.any2b.tvplayer.data.SettingsManager
import ua.any2b.tvplayer.data.TorrServerAuthStore
import ua.any2b.tvplayer.data.WorkerConnections
import ua.any2b.tvplayer.data.WorkerKeyStore

data class FreeTolokaState(
    val search: TolokaSearchState = TolokaSearchState.Idle,
    val transfer: TolokaTransferState = TolokaTransferState.Idle,
    val playbackStatus: String? = null,
)

/** Manual Toloka search: no AniList match or release mapping is involved. */
class TolokaSearchViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = SettingsManager(application)
    private val workerKeys = WorkerKeyStore(application)
    private val serverAuth = TorrServerAuthStore(application)
    private val worker = WorkerConnections.client
    private val server = TorrServerClient()
    private val _state = MutableStateFlow(FreeTolokaState())
    val state: StateFlow<FreeTolokaState> = _state

    private var searchJob: Job? = null
    private var activeSearchQuery: String? = null
    private var transferJob: Job? = null
    private var uploadedTopicId: String? = null
    private var uploadedTorrent: TorrServerTorrent? = null

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _state.value = _state.value.copy(search = TolokaSearchState.Error("Введіть назву для пошуку"))
            return
        }
        val workerUrl = settings.workerUrl()
        val key = workerKeys.read()
        if (workerUrl.isBlank() || key.isNullOrBlank()) {
            _state.value = _state.value.copy(search = TolokaSearchState.Error(
                "Налаштуйте URL і ключ Worker у налаштуваннях застосунку"))
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
                val hits = worker.search(workerUrl, key, trimmed).results
                _state.value = _state.value.copy(
                    search = TolokaSearchState.Results(trimmed, hits, hits.size))
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
        if (results.hits.none { it.id == topicId }) return
        val serverUrl = settings.torrServerUrl()
        val cached = uploadedTorrent?.takeIf { uploadedTopicId == topicId }
        val workerUrl = settings.workerUrl()
        val key = workerKeys.read()
        _state.value = _state.value.copy(
            search = results.copy(selectedId = topicId), playbackStatus = null)
        if (serverUrl.isBlank() || (cached == null && (workerUrl.isBlank() || key.isNullOrBlank()))) {
            _state.value = _state.value.copy(transfer = TolokaTransferState.Error(
                "Налаштуйте Worker і TorrServer у налаштуваннях застосунку"))
            return
        }
        _state.value = _state.value.copy(transfer = TolokaTransferState.Loading(
            if (cached == null) "Завантаження torrent із Worker" else "Читання файлів TorrServer",
            if (cached == null) "Роздачу вибрано" else "Торент додано до TorrServer"))
        transferJob = viewModelScope.launch {
            var lastSuccess = if (cached == null) "Роздачу вибрано" else "Торент додано до TorrServer"
            try {
                val auth = serverAuth.read()?.let { TorrServerBasicAuth(it.username, it.password) }
                val torrent = cached ?: run {
                    val bytes = worker.torrent(workerUrl, key!!, topicId)
                    lastSuccess = "Torrent отримано з Worker"
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
                    "Worker обмежив запити. Повторіть через ${error.waitSeconds} с", lastSuccess))
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
