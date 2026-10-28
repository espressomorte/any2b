package ua.any2b.tvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import ua.any2b.core.tmdb.TmdbDetails
import ua.any2b.core.tmdb.TmdbMediaType

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TmdbDetailsScreen(
    state: TmdbDetailsState,
    toloka: FreeTolokaState,
    onRetryDetails: () -> Unit,
    onSearchTorrent: (String) -> Unit,
    onSelectTorrent: (String) -> Unit,
    onRetryMetadata: () -> Unit,
    onPlayFile: (Int) -> Unit,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(AppColors.BackgroundDark).padding(32.dp)) {
        when (state) {
            TmdbDetailsState.Loading -> Text("Завантаження деталей TMDB…",
                modifier = Modifier.align(Alignment.Center), color = AppColors.AccentLight)
            is TmdbDetailsState.Error -> Column(
                modifier = Modifier.align(Alignment.Center),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(state.message, color = AppColors.ErrorRed)
                Button(onClick = onRetryDetails) { Text("Повторити") }
                OutlinedButton(onClick = onBack) { Text("Назад") }
            }
            is TmdbDetailsState.Ready -> TmdbDetailsContent(
                details = state.details,
                toloka = toloka,
                onSearchTorrent = onSearchTorrent,
                onSelectTorrent = onSelectTorrent,
                onRetryMetadata = onRetryMetadata,
                onPlayFile = onPlayFile,
                onBack = onBack,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TmdbDetailsContent(
    details: TmdbDetails,
    toloka: FreeTolokaState,
    onSearchTorrent: (String) -> Unit,
    onSelectTorrent: (String) -> Unit,
    onRetryMetadata: () -> Unit,
    onPlayFile: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val title = details.title
    var torrentOpened by rememberSaveable(title.type, title.id) { mutableStateOf(false) }
    var torrentQuery by rememberSaveable(title.type, title.id) { mutableStateOf(title.title) }
    val torrentFocus = remember(title.type, title.id) { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(title.type, title.id) { torrentFocus.requestFocus() }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
        Column(Modifier.width(220.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(16.dp)).background(AppColors.SurfaceCard)) {
                AsyncImage(model = title.posterUrl, contentDescription = title.title,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                title.rating?.let { score ->
                    ReleaseBadge((score * 10).toInt(), Modifier.align(Alignment.TopEnd).padding(8.dp))
                }
            }
            val typeLabel = if (title.type == TmdbMediaType.MOVIE) "Фільм" else "Серіал"
            Text(typeLabel, color = AppColors.TextSecondary, style = MaterialTheme.typography.titleSmall)
            details.runtimeMinutes?.let { Text("Тривалість: $it хв", color = AppColors.TextLight) }
            if (title.type == TmdbMediaType.TV) {
                details.seasons?.let { Text("Сезонів: $it", color = AppColors.TextLight) }
                details.episodes?.let { Text("Серій: $it", color = AppColors.TextLight) }
            }
            if (details.genres.isNotEmpty()) {
                Text(details.genres.joinToString(" · "), color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall)
            }
        }

        Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title.title, modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.headlineLarge, color = Color.White,
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                title.year?.let { Text("$it", color = AppColors.TextSecondary, fontSize = 16.sp) }
                OutlinedButton(onClick = onBack) { Text("Назад") }
            }
            title.originalTitle?.takeIf { it != title.title }?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = AppColors.TextLight)
            }
            title.overview?.let {
                Text(it, color = AppColors.TextSecondary, maxLines = 5,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            }

            Row(
                modifier = Modifier.fillMaxWidth().background(AppColors.SurfaceDark, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        if (!torrentOpened) {
                            torrentOpened = true
                            onSearchTorrent(torrentQuery)
                        }
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = if (torrentOpened) AppColors.NeonCyan else AppColors.SurfaceCard,
                        contentColor = if (torrentOpened) AppColors.ButtonDarkText else Color.White,
                    ),
                    modifier = Modifier.focusRequester(torrentFocus),
                ) { Text("Торент") }
                if (torrentOpened) {
                    Spacer(Modifier.width(16.dp))
                    Box(Modifier.weight(1f).background(AppColors.SurfaceCard, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)) {
                        BasicTextField(
                            value = torrentQuery,
                            onValueChange = { torrentQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                keyboard?.hide()
                                onSearchTorrent(torrentQuery)
                            }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = { keyboard?.hide(); onSearchTorrent(torrentQuery) },
                        enabled = torrentQuery.isNotBlank()) { Text("Шукати") }
                }
            }
            if (torrentOpened) {
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                    .background(AppColors.SurfaceDark, RoundedCornerShape(12.dp))) {
                    TolokaSearchPanel(
                        query = torrentQuery,
                        searchState = toloka.search,
                        transferState = toloka.transfer,
                        playbackStatus = toloka.playbackStatus,
                        openedTopics = emptySet(),
                        openedFiles = emptySet(),
                        onSearch = { onSearchTorrent(torrentQuery) },
                        onSelect = onSelectTorrent,
                        onRetryMetadata = onRetryMetadata,
                        onPlayFile = onPlayFile,
                        manualSearch = true,
                    )
                }
            }
        }
    }
}
