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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.window.DialogProperties
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
import ua.any2b.core.matcher.SeriesTitleSearch
import ua.any2b.core.fanart.FanartArtwork
import ua.any2b.tvplayer.data.AnitubeState
import ua.any2b.tvplayer.data.AniListAvailability
import java.util.Locale

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TmdbDetailsScreen(
    state: TmdbDetailsState,
    favoritesLayout: Boolean = false,
    torrentsOnly: Boolean = false,
    experimentalArtwork: FanartArtwork? = null,
    experimentalPalette: FanartPalette? = null,
    fanartNotice: String? = null,
    toloka: FreeTolokaState,
    anitube: AnitubeState,
    aniListAvailability: AniListAvailability = AniListAvailability.UNKNOWN,
    openedTopics: Set<String>,
    openedFiles: Set<Int>,
    onRetryDetails: () -> Unit,
    onSearchTorrent: (String) -> Unit,
    onSelectTorrent: (String) -> Unit,
    onPromoteTorrent: (String) -> Unit,
    onDiscardTorrent: (String) -> Unit,
    onRejectTorrent: (String) -> Unit,
    onRetryMetadata: () -> Unit,
    onPlayFile: (Int) -> Unit,
    onPanelSelected: (Panel) -> Unit,
    onRetryAnitube: () -> Unit,
    onSelectAnitubeRelease: (ua.any2b.core.anitube.AnitubeSearchResult) -> Unit,
    onSelectAnitubeSeason: (Int) -> Unit,
    onSelectAnitubeVoice: (Int) -> Unit,
    onSelectAnitubeEpisode: (Int) -> Unit,
    onPlayAnitube: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onTrailer: () -> Unit,
    onRetryRelated: () -> Unit,
    onOpenRelated: (TmdbTitle) -> Unit,
    onBack: () -> Unit,
) {
    val pageBackground = if (experimentalArtwork != null)
        experimentalPalette?.background ?: AppColors.BackgroundDark else AppColors.BackgroundDark
    Box(Modifier.fillMaxSize().background(pageBackground).padding(32.dp)) {
        when (state) {
            TmdbDetailsState.Loading -> TmdbDetailsPreparingScreen("Завантаження деталей TMDB…")
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
                activePanel = state.activePanel,
                favoritesLayout = favoritesLayout,
                torrentsOnly = torrentsOnly,
                experimentalArtwork = experimentalArtwork,
                experimentalPalette = experimentalPalette,
                fanartNotice = fanartNotice,
                isFavorite = state.isFavorite,
                isWatchlisted = state.isWatchlisted,
                related = state.related,
                toloka = toloka,
                anitube = anitube,
                aniListAvailability = aniListAvailability,
                openedTopics = openedTopics,
                openedFiles = openedFiles,
                onToggleFavorite = onToggleFavorite,
                onToggleWatchlist = onToggleWatchlist,
                onTrailer = onTrailer,
                onRetryRelated = onRetryRelated,
                onOpenRelated = onOpenRelated,
                onSearchTorrent = onSearchTorrent,
                onSelectTorrent = onSelectTorrent,
                onPromoteTorrent = onPromoteTorrent,
                onDiscardTorrent = onDiscardTorrent,
                onRejectTorrent = onRejectTorrent,
                onRetryMetadata = onRetryMetadata,
                onPlayFile = onPlayFile,
                onPanelSelected = onPanelSelected,
                onRetryAnitube = onRetryAnitube,
                onSelectAnitubeRelease = onSelectAnitubeRelease,
                onSelectAnitubeSeason = onSelectAnitubeSeason,
                onSelectAnitubeVoice = onSelectAnitubeVoice,
                onSelectAnitubeEpisode = onSelectAnitubeEpisode,
                onPlayAnitube = onPlayAnitube,
                onBack = onBack,
            )
        }
    }
}

@Composable
internal fun TmdbDetailsPreparingScreen(message: String = "Підготовка оформлення…") {
    Box(Modifier.fillMaxSize().background(AppColors.BackgroundDark),
        contentAlignment = Alignment.Center) {
        Text(message, color = AppColors.AccentLight)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TmdbDetailsContent(
    details: TmdbDetails,
    activePanel: Panel,
    favoritesLayout: Boolean,
    torrentsOnly: Boolean,
    experimentalArtwork: FanartArtwork?,
    experimentalPalette: FanartPalette?,
    fanartNotice: String?,
    isFavorite: Boolean,
    isWatchlisted: Boolean,
    related: TmdbRelatedState,
    toloka: FreeTolokaState,
    anitube: AnitubeState,
    aniListAvailability: AniListAvailability,
    openedTopics: Set<String>,
    openedFiles: Set<Int>,
    onToggleFavorite: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onTrailer: () -> Unit,
    onRetryRelated: () -> Unit,
    onOpenRelated: (TmdbTitle) -> Unit,
    onSearchTorrent: (String) -> Unit,
    onSelectTorrent: (String) -> Unit,
    onPromoteTorrent: (String) -> Unit,
    onDiscardTorrent: (String) -> Unit,
    onRejectTorrent: (String) -> Unit,
    onRetryMetadata: () -> Unit,
    onPlayFile: (Int) -> Unit,
    onPanelSelected: (Panel) -> Unit,
    onRetryAnitube: () -> Unit,
    onSelectAnitubeRelease: (ua.any2b.core.anitube.AnitubeSearchResult) -> Unit,
    onSelectAnitubeSeason: (Int) -> Unit,
    onSelectAnitubeVoice: (Int) -> Unit,
    onSelectAnitubeEpisode: (Int) -> Unit,
    onPlayAnitube: () -> Unit,
    onBack: () -> Unit,
) {
    val title = details.title
    val surfaceColors = detailsSurfaceColors(experimentalPalette, experimentalArtwork != null)
    val dayText = if (experimentalArtwork != null && experimentalPalette?.light == true)
        Color(0xFF263241) else AppColors.TextSecondary
    val countries = details.countryCodes.mapNotNull { code ->
        code.takeIf { it.matches(Regex("[A-Za-z]{2}")) }?.let {
            Locale.forLanguageTag("und-${it.uppercase(Locale.ROOT)}")
                .getDisplayCountry(Locale.forLanguageTag("uk"))
        }?.takeIf(String::isNotBlank)
    }
    val torrentOpened = torrentsOnly || activePanel == Panel.TORRENT
    val customTorrentQueries = remember(title.type, title.id) { mutableStateListOf<String>() }
    val torrentOptions = (customTorrentQueries.map { "Власний" to it } +
        toloka.queryOptions + tmdbTorrentQueryOptions(details))
        .distinctBy { it.second.lowercase() }
    var torrentQuery by rememberSaveable(title.type, title.id) {
        mutableStateOf(torrentOptions.firstOrNull()?.second ?: title.title)
    }
    var torrentQueryCustomized by rememberSaveable(title.type, title.id) { mutableStateOf(false) }
    var showQueryDialog by remember(title.type, title.id) { mutableStateOf(false) }
    val torrentFocus = remember(title.type, title.id) { FocusRequester() }
    var showRelated by rememberSaveable(title.type, title.id) { mutableStateOf(false) }
    var infoDialog by rememberSaveable(title.type, title.id) { mutableStateOf<String?>(null) }
    if (!torrentsOnly) LaunchedEffect(title.type, title.id) { torrentFocus.requestFocus() }
    LaunchedEffect(title.englishTitle, details.alternativeTitles) {
        if (!torrentQueryCustomized && torrentOptions.isNotEmpty()) {
            torrentQuery = torrentOptions.first().second
        }
    }
    LaunchedEffect(toloka.search) {
        val result = toloka.search as? TolokaSearchState.Results
        if (result != null && !torrentQueryCustomized) torrentQuery = result.query
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
        Column(Modifier.width(220.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                .then(if (experimentalArtwork == null)
                    Modifier.clip(RoundedCornerShape(16.dp)) else Modifier)
                .background(if (experimentalArtwork != null)
                    experimentalPalette?.background ?: AppColors.BackgroundDark
                else AppColors.SurfaceCard)) {
                if (experimentalArtwork != null && !favoritesLayout) {
                    FanartPoster(experimentalArtwork, title.title,
                        experimentalPalette?.background ?: AppColors.BackgroundDark,
                        Modifier.fillMaxSize())
                } else {
                    AsyncImage(model = title.posterUrl, contentDescription = title.title,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                if (experimentalArtwork == null && !favoritesLayout) title.rating?.let { score ->
                    ReleaseBadge((score * 10).toInt(), Modifier.align(Alignment.TopEnd).padding(8.dp))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TmdbDetailAction(FaIcons.BOOKMARK,
                    if (isFavorite) "Вилучити з обраного" else "Додати в обране",
                    selected = isFavorite, modifier = Modifier.weight(1f),
                    surfaceColors = surfaceColors, onClick = onToggleFavorite)
                TmdbDetailAction(FaIcons.EYE,
                    if (isWatchlisted) "У плані перегляду" else "Додати в план перегляду",
                    selected = isWatchlisted, modifier = Modifier.weight(1f),
                    surfaceColors = surfaceColors, onClick = onToggleWatchlist)
                TmdbDetailAction(FaIcons.FILM, "Відкрити трейлер",
                    modifier = Modifier.weight(1f), surfaceColors = surfaceColors, onClick = onTrailer)
                TmdbDetailAction(FaIcons.LINK, "Пов’язані тайтли",
                    modifier = Modifier.weight(1f), surfaceColors = surfaceColors,
                    onClick = { showRelated = true })
            }
            if (favoritesLayout) {
                if (title.type == TmdbMediaType.TV) {
                    details.seasons?.let { Text("Сезонів: $it", color = dayText) }
                    details.episodes?.let { Text("Серій: $it", color = dayText) }
                }
                details.runtimeMinutes?.let { Text("Тривалість: $it хв", color = dayText) }
                if (!title.overview.isNullOrBlank()) {
                    TmdbInfoChip("Синопсис", surfaceColors) { infoDialog = "synopsis" }
                    Text(title.overview.orEmpty(), color = dayText, maxLines = 5, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { infoDialog = "synopsis" })
                }
            } else {
            val typeLabel = if (title.type == TmdbMediaType.MOVIE) "Фільм" else "Серіал"
            if (experimentalArtwork != null) {
                val metadata = listOfNotNull(
                    title.year?.toString(),
                    if (title.type == TmdbMediaType.MOVIE) "Фільм" else "Серіал",
                    countries.joinToString(", ").takeIf(String::isNotBlank),
                    title.rating?.let { "TMDB: ${String.format(Locale.US, "%.1f", it)}" },
                ).joinToString("  ·  ")
                if (metadata.isNotBlank()) Text(metadata, color = dayText,
                    style = MaterialTheme.typography.titleSmall)
            } else Text(typeLabel, color = dayText, style = MaterialTheme.typography.titleSmall)
            details.runtimeMinutes?.let { Text("Тривалість: $it хв", color = dayText) }
            if (title.type == TmdbMediaType.TV) {
                details.seasons?.let { Text("Сезонів: $it", color = dayText) }
                details.episodes?.let { Text("Серій: $it", color = dayText) }
            }
            if (details.genres.isNotEmpty()) {
                Text(details.genres.joinToString(" · "), color = dayText,
                    style = MaterialTheme.typography.bodySmall)
            }
            }
        }

        Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                if (favoritesLayout) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            if (experimentalArtwork?.logoUrl != null) {
                                FanartLogo(experimentalArtwork, title.title,
                                    Modifier.weight(1f, fill = false).widthIn(max = 460.dp).height(82.dp))
                            } else if (details.logoUrl != null) {
                                AsyncImage(model = details.logoUrl, contentDescription = title.title,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.weight(1f, fill = false).widthIn(max = 460.dp).height(82.dp))
                            } else {
                                Text(title.title, style = MaterialTheme.typography.headlineLarge,
                                    color = surfaceColors.text, fontWeight = FontWeight.Bold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Text(title.matchedSeasonTitle ?: title.ukrainianTitle ?: title.title,
                            style = MaterialTheme.typography.titleMedium, color = AppColors.TextLight,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            title.year?.let { Text(it.toString(), color = AppColors.TextSecondary) }
                            if (countries.isNotEmpty()) Text(countries.joinToString(", "), color = AppColors.TextSecondary)
                            title.rating?.let { Text("TMDB: ${String.format(Locale.US, "%.1f", it)}",
                                color = AppColors.TextSecondary) }
                        }
                    }
                } else if (experimentalArtwork != null) {
                    FanartLogo(experimentalArtwork, title.title,
                        Modifier.widthIn(max = 460.dp).height(82.dp))
                } else if (details.logoUrl != null) {
                    AsyncImage(model = details.logoUrl, contentDescription = title.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.widthIn(max = 460.dp).height(82.dp))
                } else {
                    Text(title.matchedSeasonTitle ?: title.ukrainianTitle ?: title.title,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.headlineLarge,
                        color = surfaceColors.text,
                        fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (!favoritesLayout) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!title.overview.isNullOrBlank()) {
                        TmdbInfoChip("Synopsis", surfaceColors) { infoDialog = "synopsis" }
                    }
                    if (details.directors.isNotEmpty() || details.cast.isNotEmpty()) {
                        TmdbInfoChip("Crew", surfaceColors) { infoDialog = "crew" }
                    }
                }
            }
            if (!favoritesLayout) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val secondaryTitle = title.englishTitle?.takeIf { it != title.title }
                    ?: title.originalTitle?.takeIf { it != title.title }
                secondaryTitle?.takeIf { experimentalArtwork == null }?.let {
                    Text(it, modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleMedium, color = AppColors.TextLight,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (fanartNotice != null) {
                Text(fanartNotice, color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall)
            }

            Row(
                modifier = Modifier.fillMaxWidth().background(surfaceColors.panel, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!torrentsOnly) OutlinedButton(
                    onClick = { onPanelSelected(if (torrentOpened) Panel.ONLINE else Panel.TORRENT) },
                    enabled = aniListAvailability != AniListAvailability.CHECKING &&
                        aniListAvailability != AniListAvailability.MISSING,
                    scale = OutlinedButtonDefaults.scale(scale = 1f, focusedScale = 1f),
                    shape = OutlinedButtonDefaults.shape(RoundedCornerShape(10.dp)),
                    border = OutlinedButtonDefaults.border(
                        border = Border(BorderStroke(1.dp, AppColors.TextMuted)),
                        focusedBorder = Border(BorderStroke(2.dp, AppColors.NeonCyan))
                    ),
                    colors = OutlinedButtonDefaults.colors(
                        containerColor = if (torrentOpened) AppColors.NeonCyan else surfaceColors.card,
                        contentColor = if (torrentOpened) AppColors.ButtonDarkText else surfaceColors.text,
                        focusedContainerColor = AppColors.NeonCyanLight,
                        focusedContentColor = AppColors.ButtonDarkText
                    ),
                    modifier = Modifier.focusRequester(torrentFocus)
                        .semantics { contentDescription = if (torrentOpened) "Перемкнутися на AniTube" else "Перемкнутися на Toloka" },
                ) { FontAwesomeIcon(FaIcons.ASTERISK, size = 18.sp) }

                if (torrentOpened) {
                    if (!torrentsOnly) Spacer(Modifier.width(16.dp))
                    TorrentTitleHeading(
                        ukrainianTitle = title.matchedSeasonTitle ?: title.ukrainianTitle ?: title.title,
                        englishTitle = title.englishTitle
                            ?: title.originalTitle?.takeIf { it != title.ukrainianTitle }
                            ?: title.title.takeIf { it != title.ukrainianTitle },
                        modifier = Modifier.weight(1f),
                        surfaceColors = surfaceColors,
                    )
                } else {
                    Spacer(Modifier.width(16.dp))
                    if (anitube is AnitubeState.Found) {
                        val candidate = anitube.catalog?.candidates?.firstOrNull {
                            it.release.newsId == anitube.release.newsId
                        }
                        val isFilm = title.type == TmdbMediaType.MOVIE ||
                            candidate?.kind == ua.any2b.tvplayer.data.AnitubeReleaseKind.FILM
                        val seasons = anitube.catalog?.seasons.orEmpty()
                            .filter { it.releases.isNotEmpty() }
                        val voice = anitube.voices.getOrNull(anitube.selectedVoiceIndex)
                        Box(Modifier.weight(1f)) {
                            AnitubePlaybackControls(
                                voices = anitube.voices,
                                selectedVoiceIndex = anitube.selectedVoiceIndex,
                                onSelectVoice = onSelectAnitubeVoice,
                                episodes = if (isFilm) emptyList() else voice?.episodes.orEmpty(),
                                selectedEpisodeIndex = anitube.selectedEpisodeIndex,
                                onSelectEpisode = onSelectAnitubeEpisode,
                                seasons = seasons.map { AnitubeSeasonOption(it.number, "S${it.number}") },
                                selectedSeasonId = candidate?.seasonNumber ?: seasons.firstOrNull()?.number,
                                onSelectSeason = onSelectAnitubeSeason,
                                isMovie = isFilm,
                                resolving = anitube.resolvingStream,
                                watchLabel = if (anitube.resumeProgress in 0.01f..0.98f)
                                    "Продовжити перегляд" else "Дивитися ${if (isFilm) "фільм" else "серію"}",
                                canWatch = voice?.episodes?.getOrNull(anitube.selectedEpisodeIndex) != null,
                                onWatch = onPlayAnitube,
                                surfaceColors = surfaceColors,
                                showWatch = true,
                            )
                        }
                    }
                }
            }
            if (torrentOpened) {
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                    .background(surfaceColors.panel, RoundedCornerShape(12.dp))) {
                    TolokaSearchPanel(
                        query = torrentQuery,
                        searchState = toloka.search,
                        transferState = toloka.transfer,
                        playbackStatus = toloka.playbackStatus,
                        openedTopics = openedTopics,
                        openedFiles = openedFiles,
                        onSearch = { onSearchTorrent(torrentQuery) },
                        onSelect = onSelectTorrent,
                        onPromote = onPromoteTorrent,
                        onDiscard = onDiscardTorrent,
                        onRejectForever = onRejectTorrent,
                        onRetryMetadata = onRetryMetadata,
                        onPlayFile = onPlayFile,
                        onEditQuery = { showQueryDialog = true },
                        manualSearch = false,
                        seasonEntries = details.seasonEntries,
                        surfaceColors = surfaceColors,
                    )
                }
            } else if (anitube !is AnitubeState.Found) {
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                    .background(surfaceColors.panel, RoundedCornerShape(12.dp))) {
                    AnitubeControllerPanel(
                        state = anitube,
                        isMovie = title.type == TmdbMediaType.MOVIE,
                        query = SeriesTitleSearch.clean(title.matchedSeasonTitle ?: title.title),
                        onRetry = onRetryAnitube,
                        onSelectRelease = onSelectAnitubeRelease,
                        onSelectSeason = onSelectAnitubeSeason,
                        onSelectVoice = onSelectAnitubeVoice,
                        onSelectEpisode = onSelectAnitubeEpisode,
                        onPlay = onPlayAnitube,
                        surfaceColors = surfaceColors,
                    )
                }
            } else {
                Spacer(Modifier.weight(1f))
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
internal fun TmdbInfoChip(label: String, surfaceColors: DetailsSurfaceColors,
    onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(percent = 50)
    Surface(onClick = onClick,
        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f),
        shape = ClickableSurfaceDefaults.shape(shape),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, AppColors.TextMuted), shape = shape),
            focusedBorder = Border.None),
        colors = ClickableSurfaceDefaults.colors(containerColor = surfaceColors.card,
            focusedContainerColor = AppColors.NeonCyanLight),
        modifier = Modifier.onFocusChanged { focused = it.isFocused }) {
        Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = if (focused) AppColors.ButtonDarkText else surfaceColors.text,
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

internal fun tmdbTorrentQueryOptions(details: TmdbDetails): List<Pair<String, String>> {
    val title = details.title
    val year = title.year?.toString()
    return buildList {
        fun addVariant(label: String, value: String) {
            add(label to value)
            if (year != null) add("$label + рік" to "$value $year")
        }
        fun addName(label: String, name: String?) {
            val value = name?.trim()?.takeIf(String::isNotBlank)
                ?.let(SeriesTitleSearch::clean)?.takeIf(String::isNotBlank) ?: return
            addVariant(label, value)
            shortenedSearchNames(value).forEach { short ->
                addVariant("$label · без уточнення", short)
            }
        }
        addName("Українська", listOfNotNull(title.ukrainianTitle, title.title).firstOrNull { name ->
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
    modifier: Modifier = Modifier, surfaceColors: DetailsSurfaceColors,
    onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> AppColors.NeonCyanLight
        selected -> AppColors.NeonCyan
        else -> surfaceColors.card
    }
    val foreground = if (focused || selected) AppColors.ButtonDarkText else surfaceColors.text
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
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth(0.82f).widthIn(max = 1100.dp)
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
                        items(state.titles,
                            key = { "${it.type}:${it.id}:${it.matchedSeasonNumber}" }) { item ->
                            TmdbPosterCard(item, onClick = { onOpen(item) })
                        }
                    }
                }
            }
            OutlinedButton(onClick = onDismiss) { Text("Закрити") }
        }
    }
}
