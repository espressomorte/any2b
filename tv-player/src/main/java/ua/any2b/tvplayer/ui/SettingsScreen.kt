package ua.any2b.tvplayer.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import ua.any2b.core.update.AppRelease
import ua.any2b.tvplayer.UpdateState
import ua.any2b.tvplayer.data.ExternalVideoPlayer

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    autoPlayNext: Boolean,
    availablePlayers: List<ExternalVideoPlayer>,
    selectedPlayerPackage: String?,
    currentVersion: String,
    updateState: UpdateState,
    onAutoPlayChanged: (Boolean) -> Unit,
    onPlayerSelected: (String?) -> Unit,
    onCheckUpdates: () -> Unit,
    onDownloadUpdate: (AppRelease) -> Unit,
    onInstallUpdate: (Uri) -> Unit,
    onOpenRelease: (String) -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0B0E))
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
                    .background(Color(0xFF15171E), RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Відтворення",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = IbmPlexMono,
                    color = Color(0xFFD0BCFF)
                )

                var isFocused by remember { mutableStateOf(false) }

                Surface(
                    onClick = { onAutoPlayChanged(!autoPlayNext) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isFocused = it.isFocused },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isFocused) Color(0xFF4F378B).copy(alpha = 0.5f) else Color(0xFF20232D),
                        focusedContainerColor = Color(0xFF4F378B).copy(alpha = 0.8f)
                    )
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
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (autoPlayNext) "Увімкнено (автоматичний перехід)" else "Вимкнено (закривати плеєр)",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = IbmPlexMono,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (autoPlayNext) Color(0xFFD0BCFF) else Color(0xFF333842)),
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
                Surface(
                    onClick = { showPlayers = !showPlayers },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(0xFF20232D),
                        focusedContainerColor = Color(0xFF4F378B).copy(alpha = 0.8f)
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Плеєр для відео", color = Color.White, fontFamily = IbmPlexMono)
                        Spacer(Modifier.height(4.dp))
                        Text(selectedPlayerName, color = Color(0xFF94A3B8), fontFamily = IbmPlexMono)
                    }
                }
                if (selectedPlayerPackage != null) {
                    Text(
                        "Зовнішній плеєр не передає прогрес перегляду й не запускає наступну серію автоматично",
                        color = Color(0xFF94A3B8),
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
                            Text("Інших плеєрів не знайдено", color = Color(0xFF94A3B8))
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(Color(0xFF15171E), RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Оновлення",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = IbmPlexMono,
                    color = Color(0xFFD0BCFF),
                )
                Text("Поточна версія: $currentVersion", color = Color(0xFF94A3B8))
                when (updateState) {
                    UpdateState.Idle -> Button(onClick = onCheckUpdates) { Text("Перевірити оновлення") }
                    UpdateState.Checking -> Text("Перевіряємо GitHub Releases...", color = Color(0xFF94A3B8))
                    UpdateState.UpToDate -> {
                        Text("Встановлено останню версію", color = Color.White)
                        Button(onClick = onCheckUpdates) { Text("Перевірити ще раз") }
                    }
                    UpdateState.NoReleases -> {
                        Text("Опублікованих релізів поки немає", color = Color.White)
                        Button(onClick = onCheckUpdates) { Text("Перевірити ще раз") }
                    }
                    is UpdateState.Available -> {
                        Text("Доступна нова версія: ${updateState.release.tag}", color = Color.White)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { onDownloadUpdate(updateState.release) }) { Text("Оновити") }
                            Button(onClick = { onOpenRelease(updateState.release.pageUrl) }) { Text("Сторінка релізу") }
                        }
                    }
                    is UpdateState.MissingApk -> {
                        Text("Є реліз ${updateState.release.tag}, але APK ще не додано до Assets", color = Color.White)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { onOpenRelease(updateState.release.pageUrl) }) { Text("Сторінка релізу") }
                            Button(onClick = onCheckUpdates) { Text("Перевірити ще раз") }
                        }
                    }
                    is UpdateState.Downloading -> Text(
                        "Завантаження ${updateState.release.tag}: ${updateState.progress?.let { "$it%" } ?: "очікування"}",
                        color = Color.White,
                    )
                    is UpdateState.ReadyToInstall -> {
                        Text("Версію ${updateState.release.tag} завантажено", color = Color.White)
                        Button(onClick = { onInstallUpdate(updateState.uri) }) { Text("Встановити") }
                    }
                    is UpdateState.Error -> {
                        Text(updateState.message, color = Color(0xFFFFB4AB))
                        Button(onClick = onCheckUpdates) { Text("Повторити перевірку") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayerChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color(0xFF4F378B) else Color(0xFF20232D),
            focusedContainerColor = Color(0xFF4F378B).copy(alpha = 0.8f)
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(16.dp),
            color = Color.White,
            fontFamily = IbmPlexMono,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
