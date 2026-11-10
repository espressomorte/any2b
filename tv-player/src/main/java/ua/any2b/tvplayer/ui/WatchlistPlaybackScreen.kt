package ua.any2b.tvplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import coil.compose.AsyncImage

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun WatchlistPlaybackScreen(
    transfer: TolokaTransferState,
    title: String,
    logoUrl: String?,
    onRetryTorrServer: () -> Unit = {},
    onOpenTorrServe: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.widthIn(max = 720.dp).padding(horizontal = 24.dp)) {
            if (logoUrl != null) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.sizeIn(maxWidth = 300.dp, maxHeight = 86.dp),
                )
            } else {
                Text(title, fontSize = 30.sp)
            }
            if (transfer is TolokaTransferState.Error && transfer.torrServerUnavailable) {
                Text(transfer.message, modifier = Modifier.padding(horizontal = 8.dp))
                Button(onClick = onOpenTorrServe) { Text("Відкрити TorrServe") }
                Button(onClick = onRetryTorrServer) { Text("Повторити перевірку") }
            } else if (transfer is TolokaTransferState.Error) {
                Text("Не вдалося підготувати відтворення", fontSize = 20.sp)
                Text(transfer.message, modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
    }
}
