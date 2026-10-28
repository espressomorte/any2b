package ua.any2b.tvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text

@Composable
fun TolokaSearchScreen(
    state: FreeTolokaState,
    onSearch: (String) -> Unit,
    onSelect: (String) -> Unit,
    onRetryMetadata: () -> Unit,
    onPlayFile: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val inputFocus = androidx.compose.runtime.remember { FocusRequester() }
    fun submit() {
        if (query.isNotBlank()) {
            keyboard?.hide()
            onSearch(query.trim())
        }
    }
    LaunchedEffect(Unit) {
        inputFocus.requestFocus()
        keyboard?.show()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(AppColors.BackgroundDark).padding(32.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 940.dp).fillMaxWidth().fillMaxSize()
                .background(AppColors.SurfaceDark, RoundedCornerShape(12.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Торент", color = Color.White, fontSize = 20.sp)
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onBack) { Text("Назад") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                        .background(AppColors.SurfaceCard, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) Text("Назва тайтла", color = AppColors.TextSecondary, fontSize = 16.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(inputFocus),
                    )
                }
                Button(onClick = { submit() }, enabled = query.isNotBlank()) { Text("Шукати") }
            }
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                TolokaSearchPanel(
                    query = query,
                    searchState = state.search,
                    transferState = state.transfer,
                    playbackStatus = state.playbackStatus,
                    openedTopics = emptySet(),
                    openedFiles = emptySet(),
                    onSearch = { submit() },
                    onSelect = onSelect,
                    onRetryMetadata = onRetryMetadata,
                    onPlayFile = onPlayFile,
                    manualSearch = true,
                )
            }
        }
    }
}
