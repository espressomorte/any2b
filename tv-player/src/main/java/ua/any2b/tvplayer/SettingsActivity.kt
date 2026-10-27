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
import ua.any2b.tvplayer.data.SettingsManager
import ua.any2b.tvplayer.data.ExternalVideoPlayer
import ua.any2b.tvplayer.data.ExternalVideoPlayers
import ua.any2b.tvplayer.data.WatchHistoryManager
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

    private val _autoPlayNext = MutableStateFlow(settingsManager.isAutoPlayNextEnabled())
    val autoPlayNext: StateFlow<Boolean> = _autoPlayNext
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

    fun clearHistory() {
        watchHistory.clearHistory()
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
                val availablePlayers = viewModel.availablePlayers.collectAsStateWithLifecycle().value
                val selectedPlayerPackage = viewModel.selectedPlayerPackage.collectAsStateWithLifecycle().value
                val updateState = viewModel.updateState.collectAsStateWithLifecycle().value
                SettingsScreen(
                    autoPlayNext = autoPlay,
                    availablePlayers = availablePlayers,
                    selectedPlayerPackage = selectedPlayerPackage,
                    currentVersion = viewModel.currentVersion,
                    updateState = updateState,
                    onAutoPlayChanged = viewModel::setAutoPlayNext,
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
