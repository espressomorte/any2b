package ua.any2b.tvplayer

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.any2b.core.update.AppRelease
import ua.any2b.core.update.GitHubReleaseClient
import ua.any2b.core.update.compareVersions
import ua.any2b.core.fanart.FanartClient
import ua.any2b.core.toloka.WorkerFailure
import ua.any2b.core.toloka.TorrServerProbe
import ua.any2b.core.toloka.TorrServerBasicAuth
import ua.any2b.tvplayer.data.SettingsManager
import ua.any2b.tvplayer.data.HomeSectionSetting
import ua.any2b.tvplayer.data.ExternalVideoPlayer
import ua.any2b.tvplayer.data.ExternalVideoPlayers
import ua.any2b.tvplayer.data.WatchHistoryManager
import ua.any2b.tvplayer.data.WorkerKeyStore
import ua.any2b.tvplayer.data.FanartKeyStore
import ua.any2b.tvplayer.data.TmdbKeyStore
import ua.any2b.tvplayer.data.TolokaCredentialStore
import ua.any2b.tvplayer.data.TolokaConnections
import ua.any2b.tvplayer.data.EncryptedConfigSource
import ua.any2b.tvplayer.data.WorkerConnections
import ua.any2b.tvplayer.data.TorrServerAuthStore
import ua.any2b.tvplayer.data.TolokaPlaybackStore
import ua.any2b.tvplayer.data.TmdbWatchHistoryStore
import ua.any2b.tvplayer.ui.AppTheme
import ua.any2b.tvplayer.ui.SettingsScreen

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object NoReleases : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class MissingApk(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val progress: Int?) : UpdateState
    data class ReadyToInstall(val release: AppRelease, val uri: Uri) : UpdateState
    data class Error(val message: String) : UpdateState
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsManager = SettingsManager(application)
    private val externalPlayers = ExternalVideoPlayers(application)
    private val watchHistory = WatchHistoryManager(application)
    private val releaseClient = GitHubReleaseClient()
    private val downloads = application.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val updatePrefs = application.getSharedPreferences("update_download", Context.MODE_PRIVATE)
    private val workerKeyStore = WorkerKeyStore(application)
    private val fanartKeyStore = FanartKeyStore(application)
    private val tmdbKeyStore = TmdbKeyStore(application)
    private val tolokaCredentialStore = TolokaCredentialStore(application)
    private val encryptedConfigSource = EncryptedConfigSource(application)
    private val fanartClient = FanartClient()
    private val workerClient = WorkerConnections.client
    private val torrServerProbe = TorrServerProbe()
    private val torrServerAuthStore = TorrServerAuthStore(application)

    private val _workerUrl = MutableStateFlow(settingsManager.workerUrl())
    val workerUrl: StateFlow<String> = _workerUrl
    private val _torrServerUrl = MutableStateFlow(settingsManager.torrServerUrl())
    val torrServerUrl: StateFlow<String> = _torrServerUrl
    private val _torrServerAuthConfigured = MutableStateFlow(torrServerAuthStore.read() != null)
    val torrServerAuthConfigured: StateFlow<Boolean> = _torrServerAuthConfigured
    private val _workerKeyConfigured = MutableStateFlow(workerKeyStore.read() != null)
    val workerKeyConfigured: StateFlow<Boolean> = _workerKeyConfigured
    private val _fanartKeyConfigured = MutableStateFlow(fanartKeyStore.read() != null)
    val fanartKeyConfigured: StateFlow<Boolean> = _fanartKeyConfigured
    private val _tmdbTokenConfigured = MutableStateFlow(tmdbKeyStore.read() != null)
    val tmdbTokenConfigured: StateFlow<Boolean> = _tmdbTokenConfigured
    private val _fanartKeyError = MutableStateFlow("")
    val fanartKeyError: StateFlow<String> = _fanartKeyError
    private val _fanartCheck = MutableStateFlow("")
    val fanartCheck: StateFlow<String> = _fanartCheck
    private val _encryptedConfigEnabled = MutableStateFlow(settingsManager.isEncryptedConfigEnabled())
    val encryptedConfigEnabled: StateFlow<Boolean> = _encryptedConfigEnabled
    private val _encryptedConfigStatus = MutableStateFlow("")
    val encryptedConfigStatus: StateFlow<String> = _encryptedConfigStatus
    private val _encryptedConfigUrl = MutableStateFlow(settingsManager.encryptedConfigUrl())
    val encryptedConfigUrl: StateFlow<String> = _encryptedConfigUrl
    private var applyingEncryptedConfig = false
    private val _workerCheck = MutableStateFlow("")
    val workerCheck: StateFlow<String> = _workerCheck
    private val _torrServerCheck = MutableStateFlow("")
    val torrServerCheck: StateFlow<String> = _torrServerCheck
    private var checkingWorker = false
    private var checkingTorrServer = false
    private var checkingFanart = false

    private val _autoPlayNext = MutableStateFlow(settingsManager.isAutoPlayNextEnabled())
    val autoPlayNext: StateFlow<Boolean> = _autoPlayNext
    private val _experimentalAppearance = MutableStateFlow(settingsManager.isExperimentalAppearanceEnabled())
    val experimentalAppearance: StateFlow<Boolean> = _experimentalAppearance
    private val _homeSections = MutableStateFlow(settingsManager.homeSections())
    val homeSections: StateFlow<List<HomeSectionSetting>> = _homeSections
    private val _mainHomeSections = MutableStateFlow(settingsManager.mainHomeSections())
    val mainHomeSections: StateFlow<List<HomeSectionSetting>> = _mainHomeSections
    private val _availablePlayers = MutableStateFlow<List<ExternalVideoPlayer>>(emptyList())
    val availablePlayers: StateFlow<List<ExternalVideoPlayer>> = _availablePlayers
    private val _selectedPlayerPackage = MutableStateFlow(settingsManager.selectedPlayerPackage())
    val selectedPlayerPackage: StateFlow<String?> = _selectedPlayerPackage
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState

    val currentVersion: String = application.packageManager
        .getPackageInfo(application.packageName, 0).versionName.orEmpty()

    init {
        val id = updatePrefs.getLong("id", -1L)
        val tag = updatePrefs.getString("tag", null)
        val pageUrl = updatePrefs.getString("pageUrl", null)
        if (id > 0 && tag != null && pageUrl != null) {
            if (compareVersions(tag, currentVersion) > 0) {
                monitorDownload(id, AppRelease(tag, pageUrl, null))
            } else {
                clearSavedDownload()
            }
        }
    }

    fun setAutoPlayNext(enabled: Boolean) {
        settingsManager.setAutoPlayNextEnabled(enabled)
        _autoPlayNext.value = enabled
    }

    fun setExperimentalAppearance(enabled: Boolean) {
        settingsManager.setExperimentalAppearanceEnabled(enabled)
        _experimentalAppearance.value = enabled
    }

    fun setEncryptedConfigEnabled(enabled: Boolean) {
        if (enabled) return
        settingsManager.setEncryptedConfigEnabled(false)
        _encryptedConfigEnabled.value = false
        _encryptedConfigStatus.value = "Автоматичний конфіг вимкнено. Введені значення залишилися в налаштуваннях."
    }

    fun setEncryptedConfigUrl(value: String) {
        settingsManager.setEncryptedConfigUrl(value)
        _encryptedConfigUrl.value = settingsManager.encryptedConfigUrl()
        if (_encryptedConfigEnabled.value) setEncryptedConfigEnabled(false)
        _encryptedConfigStatus.value = "Адресу конфіга оновлено. Введіть пароль для нового файла."
    }

    fun applyEncryptedConfig(password: String) {
        if (applyingEncryptedConfig) return
        if (password.isEmpty()) {
            _encryptedConfigStatus.value = "Введіть пароль конфіга"
            return
        }
        applyingEncryptedConfig = true
        _encryptedConfigStatus.value = "Завантажуємо та розшифровуємо конфіг…"
        val chars = password.toCharArray()
        viewModelScope.launch {
            try {
                val config = encryptedConfigSource.load(chars)
                withContext(Dispatchers.Main) {
                    require(!config.tmdbToken.isNullOrBlank() &&
                        !config.tolokaUser.isNullOrBlank() &&
                        !config.tolokaPassword.isNullOrBlank()) {
                        "Для автономного режиму потрібні токен TMDB і логін/пароль Toloka у конфігу"
                    }
                    config.workerUrl?.let(::setWorkerUrl)
                    config.workerKey?.let(::setWorkerKey)
                    config.torrServerUrl?.let(::setTorrServerUrl)
                    val torrUser = config.torrServerUser
                    val torrPassword = config.torrServerPassword
                    if (!torrUser.isNullOrBlank() && !torrPassword.isNullOrBlank())
                        torrServerAuthStore.write(torrUser, torrPassword)
                    _torrServerAuthConfigured.value = torrServerAuthStore.read() != null
                    config.fanartKey?.let(::setFanartKey)
                    config.tmdbToken?.let(tmdbKeyStore::write)
                    val tolokaUser = config.tolokaUser
                    val tolokaPassword = config.tolokaPassword
                    if (!tolokaUser.isNullOrBlank() && !tolokaPassword.isNullOrBlank())
                        tolokaCredentialStore.write(tolokaUser, tolokaPassword)
                    _tmdbTokenConfigured.value = tmdbKeyStore.read() != null
                    settingsManager.setEncryptedConfigEnabled(true)
                    _encryptedConfigEnabled.value = true
                    _encryptedConfigStatus.value = "Конфіг застосовано. Доступні поля заповнено."
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: IllegalArgumentException) {
                _encryptedConfigStatus.value = error.message ?: "Невірний пароль або пошкоджений конфіг"
            } catch (_: Exception) {
                _encryptedConfigStatus.value = "Не вдалося завантажити або зберегти конфіг. Повторіть спробу."
            } finally {
                chars.fill('\u0000')
                applyingEncryptedConfig = false
            }
        }
    }

    fun setFanartKey(value: String) {
        try {
            fanartKeyStore.write(value.trim())
            _fanartKeyConfigured.value = fanartKeyStore.read() != null
            _fanartKeyError.value = ""
            _fanartCheck.value = ""
        } catch (_: Exception) {
            _fanartKeyError.value = "Не вдалося захищено зберегти ключ fanart.tv"
        }
    }

    fun setTmdbToken(value: String) {
        try {
            tmdbKeyStore.write(value.trim())
            _tmdbTokenConfigured.value = tmdbKeyStore.read() != null
        } catch (_: Exception) {
            _encryptedConfigStatus.value = "Не вдалося захищено зберегти токен TMDB"
        }
    }

    fun checkFanartKey() {
        if (checkingFanart) return
        val key = fanartKeyStore.read()
        if (key.isNullOrBlank()) {
            _fanartCheck.value = "Спочатку введіть особистий ключ fanart.tv"
            return
        }
        checkingFanart = true
        _fanartCheck.value = "Перевіряємо ключ fanart.tv…"
        viewModelScope.launch {
            try {
                _fanartCheck.value = when (val code = fanartClient.checkKey(key)) {
                    200 -> "Ключ fanart.tv прийнято"
                    401 -> "Ключ fanart.tv не прийнято. Перевірте введене значення"
                    429 -> "fanart.tv обмежив запити. Повторіть перевірку пізніше"
                    else -> "fanart.tv повернув HTTP $code. Повторіть перевірку пізніше"
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _fanartCheck.value = "Не вдалося зв’язатися з fanart.tv. Перевірте мережу"
            } finally {
                checkingFanart = false
            }
        }
    }

    fun setWorkerUrl(value: String) {
        settingsManager.setWorkerUrl(value)
        _workerUrl.value = settingsManager.workerUrl()
        _workerCheck.value = ""
    }

    fun setTorrServerUrl(value: String) {
        settingsManager.setTorrServerUrl(value)
        _torrServerUrl.value = settingsManager.torrServerUrl()
        _torrServerCheck.value = ""
    }

    fun setWorkerKey(value: String) {
        try {
            workerKeyStore.write(value.trim())
            _workerKeyConfigured.value = workerKeyStore.read() != null
            _workerCheck.value = ""
        } catch (_: Exception) {
            _workerCheck.value = "Не вдалося захищено зберегти ключ Worker"
        }
    }

    fun setTorrServerAuth(value: String) {
        val text = value.trim()
        if (text.isNotEmpty() && (!text.contains(':') || text.substringBefore(':').isBlank() ||
                    text.substringAfter(':').isBlank())) {
            _torrServerCheck.value = "Вкажіть дані у форматі користувач:пароль"
            return
        }
        try {
            torrServerAuthStore.write(text.substringBefore(':'), text.substringAfter(':', ""))
            _torrServerAuthConfigured.value = torrServerAuthStore.read() != null
            _torrServerCheck.value = ""
        } catch (_: Exception) {
            _torrServerCheck.value = "Не вдалося захищено зберегти дані TorrServer"
        }
    }

    fun checkWorker() {
        if (checkingWorker) return
        checkingWorker = true
        _workerCheck.value = if (_encryptedConfigEnabled.value) "Перевіряємо Toloka…" else "Перевіряємо Worker…"
        viewModelScope.launch {
            try {
                if (_encryptedConfigEnabled.value) {
                    TolokaConnections.checkDirect(getApplication())
                    _workerCheck.value = "Toloka доступна · вхід підтверджено"
                } else {
                    val key = workerKeyStore.read() ?: throw IllegalArgumentException("Вкажіть ключ Worker")
                    val status = workerClient.status(_workerUrl.value, key)
                    _workerCheck.value = "Worker доступний · KV: ${if (status.kvConfigured) "так" else "ні"} · сесія: ${if (status.sessionStored) "так" else "ні"}"
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: WorkerFailure) {
                _workerCheck.value = if (error is WorkerFailure.RateLimited)
                    "Toloka обмежила запити. Повторіть через ${error.waitSeconds} с"
                else error.message.orEmpty()
            } catch (error: IllegalArgumentException) {
                _workerCheck.value = error.message.orEmpty()
            } catch (_: Exception) {
                _workerCheck.value = "Не вдалося перевірити підключення. Повторіть вручну"
            } finally {
                checkingWorker = false
            }
        }
    }

    fun checkTorrServer() {
        if (checkingTorrServer) return
        checkingTorrServer = true
        _torrServerCheck.value = "Перевіряємо TorrServer…"
        viewModelScope.launch {
            try {
                val auth = torrServerAuthStore.read()?.let { TorrServerBasicAuth(it.username, it.password) }
                val version = torrServerProbe.version(_torrServerUrl.value, auth)
                _torrServerCheck.value = "TorrServer доступний · версія $version"
            } catch (error: CancellationException) {
                throw error
            } catch (error: IllegalArgumentException) {
                _torrServerCheck.value = error.message.orEmpty()
            } catch (error: IllegalStateException) {
                _torrServerCheck.value = error.message.orEmpty()
            } catch (_: Exception) {
                _torrServerCheck.value = "Не вдалося з’єднатися з TorrServer. Перевірте адресу й мережу"
            } finally {
                checkingTorrServer = false
            }
        }
    }

    fun setHomeSectionEnabled(id: String, enabled: Boolean) {
        _homeSections.value = settingsManager.setHomeSectionEnabled(id, enabled)
    }

    fun moveHomeSection(id: String, offset: Int) {
        _homeSections.value = settingsManager.moveHomeSection(id, offset)
    }

    fun setMainHomeSectionEnabled(id: String, enabled: Boolean) {
        _mainHomeSections.value = settingsManager.setMainHomeSectionEnabled(id, enabled)
    }

    fun moveMainHomeSection(id: String, offset: Int) {
        _mainHomeSections.value = settingsManager.moveMainHomeSection(id, offset)
    }

    fun clearHistory() {
        watchHistory.clearHistory()
        TmdbWatchHistoryStore(getApplication()).clear()
        TolokaPlaybackStore(getApplication()).clear()
    }

    fun refreshPlayers() {
        val players = externalPlayers.available()
        _availablePlayers.value = players
        val selected = _selectedPlayerPackage.value
        if (selected != null && players.none { it.packageName == selected }) {
            setSelectedPlayer(null)
        }
    }

    fun setSelectedPlayer(packageName: String?) {
        if (packageName != null && _availablePlayers.value.none { it.packageName == packageName }) return
        settingsManager.setSelectedPlayerPackage(packageName)
        _selectedPlayerPackage.value = packageName
    }

    fun checkForUpdates() {
        if (_updateState.value is UpdateState.Checking || _updateState.value is UpdateState.Downloading) return
        _updateState.value = UpdateState.Checking
        viewModelScope.launch {
            _updateState.value = try {
                val release = releaseClient.newestRelease()
                when {
                    release == null -> UpdateState.NoReleases
                    compareVersions(release.tag, currentVersion) <= 0 -> UpdateState.UpToDate
                    release.apkUrl == null -> UpdateState.MissingApk(release)
                    else -> UpdateState.Available(release)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                UpdateState.Error(error.message ?: "Не вдалося перевірити оновлення")
            }
        }
    }

    fun downloadUpdate(release: AppRelease) {
        val apkUrl = release.apkUrl ?: return
        if (_updateState.value is UpdateState.Downloading) return
        try {
            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("Animarium ${release.tag}")
                .setDescription("Завантаження оновлення")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            val id = downloads.enqueue(request)
            updatePrefs.edit().putLong("id", id).putString("tag", release.tag)
                .putString("pageUrl", release.pageUrl).apply()
            monitorDownload(id, release)
        } catch (error: Exception) {
            _updateState.value = UpdateState.Error(error.message ?: "Не вдалося почати завантаження")
        }
    }

    private fun monitorDownload(id: Long, release: AppRelease) {
        _updateState.value = UpdateState.Downloading(release, null)
        viewModelScope.launch {
            try {
                while (true) {
                    val status = withContext(Dispatchers.IO) {
                        downloads.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                            if (!cursor.moveToFirst()) return@use null
                            val state = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            val completed = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            state to if (total > 0) ((completed * 100) / total).toInt().coerceIn(0, 100) else null
                        }
                    }
                    when (status?.first) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            val uri = downloads.getUriForDownloadedFile(id)
                            _updateState.value = if (uri != null) UpdateState.ReadyToInstall(release, uri)
                            else UpdateState.Error("Завантажений APK недоступний")
                            return@launch
                        }
                        DownloadManager.STATUS_FAILED, null -> {
                            clearSavedDownload()
                            _updateState.value = UpdateState.Error("Не вдалося завантажити APK")
                            return@launch
                        }
                        else -> _updateState.value = UpdateState.Downloading(release, status.second)
                    }
                    delay(1_000)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _updateState.value = UpdateState.Error(error.message ?: "Не вдалося перевірити завантаження")
            }
        }
    }

    fun showUpdateError(message: String) {
        _updateState.value = UpdateState.Error(message)
    }

    private fun clearSavedDownload() {
        updatePrefs.edit().clear().apply()
    }
}

class SettingsActivity : ComponentActivity() {
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                val autoPlay = viewModel.autoPlayNext.collectAsStateWithLifecycle().value
                val homeSections = viewModel.homeSections.collectAsStateWithLifecycle().value
                val mainHomeSections = viewModel.mainHomeSections.collectAsStateWithLifecycle().value
                val availablePlayers = viewModel.availablePlayers.collectAsStateWithLifecycle().value
                val selectedPlayerPackage = viewModel.selectedPlayerPackage.collectAsStateWithLifecycle().value
                val updateState = viewModel.updateState.collectAsStateWithLifecycle().value
                val workerUrl = viewModel.workerUrl.collectAsStateWithLifecycle().value
                val torrServerUrl = viewModel.torrServerUrl.collectAsStateWithLifecycle().value
                val torrServerAuthConfigured = viewModel.torrServerAuthConfigured.collectAsStateWithLifecycle().value
                val workerKeyConfigured = viewModel.workerKeyConfigured.collectAsStateWithLifecycle().value
                val fanartKeyConfigured = viewModel.fanartKeyConfigured.collectAsStateWithLifecycle().value
                val tmdbTokenConfigured = viewModel.tmdbTokenConfigured.collectAsStateWithLifecycle().value
                val fanartKeyError = viewModel.fanartKeyError.collectAsStateWithLifecycle().value
                val fanartCheck = viewModel.fanartCheck.collectAsStateWithLifecycle().value
                val encryptedConfigEnabled = viewModel.encryptedConfigEnabled.collectAsStateWithLifecycle().value
                val encryptedConfigStatus = viewModel.encryptedConfigStatus.collectAsStateWithLifecycle().value
                val encryptedConfigUrl = viewModel.encryptedConfigUrl.collectAsStateWithLifecycle().value
                val experimentalAppearance = viewModel.experimentalAppearance.collectAsStateWithLifecycle().value
                val workerCheck = viewModel.workerCheck.collectAsStateWithLifecycle().value
                val torrServerCheck = viewModel.torrServerCheck.collectAsStateWithLifecycle().value
                SettingsScreen(
                    autoPlayNext = autoPlay,
                    homeSections = homeSections,
                    mainHomeSections = mainHomeSections,
                    availablePlayers = availablePlayers,
                    selectedPlayerPackage = selectedPlayerPackage,
                    currentVersion = viewModel.currentVersion,
                    updateState = updateState,
                    workerUrl = workerUrl,
                    torrServerUrl = torrServerUrl,
                    torrServerAuthConfigured = torrServerAuthConfigured,
                    workerKeyConfigured = workerKeyConfigured,
                    fanartKeyConfigured = fanartKeyConfigured,
                    tmdbTokenConfigured = tmdbTokenConfigured,
                    fanartKeyError = fanartKeyError,
                    fanartCheck = fanartCheck,
                    encryptedConfigEnabled = encryptedConfigEnabled,
                    encryptedConfigStatus = encryptedConfigStatus,
                    encryptedConfigUrl = encryptedConfigUrl,
                    experimentalAppearance = experimentalAppearance,
                    workerCheck = workerCheck,
                    torrServerCheck = torrServerCheck,
                    onWorkerUrlChanged = viewModel::setWorkerUrl,
                    onWorkerKeyChanged = viewModel::setWorkerKey,
                    onTorrServerUrlChanged = viewModel::setTorrServerUrl,
                    onTorrServerAuthChanged = viewModel::setTorrServerAuth,
                    onCheckWorker = viewModel::checkWorker,
                    onCheckTorrServer = viewModel::checkTorrServer,
                    onAutoPlayChanged = viewModel::setAutoPlayNext,
                    onExperimentalAppearanceChanged = viewModel::setExperimentalAppearance,
                    onFanartKeyChanged = viewModel::setFanartKey,
                    onTmdbTokenChanged = viewModel::setTmdbToken,
                    onCheckFanartKey = viewModel::checkFanartKey,
                    onEncryptedConfigChanged = viewModel::setEncryptedConfigEnabled,
                    onApplyEncryptedConfig = viewModel::applyEncryptedConfig,
                    onEncryptedConfigUrlChanged = viewModel::setEncryptedConfigUrl,
                    onHomeSectionEnabled = viewModel::setHomeSectionEnabled,
                    onMoveHomeSection = viewModel::moveHomeSection,
                    onMainHomeSectionEnabled = viewModel::setMainHomeSectionEnabled,
                    onMoveMainHomeSection = viewModel::moveMainHomeSection,
                    onPlayerSelected = viewModel::setSelectedPlayer,
                    onClearHistory = viewModel::clearHistory,
                    onCheckUpdates = viewModel::checkForUpdates,
                    onDownloadUpdate = viewModel::downloadUpdate,
                    onInstallUpdate = ::installUpdate,
                    onOpenRelease = ::openRelease,
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPlayers()
    }

    private fun installUpdate(uri: Uri) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                return
            }
            startActivity(Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (error: Exception) {
            viewModel.showUpdateError(error.message ?: "Не вдалося відкрити встановлення APK")
        }
    }

    private fun openRelease(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (error: Exception) {
            viewModel.showUpdateError(error.message ?: "Не вдалося відкрити сторінку релізу")
        }
    }
}
