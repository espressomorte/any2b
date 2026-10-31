package ua.any2b.tvplayer.ui

import android.content.ComponentName
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
    autoPlayNext: Boolean,
    experimentalAppearance: Boolean,
    fanartKeyConfigured: Boolean,
    tmdbTokenConfigured: Boolean,
    fanartKeyError: String,
    fanartCheck: String,
    encryptedConfigEnabled: Boolean,
    encryptedConfigStatus: String,
    encryptedConfigUrl: String,
    homeSections: List<HomeSectionSetting>,
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
    onAutoPlayChanged: (Boolean) -> Unit,
    onExperimentalAppearanceChanged: (Boolean) -> Unit,
    onFanartKeyChanged: (String) -> Unit,
    onTmdbTokenChanged: (String) -> Unit,
    onCheckFanartKey: () -> Unit,
    onEncryptedConfigChanged: (Boolean) -> Unit,
    onApplyEncryptedConfig: (String) -> Unit,
    onEncryptedConfigUrlChanged: (String) -> Unit,
    onHomeSectionEnabled: (String, Boolean) -> Unit,
    onMoveHomeSection: (String, Int) -> Unit,
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
    var showHomeSectionsMenu by rememberSaveable { mutableStateOf(false) }
    var showMainHomeSectionsMenu by rememberSaveable { mutableStateOf(false) }
    var showTorrentMenu by rememberSaveable { mutableStateOf(false) }
    var focusedIndex by remember { mutableStateOf(0) }

    BackHandler(enabled = !showHomeSectionsMenu && !showMainHomeSectionsMenu &&
        !showTorrentMenu && !showPlayersDialog &&
        !showClearHistoryDialog) {
        onBack()
    }

    if (showHomeSectionsMenu) {
        HomeSectionsMenu(
            sections = homeSections,
            heading = "НАЛАШТУВАТИ ГОЛОВНУ АНІТЮБ",
            onToggle = onHomeSectionEnabled,
            onMove = onMoveHomeSection,
            onBack = { showHomeSectionsMenu = false },
        )
        return
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
    if (showTorrentMenu) {
        TorrentConnectionsMenu(
            fanartKeyConfigured = fanartKeyConfigured,
            tmdbTokenConfigured = tmdbTokenConfigured,
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
        "Увімкніть для автоматичного переходу до наступної серії після завершення поточної.",
        "Виберіть внутрішній ExoPlayer або зовнішній відеоплеєр (напр. Vimu) для відтворення.",
        "Очистіть усю збережену історію перегляду та поточний прогрес серій.",
        "Налаштуйте порядок та видимість списків на новій головній сторінці.",
        "Налаштуйте порядок та видимість рядків у каталозі Анітюб.",
        "Worker, TorrServer, fanart.tv та зашифрований конфіг розміщені у розділі інтеграцій.",
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
                        label = "Автоперехід до наступної серії",
                        value = if (autoPlayNext) "Увімкнено" else "Вимкнено",
                        onFocus = { focusedIndex = 0 },
                        onClick = { onAutoPlayChanged(!autoPlayNext) }
                    )

                    BiosSettingRow(
                        label = "Плеєр для відео",
                        value = selectedPlayerName,
                        onFocus = { focusedIndex = 1 },
                        onClick = { showPlayersDialog = true }
                    )

                    BiosSettingRow(
                        label = "Історія перегляду",
                        value = "Очистити",
                        onFocus = { focusedIndex = 2 },
                        onClick = { showClearHistoryDialog = true }
                    )

                    BiosSettingRow(
                        label = "Налаштувати нову головну",
                        value = "Змінити",
                        onFocus = { focusedIndex = 3 },
                        onClick = { showMainHomeSectionsMenu = true }
                    )

                    BiosSettingRow(
                        label = "Налаштувати головну Анітюб",
                        value = "Змінити",
                        onFocus = { focusedIndex = 4 },
                        onClick = { showHomeSectionsMenu = true }
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
                        onFocus = { focusedIndex = 5 },
                        onClick = { showTorrentMenu = true }
                    )

                    BiosSettingRow(
                        label = "Експериментальний вигляд",
                        value = if (experimentalAppearance) "X" else " ",
                        onFocus = { focusedIndex = 6 },
                        onClick = { onExperimentalAppearanceChanged(!experimentalAppearance) }
                    )

                    BiosSettingRow(
                        label = "Оновлення ПЗ ($currentVersion)",
                        value = updateActionText,
                        onFocus = { focusedIndex = 7 },
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FanartKeyBiosDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf("") }
    val inputFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { inputFocus.requestFocus() }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(620.dp).background(BiosSettingsTheme.Background)
            .border(BorderStroke(1.dp, BiosSettingsTheme.BorderColor), BiosSettingsTheme.Shape)
            .padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("КЛЮЧ FANART.TV", color = BiosSettingsTheme.TextNormal,
                fontFamily = IbmPlexMono, style = MaterialTheme.typography.titleLarge)
            BasicTextField(value = value, onValueChange = { value = it }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(value) }),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = BiosSettingsTheme.TextNormal, fontFamily = IbmPlexMono),
                modifier = Modifier.fillMaxWidth().focusRequester(inputFocus)
                    .border(BorderStroke(1.dp, BiosSettingsTheme.BorderColor), BiosSettingsTheme.Shape)
                    .padding(12.dp))
            Text("Порожнє значення видалить ключ.", color = BiosSettingsTheme.TextDim,
                fontFamily = IbmPlexMono, style = MaterialTheme.typography.bodySmall)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BiosDialogOption("Зберегти", false, onClick = { onSave(value) })
                BiosDialogOption("Скасувати", false, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun TorrentConnectionsMenu(
    fanartKeyConfigured: Boolean,
    tmdbTokenConfigured: Boolean,
    fanartKeyError: String,
    fanartCheck: String,
    encryptedConfigEnabled: Boolean,
    encryptedConfigStatus: String,
    encryptedConfigUrl: String,
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
    onFanartKeyChanged: (String) -> Unit,
    onTmdbTokenChanged: (String) -> Unit,
    onCheckFanartKey: () -> Unit,
    onEncryptedConfigChanged: (Boolean) -> Unit,
    onApplyEncryptedConfig: (String) -> Unit,
    onEncryptedConfigUrlChanged: (String) -> Unit,
    onBack: () -> Unit,
) {
    var focusedIndex by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var showFanartKeyDialog by remember { mutableStateOf(false) }
    val inputFocus = remember { FocusRequester() }

    fun save() {
        if (editing == "config") {
            onApplyEncryptedConfig(draft)
            draft = ""
            return
        }
        when (editing) {
            "worker" -> onWorkerUrlChanged(draft)
            "key" -> onWorkerKeyChanged(draft)
            "server" -> onTorrServerUrlChanged(draft)
            "basic" -> onTorrServerAuthChanged(draft)
            "tmdb" -> onTmdbTokenChanged(draft)
            "configUrl" -> onEncryptedConfigUrlChanged(draft)
        }
        draft = ""
        editing = null
    }

    BackHandler(enabled = !showFanartKeyDialog) { if (editing != null) editing = null else onBack() }
    LaunchedEffect(editing) { if (editing != null) inputFocus.requestFocus() }
    LaunchedEffect(encryptedConfigEnabled, encryptedConfigStatus) {
        if (encryptedConfigEnabled && encryptedConfigStatus.startsWith("Конфіг застосовано") &&
            editing == "config") editing = null
    }

    Box(Modifier.fillMaxSize().background(BiosSettingsTheme.Background).padding(24.dp)) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .border(BorderStroke(1.dp, BiosSettingsTheme.BorderColor), BiosSettingsTheme.Shape)
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("ANIMARIUM — SETUP UTILITY", style = MaterialTheme.typography.titleLarge,
                    fontFamily = IbmPlexMono, color = BiosSettingsTheme.TextNormal, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(
                    modifier = Modifier.weight(1.3f).fillMaxHeight()
                        .border(BorderStroke(1.dp, BiosSettingsTheme.BorderColor), BiosSettingsTheme.Shape)
                        .verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("[ ІНТЕГРАЦІЇ ]", style = MaterialTheme.typography.titleMedium,
                        fontFamily = IbmPlexMono, color = BiosSettingsTheme.TextNormal,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    BiosSettingRow("URL Worker", if (workerUrl.isBlank()) "Не задано" else "Задано",
                        onFocus = { focusedIndex = 0 }, onClick = { draft = workerUrl; editing = "worker" })
                    BiosSettingRow("Ключ Worker", if (workerKeyConfigured) "●●●●●●" else "Не задано",
                        onFocus = { focusedIndex = 1 }, onClick = { draft = ""; editing = "key" })
                    BiosSettingRow(if (encryptedConfigEnabled) "Перевірити Toloka" else "Перевірити Worker", "Пуск",
                        onFocus = { focusedIndex = 2 }, onClick = onCheckWorker)
                    Spacer(Modifier.height(8.dp))
                    BiosSettingRow("Адреса TorrServer", if (torrServerUrl.isBlank()) "Не задано" else "Задано",
                        onFocus = { focusedIndex = 3 }, onClick = { draft = torrServerUrl; editing = "server" })
                    BiosSettingRow("Basic TorrServer", if (torrServerAuthConfigured) "●●●●●●" else "Не задано",
                        onFocus = { focusedIndex = 4 }, onClick = { draft = ""; editing = "basic" })
                    BiosSettingRow("Перевірити TorrServer", "Пуск",
                        onFocus = { focusedIndex = 5 }, onClick = onCheckTorrServer)
                    Spacer(Modifier.height(8.dp))
                    BiosSettingRow("Ключ fanart.tv", if (fanartKeyConfigured) "●●●●●●" else "Не задано",
                        onFocus = { focusedIndex = 6 }, onClick = { showFanartKeyDialog = true })
                    BiosSettingRow("Перевірити fanart.tv", "Пуск",
                        onFocus = { focusedIndex = 7 }, onClick = onCheckFanartKey)
                    BiosSettingRow("Токен TMDB", if (tmdbTokenConfigured) "●●●●●●" else "Не задано",
                        onFocus = { focusedIndex = 11 }, onClick = { draft = ""; editing = "tmdb" })
                    Spacer(Modifier.height(8.dp))
                    BiosSettingRow("URL конфіга", "Змінити",
                        onFocus = { focusedIndex = 12 },
                        onClick = { draft = encryptedConfigUrl; editing = "configUrl" })
                    BiosSettingRow("Використовувати конфіг", if (encryptedConfigEnabled) "X" else " ",
                        onFocus = { focusedIndex = 8 },
                        onClick = {
                            if (encryptedConfigEnabled) onEncryptedConfigChanged(false)
                            else { draft = ""; editing = "config" }
                        })
                    if (encryptedConfigEnabled) BiosSettingRow("Повторно ввести пароль", "Змінити",
                        onFocus = { focusedIndex = 9 }, onClick = { draft = ""; editing = "config" })
                    Spacer(Modifier.height(16.dp))
                    BiosSettingRow("Повернутися назад", "Вихід",
                        onFocus = { focusedIndex = 10 }, onClick = onBack)
                }
                Column(
                    modifier = Modifier.weight(0.9f).fillMaxHeight()
                        .border(BorderStroke(1.dp, BiosSettingsTheme.BorderColor), BiosSettingsTheme.Shape)
                        .verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("ДОПОВНЕННЯ", style = MaterialTheme.typography.titleMedium,
                        fontFamily = IbmPlexMono, color = BiosSettingsTheme.TextNormal,
                        fontWeight = FontWeight.Bold)
                    Text(
                        when (focusedIndex) {
                            0 -> "HTTPS-адреса вашого Cloudflare Worker. AniTube залишається у вкладці «Онлайн»."
                            1 -> "Особистий ключ доступу до Worker. Логін і пароль Toloka тут не потрібні."
                            2 -> if (encryptedConfigEnabled) "Пряма перевірка входу до Toloka з даними конфіга."
                                else "Перевірка /status. Показує стан KV та сесії Worker."
                            3 -> "Локальна HTTP або HTTPS адреса встановленого вами TorrServer Matrix."
                            4 -> "За потреби введіть користувач:пароль для HTTP Basic TorrServer. Не використовуйте ключ Worker."
                            5 -> "Перевірка /echo та версії TorrServer."
                            6 -> "Особистий ключ fanart.tv для експериментального вигляду."
                            7 -> "Перевірка ключа fanart.tv без показу самого ключа."
                            11 -> "Особистий токен TMDB для прямого доступу, якщо Worker не налаштований."
                            8, 9 -> "Завантажити зашифрований конфіг із Gist. У цьому режимі Toloka й TMDB працюють напряму з пристрою. Пароль не зберігається."
                            12 -> "HTTPS-адреса зашифрованого Base64 файла. Задана адреса працює без ручного введення."
                            else -> "Повернутися до основних налаштувань."
                        },
                        style = MaterialTheme.typography.bodyMedium, fontFamily = IbmPlexMono,
                        color = BiosSettingsTheme.TextDim, lineHeight = 22.sp,
                    )
                    when (focusedIndex) {
                        0 -> if (workerUrl.isNotBlank()) Text(workerUrl, color = BiosSettingsTheme.TextNormal,
                            fontFamily = IbmPlexMono, style = MaterialTheme.typography.bodySmall)
                        3 -> if (torrServerUrl.isNotBlank()) Text(torrServerUrl, color = BiosSettingsTheme.TextNormal,
                            fontFamily = IbmPlexMono, style = MaterialTheme.typography.bodySmall)
                        12 -> Text(encryptedConfigUrl, color = BiosSettingsTheme.TextNormal,
                            fontFamily = IbmPlexMono, style = MaterialTheme.typography.bodySmall)
                    }
                    if (workerCheck.isNotBlank()) Text(workerCheck, color = BiosSettingsTheme.TextNormal,
                        fontFamily = IbmPlexMono, style = MaterialTheme.typography.bodySmall)
                    if (torrServerCheck.isNotBlank()) Text(torrServerCheck, color = BiosSettingsTheme.TextNormal,
                        fontFamily = IbmPlexMono, style = MaterialTheme.typography.bodySmall)
                    if (fanartKeyError.isNotBlank()) Text(fanartKeyError, color = BiosSettingsTheme.TextNormal,
                        fontFamily = IbmPlexMono, style = MaterialTheme.typography.bodySmall)
                    if (fanartCheck.isNotBlank()) Text(fanartCheck, color = BiosSettingsTheme.TextNormal,
                        fontFamily = IbmPlexMono, style = MaterialTheme.typography.bodySmall)
                    if (encryptedConfigStatus.isNotBlank()) Text(encryptedConfigStatus,
                        color = BiosSettingsTheme.TextNormal, fontFamily = IbmPlexMono,
                        style = MaterialTheme.typography.bodySmall)
                    if (editing != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when (editing) {
                                "worker" -> "URL WORKER (HTTPS)"
                                "key" -> "КЛЮЧ WORKER"
                                "basic" -> "BASIC TORRSERVER · КОРИСТУВАЧ:ПАРОЛЬ"
                                "config" -> "ПАРОЛЬ ЗАШИФРОВАНОГО КОНФІГА"
                                "tmdb" -> "ТОКЕН TMDB"
                                "configUrl" -> "URL ЗАШИФРОВАНОГО КОНФІГА"
                                else -> "АДРЕСА TORRSERVER"
                            }, color = BiosSettingsTheme.TextNormal, fontFamily = IbmPlexMono,
                        )
                        BasicTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Done,
                                keyboardType = if (editing == "key" || editing == "basic" ||
                                    editing == "tmdb" ||
                                    editing == "config") KeyboardType.Password else KeyboardType.Text),
                            keyboardActions = KeyboardActions(onDone = { save() }),
                            visualTransformation = if (editing == "key" || editing == "basic" ||
                                editing == "tmdb" || editing == "config") PasswordVisualTransformation()
                                else VisualTransformation.None,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = BiosSettingsTheme.TextNormal, fontFamily = IbmPlexMono,
                            ),
                            modifier = Modifier.fillMaxWidth().focusRequester(inputFocus)
                                .border(BorderStroke(1.dp, BiosSettingsTheme.BorderColor), BiosSettingsTheme.Shape)
                                .padding(12.dp),
                        )
                        if (editing == "key" || editing == "basic") {
                            Text("Порожнє значення видалить ключ.", color = BiosSettingsTheme.TextDim,
                                fontFamily = IbmPlexMono, style = MaterialTheme.typography.bodySmall)
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) Text(
                                "На Android 5 ключ діє до закриття застосунку.",
                                color = BiosSettingsTheme.TextDim, fontFamily = IbmPlexMono,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        BiosDialogOption(label = if (editing == "config") "Розшифрувати" else "Зберегти",
                            selected = false, onClick = { save() })
                        BiosDialogOption(label = "Скасувати", selected = false,
                            onClick = { draft = ""; editing = null })
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier.fillMaxWidth()
                    .border(BorderStroke(1.dp, BiosSettingsTheme.BorderColor), BiosSettingsTheme.Shape)
                    .padding(vertical = 10.dp, horizontal = 16.dp),
            ) {
                Text("↑↓ Вибір   OK Змінити   Назад Повернутися", color = BiosSettingsTheme.TextNormal,
                    fontFamily = IbmPlexMono, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (showFanartKeyDialog) FanartKeyBiosDialog(onSave = { value ->
            onFanartKeyChanged(value)
            showFanartKeyDialog = false
        }, onDismiss = { showFanartKeyDialog = false })
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HomeSectionsMenu(
    sections: List<HomeSectionSetting>,
    heading: String,
    onToggle: (String, Boolean) -> Unit,
    onMove: (String, Int) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(enabled = true) { onBack() }
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

            // Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(BorderStroke(1.dp, BiosSettingsTheme.BorderColor), BiosSettingsTheme.Shape)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "[ $heading ]",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = IbmPlexMono,
                    color = BiosSettingsTheme.TextNormal,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))

                sections.forEachIndexed { index, section ->
                    key(section.id) {
                        var isFocused by remember { mutableStateOf(false) }
                        val bg = if (isFocused) BiosSettingsTheme.FocusBg else Color.Transparent
                        val textColor = if (isFocused) BiosSettingsTheme.FocusText else BiosSettingsTheme.TextNormal
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                onClick = { onToggle(section.id, !section.enabled) },
                                scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f),
                                border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
                                glow = ClickableSurfaceDefaults.glow(glow = Glow.None, focusedGlow = Glow.None),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = bg,
                                    focusedContainerColor = bg,
                                ),
                                shape = ClickableSurfaceDefaults.shape(BiosSettingsTheme.Shape),
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { isFocused = it.isFocused },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp, horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = if (section.enabled) "[X]" else "[ ]",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontFamily = IbmPlexMono,
                                        color = textColor,
                                    )
                                    Text(
                                        text = section.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontFamily = IbmPlexMono,
                                        color = textColor,
                                        fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                            if (index > 0) {
                                BiosSmallButton(label = "▲", onClick = { onMove(section.id, -1) })
                            } else {
                                Spacer(Modifier.size(36.dp))
                            }
                            if (index < sections.lastIndex) {
                                BiosSmallButton(label = "▼", onClick = { onMove(section.id, 1) })
                            } else {
                                Spacer(Modifier.size(36.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                BiosSettingRow(
                    label = "Повернутися назад",
                    value = "Вихід",
                    onFocus = {},
                    onClick = onBack
                )
            }

            Spacer(Modifier.height(16.dp))

            // Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, BiosSettingsTheme.BorderColor), BiosSettingsTheme.Shape)
                    .padding(vertical = 10.dp, horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "OK Увімк/Вимк   ▲▼ Перемістити   Назад Повернутися",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = IbmPlexMono,
                    color = BiosSettingsTheme.TextNormal,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun BiosSmallButton(label: String, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val bg = if (isFocused) BiosSettingsTheme.FocusBg else BiosSettingsTheme.BorderColor.copy(alpha = 0.3f)
    val textColor = if (isFocused) BiosSettingsTheme.FocusText else BiosSettingsTheme.TextNormal

    Surface(
        onClick = onClick,
        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
        glow = ClickableSurfaceDefaults.glow(glow = Glow.None, focusedGlow = Glow.None),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = bg,
            focusedContainerColor = bg
        ),
        shape = ClickableSurfaceDefaults.shape(BiosSettingsTheme.Shape),
        modifier = Modifier
            .size(36.dp)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = IbmPlexMono,
                color = textColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun BiosSettingRow(
    label: String,
    value: String,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val bg = if (isFocused) BiosSettingsTheme.FocusBg else Color.Transparent
    val textColor = if (isFocused) BiosSettingsTheme.FocusText else BiosSettingsTheme.TextNormal

    Surface(
        onClick = onClick,
        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
        glow = ClickableSurfaceDefaults.glow(glow = Glow.None, focusedGlow = Glow.None),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = bg,
            focusedContainerColor = bg
        ),
        shape = ClickableSurfaceDefaults.shape(BiosSettingsTheme.Shape),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocus()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = IbmPlexMono,
                color = textColor,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = "[$value]",
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = IbmPlexMono,
                color = textColor,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun PlayerSelectionBiosDialog(
    availablePlayers: List<ExternalVideoPlayer>,
    selectedPlayerPackage: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        PlayerSelectionBiosContent(availablePlayers, selectedPlayerPackage, onSelect, onDismiss)
    }
}

@Composable
private fun PlayerSelectionBiosContent(
    availablePlayers: List<ExternalVideoPlayer>,
    selectedPlayerPackage: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(500.dp)
            .heightIn(max = 480.dp)
            .background(BiosSettingsTheme.Background)
            .border(BorderStroke(1.dp, BiosSettingsTheme.BorderColor), BiosSettingsTheme.Shape)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "[ ВИБІР ВІДЕОПЛЕЄРА ]",
            style = MaterialTheme.typography.titleLarge,
            fontFamily = IbmPlexMono,
            color = BiosSettingsTheme.TextNormal,
            fontWeight = FontWeight.Bold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BiosDialogOption(
                label = "Вбудований плеєр",
                selected = selectedPlayerPackage == null,
                onClick = { onSelect(null) },
            )
            availablePlayers.forEach { player ->
                BiosDialogOption(
                    label = player.label,
                    selected = selectedPlayerPackage == player.packageName,
                    onClick = { onSelect(player.packageName) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        BiosDialogOption(label = "Скасувати", selected = false, onClick = onDismiss)
    }
}

@Composable
private fun BiosDialogOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val bg = if (isFocused) BiosSettingsTheme.FocusBg else Color.Transparent
    val textColor = if (isFocused) BiosSettingsTheme.FocusText else BiosSettingsTheme.TextNormal

    Surface(
        onClick = onClick,
        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
        glow = ClickableSurfaceDefaults.glow(glow = Glow.None, focusedGlow = Glow.None),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = bg,
            focusedContainerColor = bg
        ),
        shape = ClickableSurfaceDefaults.shape(BiosSettingsTheme.Shape),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = IbmPlexMono,
                color = textColor
            )
            if (selected) {
                Text(
                    text = "[X]",
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = IbmPlexMono,
                    color = textColor
                )
            }
        }
    }
}

@Composable
fun BiosConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(450.dp)
                .background(BiosSettingsTheme.Background)
                .border(BorderStroke(1.dp, BiosSettingsTheme.BorderColor), BiosSettingsTheme.Shape)
                .padding(24.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "[$title]",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = IbmPlexMono,
                    color = BiosSettingsTheme.TextNormal,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = IbmPlexMono,
                    color = BiosSettingsTheme.TextNormal
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        BiosDialogOption(label = "Так", selected = false, onClick = onConfirm)
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        BiosDialogOption(label = "Ні", selected = false, onClick = onDismiss)
                    }
                }
            }
        }
    }
}

// region Previews

@Preview(device = "id:tv_1080p")
@Composable
private fun SettingsScreenPreview() {
    SettingsScreen(
        autoPlayNext = true,
        experimentalAppearance = true,
        fanartKeyConfigured = true,
        tmdbTokenConfigured = false,
        fanartKeyError = "",
        fanartCheck = "Ключ fanart.tv прийнято",
        encryptedConfigEnabled = false,
        encryptedConfigStatus = "",
        encryptedConfigUrl = "https://gist.githubusercontent.com/example/config.txt",
        homeSections = listOf(
            HomeSectionSetting("history", "Історія переглядів", true),
            HomeSectionSetting("favorites", "Обране", true)
        ),
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
        onAutoPlayChanged = {},
        onExperimentalAppearanceChanged = {},
        onFanartKeyChanged = {},
        onTmdbTokenChanged = {},
        onCheckFanartKey = {},
        onEncryptedConfigChanged = {},
        onApplyEncryptedConfig = {},
        onEncryptedConfigUrlChanged = {},
        onHomeSectionEnabled = { _, _ -> },
        onMoveHomeSection = { _, _ -> },
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
