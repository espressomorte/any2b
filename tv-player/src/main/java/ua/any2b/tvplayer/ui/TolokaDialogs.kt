package ua.any2b.tvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun TolokaQueryDialog(
    options: List<Pair<String, String>>,
    selectedQuery: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onEdited: (String) -> Unit,
) {
    var editingValue by remember { mutableStateOf<String?>(null) }
    if (editingValue != null) {
        TolokaQueryEditDialog(
            initialValue = editingValue.orEmpty(),
            onDismiss = { editingValue = null },
            onConfirm = onEdited,
        )
        return
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.width(560.dp).heightIn(max = 600.dp)
                .clip(RoundedCornerShape(18.dp)).background(AppColors.SurfaceDark).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Варіант пошукової назви", color = Color.White,
                style = MaterialTheme.typography.titleLarge)
            Text("OK — шукати · утримуйте OK — редагувати варіант",
                color = AppColors.TextSecondary, fontSize = 13.sp)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (label, value) ->
                    var focused by remember(value) { mutableStateOf(false) }
                    Card(
                        onClick = { onSelect(value) },
                        onLongClick = { editingValue = value },
                        colors = CardDefaults.colors(
                            containerColor = if (value == selectedQuery) AppColors.NeonCyan else AppColors.SurfaceCard,
                            focusedContainerColor = AppColors.NeonCyanLight,
                        ),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                    ) {
                        Text("$label: $value" + if (value == selectedQuery) " · обрано" else "",
                            modifier = Modifier.padding(12.dp), maxLines = 2,
                            color = if (focused || value == selectedQuery) AppColors.ButtonDarkText else Color.White,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            OutlinedButton(onClick = onDismiss) { Text("Назад") }
        }
    }
}

@Composable
internal fun TolokaQueryEditDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus(); keyboard?.show() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.width(560.dp).clip(RoundedCornerShape(18.dp))
                .background(AppColors.SurfaceDark).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Редагувати пошуковий запит", color = Color.White,
                style = MaterialTheme.typography.titleLarge)
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    .background(AppColors.SurfaceCard, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        value.trim().takeIf(String::isNotBlank)?.let(onConfirm)
                    }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onConfirm(value.trim()) }, enabled = value.isNotBlank()) {
                    Text("Шукати")
                }
                OutlinedButton(onClick = onDismiss) { Text("Назад") }
            }
        }
    }
}

@Composable
internal fun TolokaPermanentRejectDialog(
    hitTitle: String,
    surfaceColors: DetailsSurfaceColors,
    onDismiss: () -> Unit,
    onReject: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(surfaceColors.panel,
            RoundedCornerShape(16.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Відхилити роздачу назавжди?", color = surfaceColors.text,
                style = MaterialTheme.typography.titleLarge)
            Text(hitTitle, color = surfaceColors.text)
            Text("Вона більше не показуватиметься для цього тайтлу. Це можна буде скасувати в налаштуваннях пошуку.",
                color = surfaceColors.mutedText)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onDismiss) { Text("Скасувати") }
                Button(onClick = onReject) { Text("Відхилити") }
            }
        }
    }
}
