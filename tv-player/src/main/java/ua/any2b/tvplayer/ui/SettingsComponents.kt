package ua.any2b.tvplayer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*
import ua.any2b.tvplayer.data.ExternalVideoPlayer

@Composable
internal fun BiosSmallButton(label: String, onClick: () -> Unit) {
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
internal fun BiosSettingRow(
    label: String,
    value: String,
    onFocus: () -> Unit,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val bg = if (isFocused) BiosSettingsTheme.FocusBg else Color.Transparent
    val textColor = if (isFocused) BiosSettingsTheme.FocusText else BiosSettingsTheme.TextNormal

    Surface(
        onClick = onClick,
        enabled = enabled,
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
internal fun PlayerSelectionBiosContent(
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
internal fun BiosDialogOption(
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
