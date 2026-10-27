package ua.any2b.tvplayer.ui

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*
import ua.any2b.core.update.AppRelease
import ua.any2b.tvplayer.UpdateState
import ua.any2b.tvplayer.data.ExternalVideoPlayer
import ua.any2b.tvplayer.data.HomeSectionSetting

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    autoPlayNext: Boolean,
    homeSections: List<HomeSectionSetting>,
    availablePlayers: List<ExternalVideoPlayer>,
    selectedPlayerPackage: String?,
    currentVersion: String,
    updateState: UpdateState,
    onAutoPlayChanged: (Boolean) -> Unit,
    onHomeSectionEnabled: (String, Boolean) -> Unit,
    onMoveHomeSection: (String, Int) -> Unit,
    onPlayerSelected: (String?) -> Unit,
    onClearHistory: () -> Unit,
    onCheckUpdates: () -> Unit,
    onDownloadUpdate: (AppRelease) -> Unit,
    onInstallUpdate: (Uri) -> Unit,
    onOpenRelease: (String) -> Unit,
    onBack: () -> Unit,
) {
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showHomeSectionsMenu by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showHomeSectionsMenu) { showHomeSectionsMenu = false }
    if (showHomeSectionsMenu) {
        HomeSectionsMenu(
            sections = homeSections,
            onToggle = onHomeSectionEnabled,
            onMove = onMoveHomeSection,
            onBack = { showHomeSectionsMenu = false },
        )
        return
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.BackgroundDark)
            .padding(32.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Налаштування",
                style = MaterialTheme.typography.headlineLarge,
                fontFamily = IbmPlexMono,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.SurfaceDark, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Головна",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = IbmPlexMono,
                    color = AppColors.AccentLight,
                )
                var isConfigureHomeFocused by remember { mutableStateOf(false) }
                Surface(
                    onClick = { showHomeSectionsMenu = true },
                    scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f),
                    border = ClickableSurfaceDefaults.border(
                        border = Border.None,
                        focusedBorder = Border(BorderStroke(2.dp, AppColors.NeonCyan)),
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = AppColors.SurfaceCard,
                        focusedContainerColor = AppColors.SurfaceCard,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isConfigureHomeFocused) 1f else 0f)
                        .onFocusChanged { isConfigureHomeFocused = it.isFocused },
                ) {
                    Text(
                        "Налаштувати головну",
                        modifier = Modifier.padding(16.dp),
                        color = if (isConfigureHomeFocused) AppColors.NeonCyan else Color.White,
                        fontFamily = IbmPlexMono,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.SurfaceDark, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Відтворення",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = IbmPlexMono,
                    color = AppColors.AccentLight
                )

                var isFocusedAutoPlay by remember { mutableStateOf(false) }

                Surface(
                    onClick = { onAutoPlayChanged(!autoPlayNext) },
                    scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f),
                    border = ClickableSurfaceDefaults.border(
                        border = Border.None,
                        focusedBorder = Border(BorderStroke(2.dp, AppColors.NeonCyan))
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = AppColors.SurfaceCard,
                        focusedContainerColor = AppColors.SurfaceCard
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isFocusedAutoPlay) 1f else 0f)
                        .onFocusChanged { isFocusedAutoPlay = it.isFocused }
                        .neonGlow(focused = isFocusedAutoPlay, borderRadius = 12.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "По завершенню серії відтворення наступної",
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = IbmPlexMono,
                                color = if (isFocusedAutoPlay) AppColors.NeonCyan else Color.White,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (autoPlayNext) "Увімкнено (автоматичний перехід)" else "Вимкнено (закривати плеєр)",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = IbmPlexMono,
                                color = AppColors.TextSecondary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (autoPlayNext) AppColors.AccentLight else Color(0xFF333842)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (autoPlayNext) {
                                Text("✓", color = Color(0xFF21005D), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                    }
                }


                var showPlayers by remember { mutableStateOf(false) }
                val selectedPlayerName = availablePlayers.firstOrNull {
                    it.packageName == selectedPlayerPackage
                }?.label ?: "Вбудований плеєр"
                var isFocusedPlayer by remember { mutableStateOf(false) }
                Surface(
                    onClick = { showPlayers = !showPlayers },
                    scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f),
                    border = ClickableSurfaceDefaults.border(
                        border = Border.None,
                        focusedBorder = Border(BorderStroke(2.dp, AppColors.NeonCyan))
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = AppColors.SurfaceCard,
                        focusedContainerColor = AppColors.SurfaceCard
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isFocusedPlayer) 1f else 0f)
                        .onFocusChanged { isFocusedPlayer = it.isFocused }
                        .neonGlow(focused = isFocusedPlayer, borderRadius = 12.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Плеєр для відео", color = if (isFocusedPlayer) AppColors.NeonCyan else Color.White, fontFamily = IbmPlexMono)
                        Spacer(Modifier.height(4.dp))
                        Text(selectedPlayerName, color = AppColors.TextSecondary, fontFamily = IbmPlexMono)
                    }
                }
                if (selectedPlayerPackage != null) {
                    Text(
                        "Зовнішній плеєр не передає прогрес перегляду й не запускає наступну серію автоматично",
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = IbmPlexMono
                    )
                }
                if (showPlayers) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlayerChoice(
                            label = "Вбудований плеєр",
                            selected = selectedPlayerPackage == null,
                            onClick = {
                                onPlayerSelected(null)
                                showPlayers = false
                            }
                        )
                        availablePlayers.forEach { player ->
                            PlayerChoice(
                                label = player.label,
                                selected = selectedPlayerPackage == player.packageName,
                                onClick = {
                                    onPlayerSelected(player.packageName)
                                    showPlayers = false
                                }
                            )
                        }
                        if (availablePlayers.isEmpty()) {
                            Text("Інших плеєрів не знайдено", color = AppColors.TextSecondary)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(AppColors.SurfaceDark, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Історія",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = IbmPlexMono,
                    color = AppColors.AccentLight,
                )
                var isFocusedHistory by remember { mutableStateOf(false) }
                Surface(
                    onClick = { showClearHistoryDialog = true },
                    scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f),
                    border = ClickableSurfaceDefaults.border(
                        border = Border.None,
                        focusedBorder = Border(BorderStroke(2.dp, AppColors.NeonCyan))
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = AppColors.SurfaceCard,
                        focusedContainerColor = AppColors.SurfaceCard,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isFocusedHistory) 1f else 0f)
                        .onFocusChanged { isFocusedHistory = it.isFocused }
                        .neonGlow(focused = isFocusedHistory, borderRadius = 12.dp),
                ) {
                    Text(
                        "Очистити історію",
                        modifier = Modifier.padding(16.dp),
                        color = if (isFocusedHistory) AppColors.NeonCyan else Color.White,
                        fontFamily = IbmPlexMono,
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(AppColors.SurfaceDark, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Оновлення",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = IbmPlexMono,
                    color = AppColors.AccentLight,
                )
                Text("Поточна версія: $currentVersion", color = AppColors.TextSecondary)
                when (updateState) {
                    UpdateState.Idle -> PrimaryButton(onClick = onCheckUpdates, text = "Перевірити оновлення")
                    UpdateState.Checking -> Text("Перевіряємо GitHub Releases...", color = AppColors.TextSecondary)
                    UpdateState.UpToDate -> {
                        Text("Встановлено останню версію", color = Color.White)
                        PrimaryButton(onClick = onCheckUpdates, text = "Перевірити ще раз")
                    }
                    UpdateState.NoReleases -> {
                        Text("Опублікованих релізів поки немає", color = Color.White)
                        PrimaryButton(onClick = onCheckUpdates, text = "Перевірити ще раз")
                    }
                    is UpdateState.Available -> {
                        Text("Доступна нова версія: ${updateState.release.tag}", color = Color.White)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PrimaryButton(onClick = { onDownloadUpdate(updateState.release) }, text = "Оновити")
                            PrimaryButton(onClick = { onOpenRelease(updateState.release.pageUrl) }, text = "Сторінка релізу")
                        }
                    }
                    is UpdateState.MissingApk -> {
                        Text("Є реліз ${updateState.release.tag}, але APK ще не додано до Assets", color = Color.White)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PrimaryButton(onClick = { onOpenRelease(updateState.release.pageUrl) }, text = "Сторінка релізу")
                            PrimaryButton(onClick = onCheckUpdates, text = "Перевірити ще раз")
                        }
                    }
                    is UpdateState.Downloading -> Text(
                        "Завантаження ${updateState.release.tag}: ${updateState.progress?.let { "$it%" } ?: "очікування"}",
                        color = Color.White,
                    )
                    is UpdateState.ReadyToInstall -> {
                        Text("Версію ${updateState.release.tag} завантажено", color = Color.White)
                        PrimaryButton(onClick = { onInstallUpdate(updateState.uri) }, text = "Встановити")
                    }
                    is UpdateState.Error -> {
                        Text(updateState.message, color = AppColors.ErrorRed)
                        PrimaryButton(onClick = onCheckUpdates, text = "Повторити перевірку")
                    }
                }
            }
        }
        if (showClearHistoryDialog) {
            Dialog(onDismissRequest = { showClearHistoryDialog = false }) {
                Column(
                    modifier = Modifier
                        .background(AppColors.SurfaceCard, RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text(
                        "Ар ю шур?",
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = IbmPlexMono,
                        color = Color.White,
                    )
                    Text(
                        "Усю історію перегляду буде видалено.",
                        color = AppColors.TextSecondary,
                        fontFamily = IbmPlexMono,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PrimaryButton(onClick = { showClearHistoryDialog = false }, text = "Нат шур")
                        PrimaryButton(onClick = {
                            onClearHistory()
                            showClearHistoryDialog = false
                        }, text = "Шур")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HomeSectionsMenu(
    sections: List<HomeSectionSetting>,
    onToggle: (String, Boolean) -> Unit,
    onMove: (String, Int) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.BackgroundDark)
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Налаштувати головну",
            style = MaterialTheme.typography.headlineLarge,
            fontFamily = IbmPlexMono,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
        sections.forEachIndexed { index, section ->
            key(section.id) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppColors.SurfaceDark, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    var isFocused by remember { mutableStateOf(false) }
                    Surface(
                        onClick = { onToggle(section.id, !section.enabled) },
                        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f),
                        border = ClickableSurfaceDefaults.border(
                            border = Border.None,
                            focusedBorder = Border(BorderStroke(2.dp, AppColors.NeonCyan)),
                        ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = AppColors.SurfaceCard,
                            focusedContainerColor = AppColors.SurfaceCard,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { isFocused = it.isFocused }
                            .semantics {
                                role = Role.Checkbox
                                stateDescription = if (section.enabled) "Увімкнено" else "Вимкнено"
                            },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (section.enabled) AppColors.NeonCyan else AppColors.SurfaceDark)
                                    .border(
                                        2.dp,
                                        if (section.enabled) AppColors.NeonCyan else AppColors.TextSecondary,
                                        RoundedCornerShape(4.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (section.enabled) {
                                    FontAwesomeIcon(FaIcons.CHECK, color = AppColors.ButtonDarkText, size = 13.sp)
                                }
                            }
                            Text(
                                section.title,
                                color = if (isFocused) AppColors.NeonCyan else Color.White,
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = IbmPlexMono,
                            )
                        }
                    }
                    IconButton(
                        onClick = { onMove(section.id, -1) },
                        enabled = index > 0,
                        modifier = Modifier.semantics { contentDescription = "Перемістити ${section.title} вгору" },
                    ) {
                        FontAwesomeIcon(
                            FaIcons.ARROW_UP,
                            color = if (index > 0) AppColors.TextLight else AppColors.TextWatched,
                            size = 17.sp,
                        )
                    }
                    IconButton(
                        onClick = { onMove(section.id, 1) },
                        enabled = index < sections.lastIndex,
                        modifier = Modifier.semantics { contentDescription = "Перемістити ${section.title} вниз" },
                    ) {
                        FontAwesomeIcon(
                            FaIcons.ARROW_DOWN,
                            color = if (index < sections.lastIndex) AppColors.TextLight else AppColors.TextWatched,
                            size = 17.sp,
                        )
                    }
                }
            }
        }
        PrimaryButton(onClick = onBack, text = "Назад")
    }
}

@Composable
private fun PrimaryButton(onClick: () -> Unit, text: String) {
    var isFocused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        scale = ButtonDefaults.scale(scale = 1f, focusedScale = 1f),
        shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
        glow = ButtonDefaults.glow(glow = Glow.None, focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 10.dp)),
        colors = ButtonDefaults.colors(
            containerColor = AppColors.PrimaryPurple,
            contentColor = Color.White,
            focusedContainerColor = AppColors.PrimaryPurple,
            focusedContentColor = Color.White
        ),
        modifier = Modifier
            .zIndex(if (isFocused) 1f else 0f)
            .onFocusChanged { isFocused = it.isFocused }
            .neonGlow(focused = isFocused, borderRadius = 12.dp)
    ) {
        Text(text)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayerChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val containerColor = if (selected) Color(0xFFE2E8F0) else AppColors.SurfaceCard
    val contentColor = if (selected) Color(0xFF0F172A) else Color.White
    Surface(
        onClick = onClick,
        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(BorderStroke(2.dp, AppColors.NeonCyan))
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            focusedContainerColor = containerColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isFocused) 1f else 0f)
            .onFocusChanged { isFocused = it.isFocused }
            .neonGlow(focused = isFocused, borderRadius = 12.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(16.dp),
            color = if (isFocused) AppColors.NeonCyan else contentColor,
            fontFamily = IbmPlexMono,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
