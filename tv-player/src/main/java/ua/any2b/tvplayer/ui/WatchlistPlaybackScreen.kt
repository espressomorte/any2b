package ua.any2b.tvplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun WatchlistPlaybackScreen(
    transfer: TolokaTransferState,
    playbackStatus: String? = null,
    logoUrl: String? = null,
    onRetryTorrServer: () -> Unit = {},
    onOpenTorrServe: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize()) {
        val status = when (transfer) {
            is TolokaTransferState.Loading -> transfer.step
            is TolokaTransferState.Files -> "Підключаємось до потоку…"
            is TolokaTransferState.Error -> transfer.message
            TolokaTransferState.Idle -> "Готуємо роздачу…"
        }
        BlackLoadingScreen(status.ifBlank { playbackStatus.orEmpty() }, logoUrl)
        if (transfer is TolokaTransferState.Error && transfer.torrServerUnavailable) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) {
                Button(onClick = onOpenTorrServe) { Text("Відкрити TorrServe") }
                Button(onClick = onRetryTorrServer) { Text("Повторити перевірку") }
            }
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun WatchlistPlaybackScreenPreview() {
    AppTheme {
        WatchlistPlaybackScreen(
            transfer = TolokaTransferState.Loading(
                step = "Підключаємось до потоку…",
                lastSuccess = "Файли роздачі отримано"
            ), logoUrl = null
        )
    }
}
