package ua.any2b.tvplayer.ui

import android.content.ComponentName
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import ua.any2b.core.update.AppRelease
import ua.any2b.tvplayer.UpdateState
import ua.any2b.tvplayer.data.ExternalVideoPlayer
import ua.any2b.tvplayer.data.HomeSectionSetting

object BiosSettingsTheme {
    val Background = Color(0xFF0000AA)
    val TextNormal = Color(0xFFFFFFFF)
    val TextDim = Color(0xFFAAAAAA)
    val FocusBg = Color(0xFFC0C0C0)
    val FocusText = Color(0xFF0000AA)
    val BorderColor = Color(0xFFAAAAAA)
    val Shape = RoundedCornerShape(0.dp)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    experimentalAppearance: Boolean,
    dynamicLogo: Boolean,
    fanartKeyConfigured: Boolean,
    tmdbTokenConfigured: Boolean,
    malClientIdConfigured: Boolean,
    malClientIdError: String,
    fanartKeyError: String,
    fanartCheck: String,
    encryptedConfigEnabled: Boolean,
    encryptedConfigStatus: String,
    encryptedConfigUrl: String,
    mainHomeSections: List<HomeSectionSetting>,
    availablePlayers: List<ExternalVideoPlayer>,
    selectedPlayerPackage: String?,
    currentVersion: String,
    updateState: UpdateState,
    workerUrl: String,
    torrServerUrl: String,
    torrServerAuthConfigured: Boolean,
    workerKeyConfigured: Boolean,
    workerCheck: String,
    torrServerCheck: String,
    onWorkerUrlChanged: (String) -> Unit,
    onWorkerKeyChanged: (String) -> Unit,
    onTorrServerUrlChanged: (String) -> Unit,
    onTorrServerAuthChanged: (String) -> Unit,
    onCheckWorker: () -> Unit,
    onCheckTorrServer: () -> Unit,
    onExperimentalAppearanceChanged: (Boolean) -> Unit,
    onDynamicLogoChanged: (Boolean) -> Unit,
    onFanartKeyChanged: (String) -> Unit,
    onTmdbTokenChanged: (String) -> Unit,
    onMalClientIdChanged: (String) -> Unit,
    onCheckFanartKey: () -> Unit,
    onEncryptedConfigChanged: (Boolean) -> Unit,
    onApplyEncryptedConfig: (String) -> Unit,
    onEncryptedConfigUrlChanged: (String) -> Unit,
    onMainHomeSectionEnabled: (String, Boolean) -> Unit,
    onMoveMainHomeSection: (String, Int) -> Unit,
    onPlayerSelected: (String?) -> Unit,
    onClearHistory: () -> Unit,
    onCheckUpdates: () -> Unit,
    onDownloadUpdate: (AppRelease) -> Unit,
    onInstallUpdate: (Uri) -> Unit,
    onOpenRelease: (String) -> Unit,
    onBack: () -> Unit,
) {
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showPlayersDialog by remember { mutableStateOf(false) }
    var showMainHomeSectionsMenu by rememberSaveable { mutableStateOf(false) }
    var showTorrentMenu by rememberSaveable { mutableStateOf(false) }
    var showAppearanceMenu by rememberSaveable { mutableStateOf(false) }
    var focusedIndex by remember { mutableStateOf(0) }

    BackHandler(enabled = !showMainHomeSectionsMenu &&
        !showTorrentMenu && !showAppearanceMenu && !showPlayersDialog &&
        !showClearHistoryDialog) {
        onBack()
    }

    if (showMainHomeSectionsMenu) {
        HomeSectionsMenu(
            sections = mainHomeSections,
            heading = "НАЛАШТУВАТИ НОВУ ГОЛОВНУ",
            onToggle = onMainHomeSectionEnabled,
            onMove = onMoveMainHomeSection,
            onBack = { showMainHomeSectionsMenu = false },
        )
        return
    }
    if (showAppearanceMenu) {
        AppearanceMenu(
            experimentalAppearance = experimentalAppearance,
            dynamicLogo = dynamicLogo,
            onExperimentalAppearanceChanged = onExperimentalAppearanceChanged,
            onDynamicLogoChanged = onDynamicLogoChanged,
            onBack = { showAppearanceMenu = false },
        )
        return
    }
    if (showTorrentMenu) {
        TorrentConnectionsMenu(
            fanartKeyConfigured = fanartKeyConfigured,
            tmdbTokenConfigured = tmdbTokenConfigured,
            malClientIdConfigured = malClientIdConfigured,
            malClientIdError = malClientIdError,
            fanartKeyError = fanartKeyError,
            fanartCheck = fanartCheck,
            encryptedConfigEnabled = encryptedConfigEnabled,
            encryptedConfigStatus = encryptedConfigStatus,
            encryptedConfigUrl = encryptedConfigUrl,
            workerUrl = workerUrl,
            torrServerUrl = torrServerUrl,
            torrServerAuthConfigured = torrServerAuthConfigured,
            workerKeyConfigured = workerKeyConfigured,
            workerCheck = workerCheck,
            torrServerCheck = torrServerCheck,
            onWorkerUrlChanged = onWorkerUrlChanged,
            onWorkerKeyChanged = onWorkerKeyChanged,
            onTorrServerUrlChanged = onTorrServerUrlChanged,
            onTorrServerAuthChanged = onTorrServerAuthChanged,
            onCheckWorker = onCheckWorker,
            onCheckTorrServer = onCheckTorrServer,
            onFanartKeyChanged = onFanartKeyChanged,
            onTmdbTokenChanged = onTmdbTokenChanged,
            onMalClientIdChanged = onMalClientIdChanged,
            onCheckFanartKey = onCheckFanartKey,
            onEncryptedConfigChanged = onEncryptedConfigChanged,
            onApplyEncryptedConfig = onApplyEncryptedConfig,
            onEncryptedConfigUrlChanged = onEncryptedConfigUrlChanged,
            onBack = { showTorrentMenu = false },
        )
        return
    }

    val selectedPlayerName = availablePlayers.firstOrNull {
        it.packageName == selectedPlayerPackage
    }?.label ?: "Вбудований"

    val helpTexts = listOf(
        "Виберіть внутрішній ExoPlayer або зовнішній відеоплеєр (напр. Vimu) для відтворення.",
        "Очистіть усю збережену історію перегляду та поточний прогрес серій.",
        "Налаштуйте порядок та видимість списків на новій головній сторінці.",
        "Worker, TorrServer, fanart.tv, TMDB, MyAnimeList та зашифрований конфіг розміщені у розділі інтеграцій.",
        "Увімкніть пробний вигляд деталей із постером без тексту та англійським логотипом fanart.tv. Без обох зображень залишиться звичайний вигляд.",
        when (updateState) {
            UpdateState.Idle -> "Перевірте наявність нових версій програми Animarium TV на GitHub."
            UpdateState.Checking -> "Перевіряємо оновлення..."
            UpdateState.UpToDate -> "Встановлено останню актуальну версію."
            UpdateState.NoReleases -> "Опублікованих релізів на GitHub поки немає."
            is UpdateState.Available -> "Доступна нова версія ${updateState.release.tag}. Натисніть OK для оновлення."
            is UpdateState.MissingApk -> "Реліз ${updateState.release.tag} знайдено, але APK ще не додано до Assets. Можна відкрити сторінку релізу або перевірити ще раз."
            is UpdateState.Downloading -> "Завантаження ${updateState.release.tag}: ${updateState.progress?.let { "$it%" } ?: "очікування"}."
            is UpdateState.ReadyToInstall -> "Оновлення завантажено. Натисніть OK для встановлення."
            is UpdateState.Error -> "Помилка перевірки оновлень. Натисніть OK для повтору."
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BiosSettingsTheme.Background)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, BiosSettingsTheme.BorderColor), BiosSettingsTheme.Shape)
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ANIMARIUM — SETUP UTILITY",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = IbmPlexMono,
                    color = BiosSettingsTheme.TextNormal,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))

            // Main Body: Two Columns (Left: Options, Right: Help)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Column: Options
                Column(
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxHeight()
                        .border(BorderStroke(1.dp, BiosSettingsTheme.BorderColor), BiosSettingsTheme.Shape)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BiosSettingRow(
                        label = "Плеєр для відео",
                        value = selectedPlayerName,
                        onFocus = { focusedIndex = 0 },
                        onClick = { showPlayersDialog = true }
                    )

                    BiosSettingRow(
                        label = "Історія перегляду",
                        value = "Очистити",
                        onFocus = { focusedIndex = 1 },
                        onClick = { showClearHistoryDialog = true }
                    )

                    BiosSettingRow(
                        label = "Налаштувати нову головну",
                        value = "Змінити",
                        onFocus = { focusedIndex = 2 },
                        onClick = { showMainHomeSectionsMenu = true }
                    )

                    val updateActionText = when (updateState) {
                        UpdateState.Idle -> "Перевірити"
                        is UpdateState.Available -> "Оновити"
                        is UpdateState.MissingApk -> "Сторінка релізу"
                        is UpdateState.Downloading -> "Завантаження ${updateState.progress?.let { "$it%" } ?: "..."}"
                        UpdateState.Checking -> "Перевіряємо..."
                        is UpdateState.ReadyToInstall -> "Встановити"
                        else -> "Перевірити ще раз"
                    }
                    BiosSettingRow(
                        label = "Інтеграції",
                        value = "Налаштувати",
                        onFocus = { focusedIndex = 3 },
                        onClick = { showTorrentMenu = true }
                    )

                    BiosSettingRow(label = "Вигляд", value = "Налаштувати",
                        onFocus = { focusedIndex = 4 },
                        onClick = { showAppearanceMenu = true })

                    BiosSettingRow(
                        label = "Оновлення ПЗ ($currentVersion)",
                        value = updateActionText,
                        onFocus = { focusedIndex = 5 },
                        onClick = {
                            when (updateState) {
                                UpdateState.Idle, UpdateState.UpToDate, UpdateState.NoReleases, is UpdateState.Error -> onCheckUpdates()
                                is UpdateState.Available -> onDownloadUpdate(updateState.release)
                                is UpdateState.MissingApk -> onOpenRelease(updateState.release.pageUrl)
                                is UpdateState.ReadyToInstall -> onInstallUpdate(updateState.uri)
                                else -> {}
                            }
                        }
                    )
                }

                // Right Column: Help Panel
                Column(
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxHeight()
                        .border(BorderStroke(1.dp, BiosSettingsTheme.BorderColor), BiosSettingsTheme.Shape)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ДОПОВНЕННЯ",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = IbmPlexMono,
                        color = BiosSettingsTheme.TextNormal,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = helpTexts.getOrElse(focusedIndex) { "" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = IbmPlexMono,
                        color = BiosSettingsTheme.TextDim,
                        lineHeight = 22.sp
                    )
                    if (updateState is UpdateState.Available || updateState is UpdateState.MissingApk) {
                        Spacer(Modifier.height(12.dp))
                        BiosSettingRow(
                            label = if (updateState is UpdateState.Available) "Сторінка релізу" else "Перевірити ще раз",
                            value = if (updateState is UpdateState.Available) "GitHub" else "Повторити",
                            onFocus = {},
                            onClick = {
                                if (updateState is UpdateState.Available) {
                                    onOpenRelease(updateState.release.pageUrl)
                                } else {
                                    onCheckUpdates()
                                }
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Footer / Hints
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, BiosSettingsTheme.BorderColor), BiosSettingsTheme.Shape)
                    .padding(vertical = 10.dp, horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "animarium v$currentVersion",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = IbmPlexMono,
                        color = BiosSettingsTheme.TextDim
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "↑↓ Вибір   OK Змінити   Назад Повернутися",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = IbmPlexMono,
                    color = BiosSettingsTheme.TextNormal,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Dialogs
        if (showPlayersDialog) {
            PlayerSelectionBiosDialog(
                availablePlayers = availablePlayers,
                selectedPlayerPackage = selectedPlayerPackage,
                onSelect = { pkg ->
                    onPlayerSelected(pkg)
                    showPlayersDialog = false
                },
                onDismiss = { showPlayersDialog = false }
            )
        }

        if (showClearHistoryDialog) {
            BiosConfirmDialog(
                title = "ОЧИЩЕННЯ ІСТОРІЇ",
                message = "Усю історію перегляду та прогрес буде видалено. Продовжити?",
                onConfirm = {
                    onClearHistory()
                    showClearHistoryDialog = false
                },
                onDismiss = { showClearHistoryDialog = false }
            )
        }
    }
}

// region Previews

@Preview(device = "id:tv_1080p")
@Composable
private fun SettingsScreenPreview() {
    SettingsScreen(
        experimentalAppearance = true,
        dynamicLogo = false,
        fanartKeyConfigured = true,
        tmdbTokenConfigured = false,
        malClientIdConfigured = true,
        malClientIdError = "",
        fanartKeyError = "",
        fanartCheck = "Ключ fanart.tv прийнято",
        encryptedConfigEnabled = false,
        encryptedConfigStatus = "",
        encryptedConfigUrl = "https://gist.githubusercontent.com/example/config.txt",
        mainHomeSections = ua.any2b.tvplayer.data.MainHomeSections.defaults,
        availablePlayers = listOf(
            ExternalVideoPlayer("com.vimu.player", "Vimu Media Player", ComponentName("com.vimu.player", "Main"), "video/*"),
            ExternalVideoPlayer("org.videolan.vlc", "VLC for Android", ComponentName("org.videolan.vlc", "Main"), "video/*")
        ),
        selectedPlayerPackage = "com.vimu.player",
        currentVersion = "1.0.0",
        updateState = UpdateState.Idle,
        workerUrl = "https://worker.example.com",
        torrServerUrl = "http://192.168.1.10:8090",
        torrServerAuthConfigured = false,
        workerKeyConfigured = true,
        workerCheck = "",
        torrServerCheck = "",
        onWorkerUrlChanged = {},
        onWorkerKeyChanged = {},
        onTorrServerUrlChanged = {},
        onTorrServerAuthChanged = {},
        onCheckWorker = {},
        onCheckTorrServer = {},
        onExperimentalAppearanceChanged = {},
        onDynamicLogoChanged = {},
        onFanartKeyChanged = {},
        onTmdbTokenChanged = {},
        onMalClientIdChanged = {},
        onCheckFanartKey = {},
        onEncryptedConfigChanged = {},
        onApplyEncryptedConfig = {},
        onEncryptedConfigUrlChanged = {},
        onMainHomeSectionEnabled = { _, _ -> },
        onMoveMainHomeSection = { _, _ -> },
        onPlayerSelected = {},
        onClearHistory = {},
        onCheckUpdates = {},
        onDownloadUpdate = {},
        onInstallUpdate = {},
        onOpenRelease = {},
        onBack = {}
    )
}

@Preview(device = "id:tv_1080p")
@Composable
private fun PlayerSelectionDialogPreview() {
    PlayerSelectionBiosContent(
        availablePlayers = listOf(
            ExternalVideoPlayer("com.vimu.player", "Vimu Media Player", ComponentName("com.vimu.player", "Main"), "video/*"),
            ExternalVideoPlayer("org.videolan.vlc", "VLC for Android", ComponentName("org.videolan.vlc", "Main"), "video/*")
        ),
        selectedPlayerPackage = "com.vimu.player",
        onSelect = {},
        onDismiss = {}
    )
}

// endregion
