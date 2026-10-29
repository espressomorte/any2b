package ua.any2b.tvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.OutlinedButtonDefaults
import androidx.tv.material3.Border
import androidx.tv.material3.Glow
import androidx.tv.material3.Text
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import ua.any2b.core.tmdb.TmdbDetails
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbTitle
import ua.any2b.core.toloka.shortenedSearchNames
import java.util.Locale

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TmdbDetailsScreen(
    state: TmdbDetailsState,
    toloka: FreeTolokaState,
    openedFiles: Set<Int>,
    onRetryDetails: () -> Unit,
    onSearchTorrent: (String) -> Unit,
    onSelectTorrent: (String) -> Unit,
    onRetryMetadata: () -> Unit,
    onPlayFile: (Int) -> Unit,
    onToggleFavorite: () -> Unit,
    onTrailer: () -> Unit,
    onRetryRelated: () -> Unit,
    onOpenRelated: (TmdbTitle) -> Unit,
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
                isFavorite = state.isFavorite,
                related = state.related,
                toloka = toloka,
                openedFiles = openedFiles,
                onToggleFavorite = onToggleFavorite,
                onTrailer = onTrailer,
                onRetryRelated = onRetryRelated,
                onOpenRelated = onOpenRelated,
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
    isFavorite: Boolean,
    related: TmdbRelatedState,
    toloka: FreeTolokaState,
    openedFiles: Set<Int>,
    onToggleFavorite: () -> Unit,
    onTrailer: () -> Unit,
    onRetryRelated: () -> Unit,
    onOpenRelated: (TmdbTitle) -> Unit,
    onSearchTorrent: (String) -> Unit,
    onSelectTorrent: (String) -> Unit,
    onRetryMetadata: () -> Unit,
    onPlayFile: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val title = details.title
    var torrentOpened by rememberSaveable(title.type, title.id) { mutableStateOf(false) }
    val customTorrentQueries = remember(title.type, title.id) { mutableStateListOf<String>() }
    val torrentOptions = (customTorrentQueries.map { "Власний" to it } +
        tmdbTorrentQueryOptions(details)).distinctBy { it.second.lowercase() }
    var torrentQuery by rememberSaveable(title.type, title.id) {
        mutableStateOf(torrentOptions.firstOrNull()?.second ?: title.title)
    }
    var torrentQueryCustomized by rememberSaveable(title.type, title.id) { mutableStateOf(false) }
    var showQueryDialog by remember(title.type, title.id) { mutableStateOf(false) }
    val torrentFocus = remember(title.type, title.id) { FocusRequester() }
    var showRelated by rememberSaveable(title.type, title.id) { mutableStateOf(false) }
    var infoDialog by rememberSaveable(title.type, title.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(title.type, title.id) { torrentFocus.requestFocus() }
    LaunchedEffect(title.englishTitle, details.alternativeTitles) {
        if (!torrentQueryCustomized && torrentOptions.isNotEmpty()) {
            torrentQuery = torrentOptions.first().second
        }
    }

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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TmdbDetailAction(FaIcons.BOOKMARK,
                    if (isFavorite) "Вилучити з обраного" else "Додати в обране",
                    selected = isFavorite, modifier = Modifier.weight(1f), onClick = onToggleFavorite)
                TmdbDetailAction(FaIcons.FILM, "Відкрити трейлер",
                    modifier = Modifier.weight(1f), onClick = onTrailer)
                TmdbDetailAction(FaIcons.LINK, "Пов’язані тайтли",
                    modifier = Modifier.weight(1f), onClick = { showRelated = true })
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
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(title.title, modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.headlineLarge, color = Color.White,
                        fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    title.year?.let { Text("$it", color = AppColors.TextSecondary, fontSize = 16.sp) }
                    val countries = details.countryCodes.mapNotNull { code ->
                        code.takeIf { it.matches(Regex("[A-Za-z]{2}")) }?.let {
                            Locale.forLanguageTag("und-${it.uppercase(Locale.ROOT)}")
                                .getDisplayCountry(Locale.forLanguageTag("uk"))
                        }?.takeIf(String::isNotBlank)
                    }
                    if (countries.isNotEmpty()) {
                        Text(countries.joinToString(", "), modifier = Modifier.widthIn(max = 360.dp),
                            color = AppColors.TextSecondary, fontSize = 16.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                OutlinedButton(onClick = onBack) { Text("Назад") }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val secondaryTitle = title.englishTitle?.takeIf { it != title.title }
                    ?: title.originalTitle?.takeIf { it != title.title }
                secondaryTitle?.let {
                    Text(it, modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleMedium, color = AppColors.TextLight,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (!title.overview.isNullOrBlank()) {
                    TmdbInfoChip("Synopsis") { infoDialog = "synopsis" }
                }
                if (details.directors.isNotEmpty() || details.cast.isNotEmpty()) {
                    TmdbInfoChip("Crew") { infoDialog = "crew" }
                }
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
                        var queryFocused by remember { mutableStateOf(false) }
                        Text(torrentQuery, color = if (queryFocused) AppColors.NeonCyanLight else Color.White,
                            fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                                .onFocusChanged { queryFocused = it.isFocused }
                                .clickable { showQueryDialog = true }
                                .semantics { contentDescription = "Вибрати пошуковий запит Toloka: $torrentQuery" })
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = { onSearchTorrent(torrentQuery) },
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
                        openedFiles = openedFiles,
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
    if (showRelated) {
        TmdbRelatedDialog(related, onRetryRelated,
            onDismiss = { showRelated = false },
            onOpen = { item -> showRelated = false; onOpenRelated(item) })
    }
    if (showQueryDialog) {
        TolokaQueryDialog(options = torrentOptions, selectedQuery = torrentQuery,
            onDismiss = { showQueryDialog = false },
            onSelect = { selected ->
                showQueryDialog = false
                torrentQuery = selected
                torrentQueryCustomized = true
                onSearchTorrent(selected)
            },
            onEdited = { edited ->
                showQueryDialog = false
                customTorrentQueries.remove(edited)
                customTorrentQueries.add(0, edited)
                torrentQuery = edited
                torrentQueryCustomized = true
                onSearchTorrent(edited)
            })
    }
    infoDialog?.let { section ->
        TmdbInfoDialog(section, details, onDismiss = { infoDialog = null })
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TmdbInfoChip(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(percent = 50)
    Surface(onClick = onClick,
        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f),
        shape = ClickableSurfaceDefaults.shape(shape),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, AppColors.TextMuted), shape = shape),
            focusedBorder = Border.None),
        colors = ClickableSurfaceDefaults.colors(containerColor = AppColors.SurfaceDark,
            focusedContainerColor = AppColors.NeonCyanLight),
        modifier = Modifier.onFocusChanged { focused = it.isFocused }) {
        Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = if (focused) AppColors.ButtonDarkText else AppColors.TextLight,
            fontSize = 12.sp)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TmdbInfoDialog(section: String, details: TmdbDetails, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(820.dp).heightIn(max = 620.dp)
            .clip(RoundedCornerShape(16.dp)).background(AppColors.SurfaceDark).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (section == "synopsis") "Synopsis" else "Crew",
                color = Color.White, style = MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).focusable(),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (section == "synopsis") {
                    Text(details.title.overview.orEmpty(), color = AppColors.TextLight,
                        style = MaterialTheme.typography.bodyMedium)
                } else {
                    if (details.directors.isNotEmpty()) {
                        Text("Режисер: ${details.directors.joinToString(", ")}",
                            color = AppColors.TextLight, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (details.cast.isNotEmpty()) {
                        Text("Актори: ${details.cast.joinToString(", ")}",
                            color = AppColors.TextLight, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            OutlinedButton(onClick = onDismiss) { Text("Закрити") }
        }
    }
}

private fun tmdbTorrentQueryOptions(details: TmdbDetails): List<Pair<String, String>> {
    val title = details.title
    val year = title.year?.toString()
    return buildList {
        fun addVariant(label: String, value: String) {
            add(label to value)
            if (year != null) add("$label + рік" to "$value $year")
        }
        fun addName(label: String, name: String?) {
            val value = name?.trim()?.takeIf(String::isNotBlank) ?: return
            addVariant(label, value)
            shortenedSearchNames(value).forEach { short ->
                addVariant("$label · без уточнення", short)
            }
        }
        addName("Українська", title.ukrainianTitle?.takeIf { name ->
            name.any { it in '\u0400'..'\u04FF' }
        })
        addName("English", title.englishTitle)
        addName("Оригінальна", title.originalTitle)
        addName("Назва", title.title)
        details.alternativeTitles.forEachIndexed { index, name ->
            addName("Синонім ${index + 1}", name)
        }
    }.distinctBy { it.second.lowercase() }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TmdbDetailAction(icon: String, label: String, selected: Boolean = false,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> AppColors.NeonCyanLight
        selected -> AppColors.NeonCyan
        else -> AppColors.SurfaceDark
    }
    val foreground = if (focused || selected) AppColors.ButtonDarkText else AppColors.AccentLight
    OutlinedButton(onClick = onClick,
        scale = OutlinedButtonDefaults.scale(scale = 1f, focusedScale = 1f),
        shape = OutlinedButtonDefaults.shape(RoundedCornerShape(10.dp)),
        border = OutlinedButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
        glow = OutlinedButtonDefaults.glow(glow = Glow.None,
            focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 8.dp)),
        colors = OutlinedButtonDefaults.colors(containerColor = background,
            contentColor = foreground, focusedContainerColor = background,
            focusedContentColor = foreground),
        modifier = modifier.onFocusChanged { focused = it.isFocused }
            .neonGlow(focused = focused, borderRadius = 10.dp)
            .semantics { contentDescription = label }) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            FontAwesomeIcon(icon, color = foreground, size = 18.sp)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TmdbRelatedDialog(state: TmdbRelatedState, onRetry: () -> Unit,
    onDismiss: () -> Unit, onOpen: (TmdbTitle) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth(0.85f)
            .clip(RoundedCornerShape(16.dp)).background(AppColors.SurfaceDark).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Пов’язані тайтли", style = MaterialTheme.typography.titleLarge,
                color = Color.White)
            when (state) {
                TmdbRelatedState.Loading -> Text("Завантаження…", color = AppColors.TextSecondary)
                is TmdbRelatedState.Error -> {
                    Text(state.message, color = AppColors.ErrorRed)
                    Button(onClick = onRetry) { Text("Повторити") }
                }
                is TmdbRelatedState.Results -> if (state.titles.isEmpty()) {
                    Text("Пов’язаних тайтлів немає", color = AppColors.TextSecondary)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(8.dp)) {
                        items(state.titles, key = { "${it.type}:${it.id}" }) { item ->
                            TmdbPosterCard(item, onClick = { onOpen(item) })
                        }
                    }
                }
            }
            OutlinedButton(onClick = onDismiss) { Text("Закрити") }
        }
    }
}
