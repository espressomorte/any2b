package ua.any2b.tvplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun WatchlistPlaybackScreen(
    transfer: TolokaTransferState,
    onRetryTorrServer: () -> Unit = {},
    onOpenTorrServe: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.widthIn(max = 720.dp).padding(horizontal = 24.dp)) {
            val (step, lastSuccess) = when (transfer) {
                is TolokaTransferState.Loading -> transfer.step to transfer.lastSuccess
                is TolokaTransferState.Files -> "Підключаємось до потоку…" to "Файли роздачі отримано"
                is TolokaTransferState.Error -> transfer.message to transfer.lastSuccess
                TolokaTransferState.Idle -> "Готуємо роздачу…" to ""
            }
            Text(if (transfer is TolokaTransferState.Error) "Не вдалося підготувати відтворення"
                else "Готуємо відтворення…")
            Text(step, modifier = Modifier.padding(horizontal = 8.dp))
            if (lastSuccess.isNotBlank()) {
                Text(lastSuccess, modifier = Modifier.padding(horizontal = 8.dp))
            }
            if (transfer is TolokaTransferState.Error && transfer.torrServerUnavailable) {
                Button(onClick = onOpenTorrServe) { Text("Відкрити TorrServe") }
                Button(onClick = onRetryTorrServer) { Text("Повторити перевірку") }
            }
        }
    }
}
