package ua.any2b.tvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.tv.material3.*
import androidx.compose.ui.tooling.preview.Preview
import coil.compose.AsyncImage
import ua.any2b.core.anilist.AnimeDetails
import ua.any2b.core.anilist.AnimeCard
import ua.any2b.core.anilist.Title
import ua.any2b.core.anilist.NumberedSeason
import ua.any2b.core.anilist.groupNumberedTvSeasons
import ua.any2b.core.anitube.AnitubeEpisode
import ua.any2b.core.anitube.AnitubeSearchResult
import ua.any2b.core.anitube.AnitubeVoice
import ua.any2b.core.toloka.TolokaHit
import ua.any2b.core.toloka.shortenedSearchNames
import ua.any2b.core.tmdb.TmdbTitle
import ua.any2b.core.fanart.FanartArtwork

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun DetailsScreen(
    state: DetailsState,
    experimentalArtwork: FanartArtwork? = null,
    experimentalPalette: FanartPalette? = null,
    fanartNotice: String? = null,
    onRetry: () -> Unit,
    onRetryVideoSource: () -> Unit,
    onSelectRelease: (AnitubeSearchResult) -> Unit,
    onManualSearch: (String) -> Unit,
    onResetManualSearch: () -> Unit,
    onSelectTmdb: (TmdbTitle) -> Unit,
    onFindTmdbCandidates: () -> Unit,
    onTolokaSearch: (String) -> Unit,
    onAutoTolokaSearch: (String, String?) -> Unit,
    onSelectTolokaHit: (String) -> Unit,
    onRetryTolokaMetadata: () -> Unit,
    onPlayTolokaFile: (Int) -> Unit,
    onToggleFavorite: () -> Unit,
    onSelectVoice: (Int) -> Unit,
    onSelectEpisode: (Int) -> Unit,
    onOpenSeason: (Int) -> Unit,
    onWatchClick: () -> Unit,
    onTrailerClick: () -> Unit,
    onBackClick: () -> Unit,
    onOpenRelated: (AnimeCard) -> Unit,
    onOpenGenre: (String) -> Unit,
    onMoreRelated: () -> Unit,
    onMoreRecommendations: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (experimentalArtwork != null)
                experimentalPalette?.background ?: AppColors.BackgroundDark else AppColors.BackgroundDark)
            .padding(32.dp)
    ) {
        when (state) {
            DetailsState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Завантаження деталей тайтла...", style = MaterialTheme.typography.titleMedium, color = AppColors.AccentLight)
                }
            }
            is DetailsState.Error -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    var isRetryFocused by remember { mutableStateOf(false) }
                    Text(state.message, color = Color.Red, style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = onRetry,
                        scale = ButtonDefaults.scale(scale = 1f, focusedScale = 1f),
                        shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                        glow = ButtonDefaults.glow(glow = Glow.None, focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 10.dp)),
                        modifier = Modifier
                            .zIndex(if (isRetryFocused) 1f else 0f)
                            .onFocusChanged { isRetryFocused = it.isFocused }
                            .neonGlow(focused = isRetryFocused, borderRadius = 12.dp)
                    ) { Text("Повторити") }
                }
            }
            is DetailsState.Ready -> {
                val surfaceColors = detailsSurfaceColors(experimentalPalette, experimentalArtwork != null)
                val isMovie = state.details.format.equals("MOVIE", ignoreCase = true)
                val watchFocusRequester = remember(state.details.id) { FocusRequester() }
                var initialWatchFocusSet by remember(state.details.id) { mutableStateOf(false) }
                var showReleaseDialog by remember(state.details.id) { mutableStateOf(false) }
                var showManualDialog by remember(state.details.id) { mutableStateOf(false) }
                var showTmdbDialog by remember(state.details.id) { mutableStateOf(false) }
                var showRelatedDialog by remember(state.details.id) { mutableStateOf(false) }
                var showTolokaQueryDialog by remember(state.details.id) { mutableStateOf(false) }
                var showEpisodeDialog by remember(state.details.id) { mutableStateOf(false) }
                var showSynopsisDialog by remember(state.details.id) { mutableStateOf(false) }
                LaunchedEffect(showEpisodeDialog) {
                    if (!showEpisodeDialog && initialWatchFocusSet) watchFocusRequester.requestFocus()
                }
                var videoTab by rememberSaveable(state.details.id) { mutableStateOf("online") }
                val onlineUnavailable = state.videoSource is VideoSourceState.NotFound &&
                    state.tmdbLink !is TmdbLinkState.Loading &&
                    state.seriesSeasons.count { it.animeId != null } <= 1
                val displayVideoTab = if (onlineUnavailable) "torrent" else videoTab
                var automaticTorrentSearchStarted by remember(state.details.id) { mutableStateOf(false) }
                val anitubeUkrainianTitle = (state.videoSource as? VideoSourceState.Success)?.release?.title
                val tmdbUkrainianTitle = state.tmdbLink.ukrainianTitle
                val seriesTitle = (state.tmdbLink as? TmdbLinkState.Confirmed)?.title
                    ?.takeIf { it.type == ua.any2b.core.tmdb.TmdbMediaType.TV }
                    ?.let { it.ukrainianTitle ?: it.title }
                val seasonIds = state.seriesSeasons
                    .mapNotNull { it.animeId }.filter { it != state.details.id }.toSet()
                val relatedOutsideSeries = state.details.related.filterNot { it.id in seasonIds }
                val tolokaUkrainianTitle = anitubeUkrainianTitle ?: tmdbUkrainianTitle
                val customTolokaQueries = remember(state.details.id) { mutableStateListOf<String>() }
                val tolokaOptions = (customTolokaQueries.map { "Власний" to it } +
                    tolokaQueryOptions(state.details, anitubeUkrainianTitle, tmdbUkrainianTitle))
                    .distinctBy { it.second.lowercase() }
                var tolokaQueryCustomized by rememberSaveable(state.details.id) { mutableStateOf(false) }
                var tolokaQuery by rememberSaveable(state.details.id) {
                    mutableStateOf(tolokaOptions.firstOrNull()?.second.orEmpty())
                }
                LaunchedEffect(tolokaUkrainianTitle) {
                    if (!tolokaQueryCustomized && tolokaOptions.isNotEmpty()) {
                        tolokaQuery = tolokaOptions.first().second
                    }
                }
                LaunchedEffect(onlineUnavailable, state.details.id) {
                    if (onlineUnavailable) {
                        // TMDB can trigger one last AniTube retry immediately after resolving.
                        delay(250)
                        videoTab = "torrent"
                        if (!automaticTorrentSearchStarted && state.tolokaSearch is TolokaSearchState.Idle) {
                            automaticTorrentSearchStarted = true
                            val initial = tolokaOptions.firstOrNull()?.second.orEmpty()
                            if (initial.isNotBlank()) {
                                tolokaQuery = initial
                                onAutoTolokaSearch(initial, state.details.title.english)
                            }
                        }
                    }
                }
                LaunchedEffect(state.tolokaSearch, onlineUnavailable) {
                    val result = state.tolokaSearch as? TolokaSearchState.Results
                    if (onlineUnavailable && result != null) {
                        tolokaQuery = result.query
                        tolokaQueryCustomized = true
                    }
                }
                LaunchedEffect(videoTab, onlineUnavailable, state.details.id) {
                    if (videoTab == "torrent" && !onlineUnavailable) {
                        val currentQuery = if (tolokaQueryCustomized) tolokaQuery
                            else tolokaOptions.firstOrNull()?.second.orEmpty()
                        if (currentQuery.isNotBlank()) {
                            tolokaQuery = currentQuery
                            tolokaQueryCustomized = true
                            onTolokaSearch(currentQuery)
                        }
                    }
                }
                val releaseCandidates = when (val source = state.videoSource) {
                    is VideoSourceState.Ambiguous -> source.candidates
                    is VideoSourceState.Error -> source.candidates
                    else -> emptyList()
                }

                // Two-Column Layout: Left (Fixed Poster & Compact Action Buttons), Right (Fixed Header + Scrollable Tab Content)
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Left Column (Fixed / Pinned) - Poster & Compact Action Buttons row below
                    Column(
                        modifier = Modifier
                            .width(220.dp)
                            .fillMaxHeight()
                            .then(if (experimentalArtwork == null)
                                Modifier.verticalScroll(rememberScrollState()) else Modifier),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .then(if (experimentalArtwork == null)
                                    Modifier.clip(RoundedCornerShape(16.dp)) else Modifier)
                                .background(surfaceColors.panel)
                        ) {
                            if (experimentalArtwork != null) {
                                FanartPoster(experimentalArtwork, state.details.title.display,
                                    experimentalPalette?.background ?: AppColors.BackgroundDark,
                                    Modifier.fillMaxSize())
                            } else {
                                AsyncImage(
                                    model = state.details.cover,
                                    contentDescription = state.details.title.display,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Box(
                                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(80.dp)
                                    .background(Brush.verticalGradient(listOf(
                                        Color.Black.copy(alpha = 0.72f), Color.Transparent,
                                    )))
                            )
                            Column(
                                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                state.details.format?.let { MetadataBadge(it, Color.Black.copy(alpha = 0.62f), Color.White) }
                                state.details.status
                                    ?.takeUnless { isMovie && it.equals("FINISHED", ignoreCase = true) }
                                    ?.let { MetadataBadge(it, Color.Black.copy(alpha = 0.62f), Color.White) }
                            }
                            ReleaseBadge(
                                score = state.details.score,
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                            )
                        }

                        // Compact Action Buttons under poster in a single horizontal row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // 1. Favorite Button
                            var isFavFocused by remember { mutableStateOf(false) }
                            val favBg = when {
                                isFavFocused -> AppColors.NeonCyanLight
                                state.isFavorite -> AppColors.NeonCyan
                                else -> surfaceColors.card
                            }
                            val favText = when {
                                isFavFocused -> AppColors.ButtonDarkText
                                state.isFavorite -> AppColors.ButtonDarkText
                                else -> surfaceColors.text
                            }
                            OutlinedButton(
                                onClick = onToggleFavorite,
                                scale = OutlinedButtonDefaults.scale(scale = 1f, focusedScale = 1f),
                                shape = OutlinedButtonDefaults.shape(RoundedCornerShape(10.dp)),
                                border = OutlinedButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                                glow = OutlinedButtonDefaults.glow(
                                    glow = Glow.None,
                                    focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 8.dp)
                                ),
                                colors = OutlinedButtonDefaults.colors(
                                    containerColor = favBg,
                                    contentColor = favText,
                                    focusedContainerColor = favBg,
                                    focusedContentColor = favText
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .zIndex(if (isFavFocused) 1f else 0f)
                                    .onFocusChanged { isFavFocused = it.isFocused }
                                    .neonGlow(focused = isFavFocused, borderRadius = 10.dp)
                                    .semantics { contentDescription = if (state.isFavorite) "Вилучити з обраного" else "Додати в обране" }
                            ) {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    FontAwesomeIcon(FaIcons.BOOKMARK, color = favText, size = 18.sp)
                                }
                            }

                            // 2. Trailer Button
                            var isTrailerFocused by remember { mutableStateOf(false) }
                            val trailerBg = if (isTrailerFocused) AppColors.NeonCyanLight else surfaceColors.card
                            val trailerText = if (isTrailerFocused) AppColors.ButtonDarkText else surfaceColors.text
                            OutlinedButton(
                                onClick = onTrailerClick,
                                scale = OutlinedButtonDefaults.scale(scale = 1f, focusedScale = 1f),
                                shape = OutlinedButtonDefaults.shape(RoundedCornerShape(10.dp)),
                                border = OutlinedButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                                glow = OutlinedButtonDefaults.glow(
                                    glow = Glow.None,
                                    focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 8.dp)
                                ),
                                colors = OutlinedButtonDefaults.colors(
                                    containerColor = trailerBg,
                                    contentColor = trailerText,
                                    focusedContainerColor = trailerBg,
                                    focusedContentColor = trailerText
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .zIndex(if (isTrailerFocused) 1f else 0f)
                                    .onFocusChanged { isTrailerFocused = it.isFocused }
                                    .neonGlow(focused = isTrailerFocused, borderRadius = 10.dp)
                                    .semantics { contentDescription = "Відкрити трейлер" }
                            ) {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    FontAwesomeIcon(FaIcons.FILM, color = trailerText, size = 18.sp)
                                }
                            }

                            // 3. Related Button ("Пов'язане")
                            var isRelatedFocused by remember { mutableStateOf(false) }
                            val relatedBg = if (isRelatedFocused) AppColors.NeonCyanLight else surfaceColors.card
                            val relatedText = if (isRelatedFocused) AppColors.ButtonDarkText else surfaceColors.text
                            OutlinedButton(
                                onClick = { showRelatedDialog = true },
                                enabled = relatedOutsideSeries.isNotEmpty(),
                                scale = OutlinedButtonDefaults.scale(scale = 1f, focusedScale = 1f),
                                shape = OutlinedButtonDefaults.shape(RoundedCornerShape(10.dp)),
                                border = OutlinedButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                                glow = OutlinedButtonDefaults.glow(
                                    glow = Glow.None,
                                    focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 8.dp)
                                ),
                                colors = OutlinedButtonDefaults.colors(
                                    containerColor = relatedBg,
                                    contentColor = relatedText,
                                    focusedContainerColor = relatedBg,
                                    focusedContentColor = relatedText
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .zIndex(if (isRelatedFocused) 1f else 0f)
                                    .onFocusChanged { isRelatedFocused = it.isFocused }
                                    .neonGlow(focused = isRelatedFocused, borderRadius = 10.dp)
                                    .semantics { contentDescription = "Пов’язані релізи" }
                            ) {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    FontAwesomeIcon(FaIcons.LINK, color = relatedText, size = 18.sp)
                                }
                            }
                        }
                    }

                    // Right Column: Fixed Header + Scrollable Tab Content
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Fixed title block above the scrolling source content.
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            val tmdbTitle = when (val link = state.tmdbLink) {
                                is TmdbLinkState.Confirmed -> link.title.matchedSeasonTitle
                                    ?: link.title.ukrainianTitle ?: link.title.title
                                is TmdbLinkState.Candidates -> link.suggested?.let { candidate ->
                                    candidate.matchedSeasonTitle ?: candidate.ukrainianTitle
                                        ?: candidate.title
                                }
                                else -> null
                            }
                            val sourceTitle = anitubeUkrainianTitle ?: tmdbTitle
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    if (experimentalArtwork != null) {
                                        FanartLogo(experimentalArtwork, state.details.title.display,
                                            Modifier.widthIn(max = 460.dp).height(82.dp)
                                                .combinedClickable(
                                                    onClick = {
                                                        onResetManualSearch()
                                                        showManualDialog = true
                                                    },
                                                    onLongClick = {
                                                        onFindTmdbCandidates()
                                                        showTmdbDialog = true
                                                    },
                                                ).semantics {
                                                    contentDescription = "${state.details.title.display}. OK — змінити реліз AniTube; утримувати — уточнити TMDB"
                                                })
                                    } else Text(
                                        text = seriesTitle ?: state.details.title.display,
                                        modifier = Modifier.weight(1f, fill = false)
                                            .then(if (sourceTitle == null) Modifier.combinedClickable(
                                                onClick = {
                                                    onResetManualSearch()
                                                    showManualDialog = true
                                                },
                                                onLongClick = {
                                                    if (state.tmdbLink is TmdbLinkState.Error) onFindTmdbCandidates()
                                                    showTmdbDialog = true
                                                },
                                            ) else Modifier),
                                        style = MaterialTheme.typography.headlineLarge,
                                        color = surfaceColors.text,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    ((state.tmdbLink as? TmdbLinkState.Confirmed)?.title
                                        ?.takeIf { it.type == ua.any2b.core.tmdb.TmdbMediaType.TV }?.year
                                        ?: state.details.seasonYear)?.let { year ->
                                        Text("$year", color = if (experimentalArtwork != null &&
                                            experimentalPalette?.light == true) Color(0xFF465466)
                                            else AppColors.TextSecondary, fontSize = 16.sp)
                                    }
                                }
                                if (sourceTitle != null && experimentalArtwork == null) {
                                    val displayTitle = if (anitubeUkrainianTitle != null) sourceTitle
                                        else "$sourceTitle · TMDB"
                                    var sourceTitleFocused by remember { mutableStateOf(false) }
                                    Text(
                                        text = displayTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (sourceTitleFocused) AppColors.NeonCyan else AppColors.TextLight,
                                        modifier = Modifier
                                            .onFocusChanged { sourceTitleFocused = it.isFocused }
                                            .combinedClickable(
                                                onClick = {
                                                    onResetManualSearch()
                                                    showManualDialog = true
                                                },
                                                onLongClick = {
                                                    if (state.tmdbLink is TmdbLinkState.Confirmed ||
                                                        state.tmdbLink is TmdbLinkState.Error) {
                                                        onFindTmdbCandidates()
                                                    }
                                                    showTmdbDialog = true
                                                },
                                            )
                                            .semantics {
                                                contentDescription = "Назва релізу: $displayTitle. OK — змінити реліз AniTube; утримувати — уточнити TMDB"
                                            },
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (fanartNotice != null) {
                                    Text(fanartNotice, color = AppColors.TextSecondary,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (!state.details.description.isNullOrBlank()) {
                                TmdbInfoChip("Synopsis", surfaceColors) { showSynopsisDialog = true }
                            }

                        }

                        if (state.details.genres.isNotEmpty()) {
                            DetailsGenreTags(state.details.genres, onOpenGenre,
                                light = experimentalArtwork != null && experimentalPalette?.light == true)
                        }

                        // SCROLLABLE TAB CONTENT AREA
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Monolithic Section Container (Header with Segmented Control Tabs + Content)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(surfaceColors.panel, RoundedCornerShape(12.dp))
                                    .padding(bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Panel Header Bar with Tabs
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(surfaceColors.card, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (onlineUnavailable) {
                                        SegmentButton("Торент", selected = true,
                                            surfaceColors = surfaceColors,
                                            icon = FaIcons.ASTERISK) { videoTab = "torrent" }
                                    } else {
                                        SegmentedControl(selectedTab = videoTab,
                                            surfaceColors = surfaceColors) { tab -> videoTab = tab }
                                    }
                                    if (displayVideoTab == "torrent") {
                                        var queryFocused by remember { mutableStateOf(false) }
                                        Text(
                                            text = tolokaQuery,
                                            color = if (queryFocused) AppColors.NeonCyanLight else AppColors.TextLight,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(start = 16.dp)
                                                .onFocusChanged { queryFocused = it.isFocused }
                                                .clickable { showTolokaQueryDialog = true }
                                                .semantics { contentDescription = "Змінити пошуковий запит Toloka: $tolokaQuery" },
                                        )
                                    }
                                }

                                // Content directly below without gaps
                                if (displayVideoTab == "online") {
                                    // Watch Progress & Compact Watch Button inside Online tab
                                    if (state.historyEntry != null) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = if (isMovie) {
                                                    "Прогрес перегляду: ${(state.historyEntry.progress * 100).toInt()}%"
                                                } else {
                                                    "Переглянуто: серія ${state.historyEntry.episodeNumber}"
                                                },
                                                color = AppColors.TextSecondary,
                                                fontSize = 11.sp
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(AppColors.SurfaceCard)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(state.historyEntry.progress.coerceIn(0f, 1f))
                                                        .fillMaxHeight()
                                                        .background(AppColors.AccentLight)
                                                )
                                            }
                                        }
                                    }

                                    when (val source = state.videoSource) {
                                        VideoSourceState.NotFound -> Unit
                                        VideoSourceState.Loading -> Row(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                                listOf(92.dp, 66.dp).forEach { width ->
                                                    Box(
                                                        modifier = Modifier.width(width).height(7.dp)
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(AppColors.SkeletonBg)
                                                    )
                                                }
                                            }
                                            Text("Шукаємо озвучки на AniTube...", color = AppColors.TextLight, fontSize = 13.sp)
                                        }
                                        is VideoSourceState.Ambiguous -> VideoSourceNotice(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            message = if (source.candidates.size == 1) {
                                                "Перевірте знайдений реліз перед відтворенням"
                                            } else {
                                                "Знайдено кілька схожих релізів, виберіть потрібний"
                                            },
                                            action = "Вибрати реліз",
                                            onClick = { showReleaseDialog = true }
                                        )
                                        is VideoSourceState.Error -> VideoSourceNotice(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            message = "Не вдалося завантажити відео з AniTube",
                                            detail = source.message,
                                            action = if (source.candidates.isNotEmpty()) "Вибрати вручну" else "Повторити спробу",
                                            onClick = if (source.candidates.isNotEmpty()) {
                                                { showReleaseDialog = true }
                                            } else onRetryVideoSource
                                        )
                                        is VideoSourceState.Success -> {
                                            val groups = remember(source.voices) { voiceCategoryGroups(source.voices) }
                                            val selectedGroup = groups.firstOrNull { group ->
                                                group.variants.any { it.index == state.selectedVoiceIndex }
                                            }
                                            val rememberedVariants = remember(state.details.id, source.release.newsId, source.voices) {
                                                mutableStateMapOf<String, Int>().apply {
                                                    if (selectedGroup != null) put(selectedGroup.key, state.selectedVoiceIndex)
                                                }
                                            }
                                            var menuGroup by remember(state.details.id, source.release.newsId, source.voices) {
                                                mutableStateOf<VoiceCategoryGroup?>(null)
                                            }
                                            val selectedEpisode = state.voices.getOrNull(state.selectedVoiceIndex)
                                                ?.episodes?.getOrNull(state.selectedEpisodeIndex)
                                            val canWatch = selectedEpisode != null && !state.resolvingStream
                                            LaunchedEffect(canWatch) {
                                                if (canWatch && !initialWatchFocusSet) {
                                                    watchFocusRequester.requestFocus()
                                                    initialWatchFocusSet = true
                                                }
                                            }
                                            val canContinue = state.historyEntry?.let { history ->
                                                history.voiceIndex == state.selectedVoiceIndex &&
                                                    history.episodeIndex == state.selectedEpisodeIndex &&
                                                    history.progress in 0.01f..0.98f
                                            } == true
                                            val buttonText = when {
                                                state.resolvingStream -> "Підготовка..."
                                                canContinue -> "Продовжити перегляд"
                                                isMovie -> "Дивитися фільм"
                                                else -> "Дивитися серію"
                                            }
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    var isWatchFocused by remember { mutableStateOf(false) }
                                                    val watchBg = if (isWatchFocused) AppColors.NeonCyanLight else surfaceColors.card
                                                    val watchText = if (isWatchFocused) AppColors.ButtonDarkText else surfaceColors.text
                                                    Button(
                                                        onClick = onWatchClick,
                                                        enabled = canWatch,
                                                        scale = ButtonDefaults.scale(scale = 1f, focusedScale = 1f),
                                                        shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                                                        glow = ButtonDefaults.glow(
                                                            glow = Glow.None,
                                                            focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 8.dp),
                                                        ),
                                                        colors = ButtonDefaults.colors(
                                                            containerColor = watchBg,
                                                            contentColor = watchText,
                                                            focusedContainerColor = watchBg,
                                                            focusedContentColor = watchText,
                                                        ),
                                                        modifier = Modifier
                                                            .height(38.dp)
                                                            .focusRequester(watchFocusRequester)
                                                            .zIndex(if (isWatchFocused) 1f else 0f)
                                                            .onFocusChanged { isWatchFocused = it.isFocused }
                                                            .neonGlow(focused = isWatchFocused, borderRadius = 10.dp),
                                                    ) {
                                                        Text(buttonText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                LazyRow(
                                                    contentPadding = PaddingValues(vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.weight(1f).graphicsLayer { clip = false }
                                                ) {
                                                    itemsIndexed(groups) { _, group ->
                                                        var isStudioFocused by remember { mutableStateOf(false) }
                                                        val isSelected = group.key == selectedGroup?.key
                                                        val displayedIndex = if (isSelected) state.selectedVoiceIndex
                                                            else rememberedVariants[group.key] ?: group.preferredIndex
                                                        val displayedVoice = group.variants.firstOrNull { it.index == displayedIndex }
                                                            ?.value ?: group.variants.first().value
                                                        val bg = when {
                                                            isStudioFocused -> AppColors.NeonCyanLight
                                                            isSelected -> AppColors.NeonCyan
                                                            else -> AppColors.SurfaceCard
                                                        }
                                                        val textColor = when {
                                                            isStudioFocused -> AppColors.ButtonDarkText
                                                            isSelected -> AppColors.ButtonDarkText
                                                            else -> AppColors.TextSecondary
                                                        }

                                                        Card(
                                                            onClick = {
                                                                if (isSelected && group.variants.size > 1) {
                                                                    menuGroup = group
                                                                } else {
                                                                    val index = rememberedVariants[group.key] ?: group.preferredIndex
                                                                    rememberedVariants[group.key] = index
                                                                    onSelectVoice(index)
                                                                }
                                                            },
                                                            onLongClick = { if (group.variants.size > 1) menuGroup = group },
                                                            scale = CardDefaults.scale(scale = 1f, focusedScale = 1f),
                                                            shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
                                                            border = CardDefaults.border(border = Border.None, focusedBorder = Border.None),
                                                            glow = CardDefaults.glow(
                                                                glow = Glow.None,
                                                                focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 8.dp)
                                                            ),
                                                            colors = CardDefaults.colors(
                                                                containerColor = bg,
                                                                focusedContainerColor = bg
                                                            ),
                                                            modifier = Modifier
                                                                .zIndex(if (isStudioFocused) 1f else 0f)
                                                                .onFocusChanged { isStudioFocused = it.isFocused }
                                                                .neonGlow(focused = isStudioFocused, borderRadius = 10.dp)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier
                                                                    .heightIn(min = 32.dp)
                                                                    .padding(horizontal = 10.dp),
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                            ) {
                                                                Text(
                                                                    text = voiceButtonLabel(group, displayedVoice),
                                                                    color = textColor,
                                                                    fontSize = 11.sp,
                                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                                )
                                                                if (group.variants.size > 1) {
                                                                    FontAwesomeIcon(FaIcons.ANGLE_DOWN, color = textColor, size = 11.sp)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                }
                                            }
                                            menuGroup?.let { group ->
                                                StudioVoiceDialog(
                                                    group = group,
                                                    selectedVoiceIndex = state.selectedVoiceIndex,
                                                    onDismiss = { menuGroup = null },
                                                    onSelect = { index ->
                                                        rememberedVariants[group.key] = index
                                                        onSelectVoice(index)
                                                        menuGroup = null
                                                    },
                                                )
                                            }
                                        }
                                    }

                                    // Season and episode choices stay together below playback.
                                    val currentVoice = if (state.videoSource is VideoSourceState.Success)
                                        state.voices.getOrNull(state.selectedVoiceIndex) else null
                                    val numbered = state.seriesSeasons
                                    if (!isMovie && (numbered.isNotEmpty() ||
                                            currentVoice?.episodes?.isNotEmpty() == true)) {
                                        val currentSeason = numbered.firstOrNull { it.animeId == state.details.id }
                                        OutlinedButton(
                                            onClick = { showEpisodeDialog = true },
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                        ) {
                                            val episode = currentVoice?.episodes?.getOrNull(state.selectedEpisodeIndex)
                                            Text((currentSeason?.let { "Сезон ${it.number} · " } ?: "") +
                                                (episode?.let { "Серія ${it.episodeNumber}" } ?: "Вибрати серію") + "  ⌄")
                                        }
                                    }
                                } else {
                                    TolokaSearchPanel(
                                        query = tolokaQuery,
                                        searchState = state.tolokaSearch,
                                        transferState = state.tolokaTransfer,
                                        playbackStatus = state.tolokaPlaybackStatus,
                                        openedTopics = state.tolokaOpenedTopics,
                                        openedFiles = state.tolokaOpenedFiles,
                                        onSearch = { onTolokaSearch(tolokaQuery) },
                                        onSelect = onSelectTolokaHit,
                                        onRetryMetadata = onRetryTolokaMetadata,
                                        onPlayFile = onPlayTolokaFile,
                                        surfaceColors = surfaceColors,
                                    )
                                }
                            }

                            // Recommendations section in scroll area
                            if (state.details.recommendations.isNotEmpty()) {
                                DetailsTitleRow(
                                    title = "Рекомендації",
                                    light = experimentalArtwork != null && experimentalPalette?.light == true,
                                    items = groupNumberedTvSeasons(state.details.recommendations),
                                    showRelatedBadge = false,
                                    showMore = groupNumberedTvSeasons(state.details.recommendations).size > 13 ||
                                        state.details.recommendationsHasMore,
                                    onOpen = onOpenRelated,
                                    onMore = onMoreRecommendations,
                                )
                            }
                        }
                    }
                }

                // Related Dialog ("Пов'язане")
                if (showSynopsisDialog) {
                    Dialog(onDismissRequest = { showSynopsisDialog = false }) {
                        Column(Modifier.width(680.dp).heightIn(max = 560.dp)
                            .background(surfaceColors.panel, RoundedCornerShape(12.dp))
                            .verticalScroll(rememberScrollState()).padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Синопсис", color = surfaceColors.text,
                                style = MaterialTheme.typography.titleLarge)
                            Text(state.details.description.orEmpty(), color = surfaceColors.mutedText)
                            OutlinedButton(onClick = { showSynopsisDialog = false }) { Text("Назад") }
                        }
                    }
                }
                if (showEpisodeDialog) {
                    val voice = state.voices.getOrNull(state.selectedVoiceIndex)
                    SeasonEpisodeDialog(
                        seasons = state.seriesSeasons,
                        currentAnimeId = state.details.id,
                        episodes = voice?.episodes.orEmpty(),
                        selectedEpisodeIndex = state.selectedEpisodeIndex,
                        onDismiss = { showEpisodeDialog = false },
                        onEpisode = { index ->
                            onSelectEpisode(index)
                            showEpisodeDialog = false
                        },
                        onSeason = { id ->
                            showEpisodeDialog = false
                            onOpenSeason(id)
                        },
                    )
                }
                if (showRelatedDialog) {
                    RelatedDialog(
                        related = relatedOutsideSeries,
                        onDismiss = { showRelatedDialog = false },
                        onOpen = { card ->
                            showRelatedDialog = false
                            onOpenRelated(card)
                        }
                    )
                }

                if (showTolokaQueryDialog) {
                    TolokaQueryDialog(
                        options = tolokaOptions,
                        selectedQuery = tolokaQuery,
                        onDismiss = { showTolokaQueryDialog = false },
                        onSelect = { selected ->
                            showTolokaQueryDialog = false
                            tolokaQuery = selected
                            tolokaQueryCustomized = true
                            onTolokaSearch(selected)
                        },
                        onEdited = { edited ->
                            showTolokaQueryDialog = false
                            customTolokaQueries.remove(edited)
                            customTolokaQueries.add(0, edited)
                            tolokaQuery = edited
                            tolokaQueryCustomized = true
                            onTolokaSearch(edited)
                        },
                    )
                }

                if (showReleaseDialog && releaseCandidates.isNotEmpty()) {
                    ReleaseSelectionDialog(
                        candidates = releaseCandidates,
                        onDismiss = { showReleaseDialog = false },
                        onSelect = { candidate ->
                            showReleaseDialog = false
                            onSelectRelease(candidate)
                        }
                    )
                }
                if (showManualDialog) {
                    ManualReleaseSearchDialog(
                        searchState = state.manualReleaseSearch,
                        initialQuery = (state.videoSource as? VideoSourceState.Success)?.release?.title.orEmpty(),
                        onSearch = onManualSearch,
                        onDismiss = { showManualDialog = false },
                        onSelect = { candidate ->
                            showManualDialog = false
                            onSelectRelease(candidate)
                        }
                    )
                }
                if (showTmdbDialog) {
                    TmdbMatchDialog(state.tmdbLink,
                        onDismiss = { showTmdbDialog = false },
                        onRetry = onFindTmdbCandidates,
                        onSelect = { selected ->
                            showTmdbDialog = false
                            onSelectTmdb(selected)
                        })
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TmdbMatchDialog(
    link: TmdbLinkState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onSelect: (TmdbTitle) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(600.dp).heightIn(max = 620.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(AppColors.SurfaceDark).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Уточнити назву TMDB", style = MaterialTheme.typography.titleLarge,
                color = AppColors.TextLight)
            Text("Виберіть відповідний тайтл. Зв’язок збережеться після підтвердження.",
                color = AppColors.TextSecondary)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (link) {
                    TmdbLinkState.Loading -> Text("Пошук кандидатів…", color = AppColors.TextLight)
                    is TmdbLinkState.Error -> Text(link.message, color = AppColors.ErrorRed)
                    is TmdbLinkState.Confirmed -> Text("Пошук кандидатів…", color = AppColors.TextLight)
                    is TmdbLinkState.Candidates -> {
                        if (link.matches.isEmpty()) Text("Відповідників не знайдено", color = AppColors.TextSecondary)
                        link.matches.forEach { match ->
                            val title = match.title
                            val secondary = listOfNotNull(title.englishTitle,
                                title.originalTitle).distinct().filter { it != title.ukrainianTitle }
                                .take(2).joinToString(" · ")
                            Button(onClick = { onSelect(title) }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(title.matchedSeasonTitle ?: title.ukrainianTitle ?: title.title,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (secondary.isNotBlank()) Text(secondary,
                                        style = MaterialTheme.typography.labelSmall)
                                    Text("${if (title.type == ua.any2b.core.tmdb.TmdbMediaType.MOVIE) "Фільм" else "TV"} · " +
                                        "${title.matchedSeasonYear ?: title.year ?: "рік невідомий"}" +
                                        if (16 in title.genreIds) " · Animation" else "",
                                        style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRetry) { Text("Шукати знову") }
                Button(onClick = onDismiss) { Text("Закрити") }
            }
        }
    }
}

@Composable
private fun SegmentedControl(
    selectedTab: String,
    surfaceColors: DetailsSurfaceColors,
    onTabSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(surfaceColors.card)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        SegmentButton("Онлайн", selected = selectedTab == "online", surfaceColors = surfaceColors) {
            onTabSelected("online")
        }
        SegmentButton("Торент", selected = selectedTab == "torrent",
            surfaceColors = surfaceColors, icon = FaIcons.ASTERISK) {
            onTabSelected("torrent")
        }
    }
}

@Composable
private fun SegmentButton(label: String, selected: Boolean,
    surfaceColors: DetailsSurfaceColors, icon: String? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> AppColors.NeonCyanLight
        selected -> AppColors.NeonCyan
        else -> Color.Transparent
    }
    val textColor = if (selected || focused) AppColors.ButtonDarkText else surfaceColors.text

    Surface(
        onClick = onClick,
        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
        glow = ClickableSurfaceDefaults.glow(glow = Glow.None, focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 6.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = bg,
            focusedContainerColor = bg
        ),
        modifier = Modifier
            .zIndex(if (focused) 1f else 0f)
            .onFocusChanged { focused = it.isFocused }
            .neonGlow(focused = focused, borderRadius = 8.dp)
            .semantics { contentDescription = label }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) FontAwesomeIcon(icon, color = textColor, size = 14.sp)
            else Text(label, color = textColor, fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
private fun RelatedDialog(
    related: List<AnimeCard>,
    onDismiss: () -> Unit,
    onOpen: (AnimeCard) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(720.dp)
                .heightIn(max = 600.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(AppColors.SurfaceDark)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Пов’язані релізи", color = Color.White, style = MaterialTheme.typography.titleLarge)
            LazyRow(
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.graphicsLayer { clip = false }
            ) {
                itemsIndexed(related, key = { _, card -> card.id }) { _, card ->
                    PosterCard(
                        item = card,
                        modifier = Modifier.width(160.dp),
                        showRelatedBadge = true,
                        onClick = { onOpen(card) },
                    )
                }
            }
            OutlinedButton(
                onClick = onDismiss,
                scale = OutlinedButtonDefaults.scale(scale = 1f, focusedScale = 1f),
                shape = OutlinedButtonDefaults.shape(RoundedCornerShape(12.dp)),
                border = OutlinedButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                glow = OutlinedButtonDefaults.glow(
                    glow = Glow.None,
                    focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 10.dp)
                ),
                colors = OutlinedButtonDefaults.colors(
                    containerColor = AppColors.SurfaceCard,
                    contentColor = Color.White,
                    focusedContainerColor = AppColors.SurfaceCard,
                    focusedContentColor = AppColors.NeonCyan
                )
            ) { Text("Закрити") }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun TolokaSearchPanel(
    query: String,
    searchState: TolokaSearchState,
    transferState: TolokaTransferState,
    playbackStatus: String?,
    openedTopics: Set<String>,
    openedFiles: Set<Int>,
    onSearch: () -> Unit,
    onSelect: (String) -> Unit,
    onRetryMetadata: () -> Unit,
    onPlayFile: (Int) -> Unit,
    manualSearch: Boolean = false,
    surfaceColors: DetailsSurfaceColors = detailsSurfaceColors(null, false),
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (searchState) {
            TolokaSearchState.Idle -> Text(if (manualSearch) "Введіть назву й натисніть «Шукати»." else "Пошук роздач почнеться автоматично.",
                color = surfaceColors.mutedText)
            TolokaSearchState.Loading -> Text("Шукаємо роздачі…", color = surfaceColors.text)
            is TolokaSearchState.Error -> {
                Text(searchState.message, color = AppColors.ErrorRed)
                Button(onClick = onSearch) { Text("Повторити пошук") }
            }
            is TolokaSearchState.Results -> {
                var showWeak by rememberSaveable(searchState.query) { mutableStateOf(false) }
                if (searchState.query != query.trim()) {
                    Text("Пошук за новим запитом…",
                        color = AppColors.TextSecondary)
                } else {
                    if (searchState.hits.isEmpty()) Text(
                        if (searchState.weakHits.isNotEmpty()) "Точних збігів немає. Можна переглянути сумнівні роздачі нижче."
                        else if (manualSearch) "Відеороздач за цим запитом не знайдено."
                        else "Підхожих роздач немає. Знайдено: ${searchState.totalFound}; відсіяно за назвою, роком або сезоном.",
                        color = surfaceColors.text)
                    if (searchState.hits.isNotEmpty()) Text(
                        "Підхожих роздач: ${searchState.hits.size} із ${searchState.totalFound}",
                        color = surfaceColors.mutedText, fontSize = 12.sp)
                    val selectedWeak = searchState.selectedId?.let { id ->
                        searchState.weakHits.any { it.id == id }
                    } == true
                    val visibleHits = searchState.hits +
                        if (showWeak || selectedWeak) searchState.weakHits else emptyList()
                    visibleHits.forEach { hit ->
                        TolokaResultRow(
                            hit = hit,
                            selected = hit.id == searchState.selectedId,
                            opened = hit.id in openedTopics,
                            enabled = transferState !is TolokaTransferState.Loading,
                            onClick = { onSelect(hit.id) },
                            surfaceColors = surfaceColors,
                        )
                        if (hit.id == searchState.selectedId) {
                            TolokaTransferContent(transferState, playbackStatus, openedFiles,
                                onSearch, onRetryMetadata, onPlayFile, surfaceColors)
                        }
                    }
                    if (searchState.weakHits.isNotEmpty()) {
                        Button(onClick = { showWeak = !showWeak },
                            modifier = Modifier.fillMaxWidth()) {
                            Text(if (showWeak || selectedWeak)
                                "Згорнути сумнівні збіги"
                            else "…  Показати ще ${searchState.weakHits.size} сумнівних збігів")
                        }
                    }
                }
            }
        }
    }
}

private fun tolokaQueryOptions(details: AnimeDetails, releaseTitle: String?,
    tmdbTitle: String?): List<Pair<String, String>> {
    val ukrainian = releaseTitle?.takeIf(String::isNotBlank)
        ?: tmdbTitle?.takeIf(String::isNotBlank)
        ?: details.synonyms.firstOrNull { it.contains(Regex("[іїєґІЇЄҐ]")) }
    val year = details.seasonYear?.toString()
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
        addName("Українська", ukrainian)
        if (releaseTitle != null && tmdbTitle != null && releaseTitle != tmdbTitle)
            addName("Українська TMDB", tmdbTitle)
        addName("English", details.title.english)
        addName("Romaji", details.title.romaji)
        addName("Native", details.title.native)
        details.synonyms.filter { it != ukrainian }.take(4).forEachIndexed { index, name ->
            addName("Синонім ${index + 1}", name)
        }
        if (isEmpty()) add("Назва" to details.title.display)
    }.distinctBy { it.second.lowercase() }
}

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
private fun TolokaQueryEditDialog(
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

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun TolokaTransferContent(
    transferState: TolokaTransferState,
    playbackStatus: String?,
    openedFiles: Set<Int>,
    onSearch: () -> Unit,
    onRetryMetadata: () -> Unit,
    onPlayFile: (Int) -> Unit,
    surfaceColors: DetailsSurfaceColors,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
    when (transferState) {
        TolokaTransferState.Idle -> Unit
        is TolokaTransferState.Loading -> {
            Text(transferState.step + "…", color = AppColors.TextLight)
            Text("Останній успішний етап: ${transferState.lastSuccess}", color = AppColors.TextSecondary,
                fontSize = 12.sp)
        }
        is TolokaTransferState.Error -> {
            Text(transferState.message, color = AppColors.ErrorRed)
            Text("Останній успішний етап: ${transferState.lastSuccess}", color = AppColors.TextSecondary,
                fontSize = 12.sp)
            if (transferState.repeatSearch) {
                Button(onClick = onSearch) { Text("Повторити пошук") }
            }
            if (transferState.retryMetadata) {
                Button(onClick = onRetryMetadata) { Text("Повторити читання файлів") }
            }
        }
        is TolokaTransferState.Files -> {
            val files = transferState.torrent.videoFiles
            val firstFileFocus = remember(transferState.torrent.hash) { FocusRequester() }
            val firstFileBringIntoView = remember(transferState.torrent.hash) { BringIntoViewRequester() }
            LaunchedEffect(transferState.torrent.hash, files.firstOrNull()?.id) {
                if (files.isNotEmpty()) {
                    firstFileFocus.requestFocus()
                    firstFileBringIntoView.bringIntoView()
                }
            }
            Text("Торент додано. Виберіть відеофайл для відтворення:", color = Color.White,
                style = MaterialTheme.typography.titleMedium)
            Text("Метадані отримано: ${transferState.torrent.files.size} файлів, відео: ${files.size}",
                color = AppColors.TextSecondary, fontSize = 12.sp)
            if (playbackStatus != null) Text(playbackStatus, color = AppColors.TextLight,
                fontSize = 12.sp)
            if (files.isEmpty()) {
                Text("У роздачі немає доступних відеофайлів.", color = AppColors.TextSecondary)
            } else {
                files.forEachIndexed { index, file ->
                    var focused by remember(file.id) { mutableStateOf(false) }
                    val opened = file.id in openedFiles
                    Card(
                        onClick = { onPlayFile(file.id) },
                        scale = CardDefaults.scale(scale = 1f, focusedScale = 1f),
                        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
                        colors = CardDefaults.colors(
                            containerColor = if (opened) surfaceColors.panel else surfaceColors.card,
                            focusedContainerColor = AppColors.NeonCyanLight,
                        ),
                        modifier = Modifier.fillMaxWidth()
                            .then(if (index == 0) Modifier
                                .bringIntoViewRequester(firstFileBringIntoView)
                                .focusRequester(firstFileFocus) else Modifier)
                            .onFocusChanged { focused = it.isFocused }
                            .neonGlow(focused = focused, borderRadius = 10.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(file.path, color = when {
                                focused -> AppColors.ButtonDarkText
                                else -> surfaceColors.text
                            }.let { if (opened) lerp(it, Color.Black, 0.3f) else it })
                            Text(formatFileSize(file.length), fontSize = 12.sp,
                                color = when {
                                    focused -> AppColors.ButtonDarkText
                                    else -> surfaceColors.mutedText
                                }.let { if (opened) lerp(it, Color.Black, 0.3f) else it })
                        }
                    }
                }
            }
        }
    }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.2f GB".format(java.util.Locale.ROOT, bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f MB".format(java.util.Locale.ROOT, bytes / 1_048_576.0)
    else -> "$bytes B"
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TolokaResultRow(hit: TolokaHit, selected: Boolean, opened: Boolean, enabled: Boolean,
    onClick: () -> Unit, surfaceColors: DetailsSurfaceColors) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> AppColors.NeonCyanLight
        selected -> AppColors.NeonCyan
        else -> surfaceColors.card
    }
    val foreground = if (focused || selected) AppColors.ButtonDarkText else surfaceColors.text
    Card(
        onClick = { if (enabled) onClick() },
        scale = CardDefaults.scale(scale = 1f, focusedScale = 1f),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        colors = CardDefaults.colors(containerColor = background, focusedContainerColor = background),
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
            .neonGlow(focused = focused, borderRadius = 10.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (opened) FontAwesomeIcon(FaIcons.EYE, color = foreground, size = 14.sp)
                Text(hit.title, color = foreground, fontWeight = FontWeight.Medium)
            }
            val details = buildList {
                if (hit.sizeText.isNotBlank()) add(hit.sizeText)
                hit.seeders?.let { add("Сіди: $it") }
                hit.leechers?.let { add("Піри: $it") }
            }.joinToString(" · ")
            if (details.isNotBlank() || hit.author != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (details.isNotBlank()) {
                        Text(details, modifier = Modifier.weight(1f), color = foreground,
                            fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else Spacer(Modifier.weight(1f))
                    hit.author?.let { author ->
                        Text(author, modifier = Modifier.widthIn(max = 170.dp), color = foreground,
                            fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsTitleRow(
    title: String,
    light: Boolean,
    items: List<AnimeCard>,
    showRelatedBadge: Boolean,
    showMore: Boolean,
    onOpen: (AnimeCard) -> Unit,
    onMore: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium,
            color = if (light) Color(0xFF263241) else AppColors.TextLight)
        LazyRow(
            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.graphicsLayer { clip = false }
        ) {
            itemsIndexed(items.take(13), key = { _, card -> card.id }) { _, card ->
                PosterCard(
                    item = card,
                    modifier = Modifier.width(160.dp),
                    showRelatedBadge = showRelatedBadge,
                    onClick = { onOpen(card) },
                )
            }
            if (showMore) item(key = "more") { MoreCard(onClick = onMore) }
        }
    }
}

@Composable
private fun ManualReleaseSearchDialog(
    searchState: ManualReleaseSearchState,
    initialQuery: String,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (AnitubeSearchResult) -> Unit,
) {
    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(620.dp)
                .heightIn(max = 600.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(AppColors.SurfaceDark)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Змінити реліз AniTube", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text("Введіть українську назву. Пошук виконується на AniTube.", color = AppColors.TextSecondary, fontSize = 13.sp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.SurfaceCard)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isBlank()) Text("Назва українською", color = AppColors.TextSecondary)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboardController?.hide()
                        onSearch(query)
                    }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                )
            }
            var isSearchBtnFocused by remember { mutableStateOf(false) }
            Button(
                onClick = { onSearch(query) },
                enabled = query.isNotBlank(),
                scale = ButtonDefaults.scale(scale = 1f, focusedScale = 1f),
                shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                glow = ButtonDefaults.glow(glow = Glow.None, focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 10.dp)),
                modifier = Modifier
                    .zIndex(if (isSearchBtnFocused) 1f else 0f)
                    .onFocusChanged { isSearchBtnFocused = it.isFocused }
                    .neonGlow(focused = isSearchBtnFocused, borderRadius = 12.dp)
            ) { Text("Шукати на AniTube") }
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (searchState) {
                    ManualReleaseSearchState.Idle -> Unit
                    ManualReleaseSearchState.Loading -> Text("Шукаємо релізи...", color = AppColors.TextLight)
                    is ManualReleaseSearchState.Error -> Text(searchState.message, color = AppColors.ErrorRed)
                    is ManualReleaseSearchState.Results -> {
                        if (searchState.candidates.isEmpty()) {
                            Text("На AniTube нічого не знайдено", color = AppColors.TextLight)
                        } else {
                            searchState.candidates.forEach { candidate ->
                                var isCandFocused by remember { mutableStateOf(false) }
                                Button(
                                    onClick = { onSelect(candidate) },
                                    scale = ButtonDefaults.scale(scale = 1f, focusedScale = 1f),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                                    glow = ButtonDefaults.glow(glow = Glow.None, focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 10.dp)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .zIndex(if (isCandFocused) 1f else 0f)
                                        .onFocusChanged { isCandFocused = it.isFocused }
                                        .neonGlow(focused = isCandFocused, borderRadius = 12.dp)
                                ) {
                                    Text(candidate.title, maxLines = 2)
                                }
                            }
                        }
                    }
                }
            }
            var isDismissFocused by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = onDismiss,
                scale = OutlinedButtonDefaults.scale(scale = 1f, focusedScale = 1f),
                shape = OutlinedButtonDefaults.shape(RoundedCornerShape(12.dp)),
                border = OutlinedButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                glow = OutlinedButtonDefaults.glow(
                    glow = Glow.None,
                    focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 10.dp)
                ),
                colors = OutlinedButtonDefaults.colors(
                    containerColor = AppColors.SurfaceCard,
                    contentColor = Color.White,
                    focusedContainerColor = AppColors.SurfaceCard,
                    focusedContentColor = AppColors.NeonCyan
                ),
                modifier = Modifier
                    .zIndex(if (isDismissFocused) 1f else 0f)
                    .onFocusChanged { isDismissFocused = it.isFocused }
                    .neonGlow(focused = isDismissFocused, borderRadius = 12.dp)
            ) { Text("Скасувати") }
        }
    }
}

@Composable
private fun VideoSourceNotice(
    modifier: Modifier = Modifier,
    message: String,
    action: String,
    onClick: () -> Unit,
    detail: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.SurfaceCard)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(message, color = Color.White, style = MaterialTheme.typography.titleSmall)
        if (!detail.isNullOrBlank()) Text(detail, color = AppColors.TextMuted, fontSize = 12.sp)
        var isNoticeFocused by remember { mutableStateOf(false) }
        Button(
            onClick = onClick,
            scale = ButtonDefaults.scale(scale = 1f, focusedScale = 1f),
            shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
            glow = ButtonDefaults.glow(glow = Glow.None, focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 10.dp)),
            modifier = Modifier
                .zIndex(if (isNoticeFocused) 1f else 0f)
                .onFocusChanged { isNoticeFocused = it.isFocused }
                .neonGlow(focused = isNoticeFocused, borderRadius = 12.dp)
        ) { Text(action) }
    }
}

@Composable
private fun ReleaseSelectionDialog(
    candidates: List<AnitubeSearchResult>,
    onDismiss: () -> Unit,
    onSelect: (AnitubeSearchResult) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(620.dp)
                .heightIn(max = 600.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(AppColors.SurfaceDark)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Виберіть реліз AniTube", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                candidates.forEach { candidate ->
                    var isCandFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = { onSelect(candidate) },
                        scale = ButtonDefaults.scale(scale = 1f, focusedScale = 1f),
                        shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                        glow = ButtonDefaults.glow(glow = Glow.None, focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 10.dp)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isCandFocused) 1f else 0f)
                            .onFocusChanged { isCandFocused = it.isFocused }
                            .neonGlow(focused = isCandFocused, borderRadius = 12.dp)
                    ) {
                        Text(candidate.title, maxLines = 2)
                    }
                }
            }
            var isDismissFocused by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = onDismiss,
                scale = OutlinedButtonDefaults.scale(scale = 1f, focusedScale = 1f),
                shape = OutlinedButtonDefaults.shape(RoundedCornerShape(12.dp)),
                border = OutlinedButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                glow = OutlinedButtonDefaults.glow(
                    glow = Glow.None,
                    focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 10.dp)
                ),
                colors = OutlinedButtonDefaults.colors(
                    containerColor = AppColors.SurfaceCard,
                    contentColor = Color.White,
                    focusedContainerColor = AppColors.SurfaceCard,
                    focusedContentColor = AppColors.NeonCyan
                ),
                modifier = Modifier
                    .zIndex(if (isDismissFocused) 1f else 0f)
                    .onFocusChanged { isDismissFocused = it.isFocused }
                    .neonGlow(focused = isDismissFocused, borderRadius = 12.dp)
            ) { Text("Скасувати") }
        }
    }
}

@Composable
private fun StudioVoiceDialog(
    group: VoiceCategoryGroup,
    selectedVoiceIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .heightIn(max = 600.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(AppColors.SurfaceDark)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(group.name, color = Color.White, style = MaterialTheme.typography.titleLarge)
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                group.variants.forEachIndexed { position, (index, voice) ->
                    val player = voice.playerName.ifBlank { "Плеєр ${position + 1}" }
                    val studio = voice.name.ifBlank { "Невідома студія" }
                    val isSelected = index == selectedVoiceIndex
                    var isVoiceFocused by remember { mutableStateOf(false) }
                    val containerColor = when {
                        isVoiceFocused -> AppColors.NeonCyanLight
                        isSelected -> AppColors.NeonCyan
                        else -> AppColors.SurfaceCard
                    }
                    val contentColor = when {
                        isVoiceFocused -> AppColors.ButtonDarkText
                        isSelected -> AppColors.ButtonDarkText
                        else -> Color.White
                    }
                    Button(
                        onClick = { onSelect(index) },
                        scale = ButtonDefaults.scale(scale = 1f, focusedScale = 1f),
                        shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                        glow = ButtonDefaults.glow(glow = Glow.None, focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 10.dp)),
                        colors = ButtonDefaults.colors(
                            containerColor = containerColor,
                            contentColor = contentColor,
                            focusedContainerColor = containerColor,
                            focusedContentColor = contentColor
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isVoiceFocused) 1f else 0f)
                            .onFocusChanged { isVoiceFocused = it.isFocused }
                            .neonGlow(focused = isVoiceFocused, borderRadius = 12.dp)
                    ) {
                        Text(
                            text = "$studio · $player" + if (isSelected) " · обрано" else "",
                            maxLines = 1,
                            color = contentColor
                        )
                    }
                }
            }
            var isDismissFocused by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = onDismiss,
                scale = OutlinedButtonDefaults.scale(scale = 1f, focusedScale = 1f),
                shape = OutlinedButtonDefaults.shape(RoundedCornerShape(12.dp)),
                border = OutlinedButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                glow = OutlinedButtonDefaults.glow(
                    glow = Glow.None,
                    focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 10.dp)
                ),
                colors = OutlinedButtonDefaults.colors(
                    containerColor = AppColors.SurfaceCard,
                    contentColor = Color.White,
                    focusedContainerColor = AppColors.SurfaceCard,
                    focusedContentColor = AppColors.NeonCyan
                ),
                modifier = Modifier
                    .zIndex(if (isDismissFocused) 1f else 0f)
                    .onFocusChanged { isDismissFocused = it.isFocused }
                    .neonGlow(focused = isDismissFocused, borderRadius = 12.dp)
            ) { Text("Скасувати") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalTvMaterial3Api::class)
@Composable
private fun DetailsGenreTags(genres: List<String>, onOpenGenre: (String) -> Unit,
    light: Boolean) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
        reverseLayout = true,
    ) {
        itemsIndexed(genres.reversed()) { _, genre ->
            var isFocused by remember(genre) { mutableStateOf(false) }
            val chipShape = RoundedCornerShape(percent = 50)
            Surface(
                onClick = { onOpenGenre(genre) },
                scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f),
                shape = ClickableSurfaceDefaults.shape(chipShape),
                border = ClickableSurfaceDefaults.border(
                    border = Border(
                        border = BorderStroke(1.5.dp, if (light) Color(0xFF596575)
                            else AppColors.TextMuted.copy(alpha = 0.65f)),
                        shape = chipShape,
                    ),
                    focusedBorder = Border.None,
                ),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = AppColors.NeonCyanLight,
                ),
                modifier = Modifier
                    .zIndex(if (isFocused) 1f else 0f)
                    .onFocusChanged { isFocused = it.isFocused },
            ) {
                Text(
                    genreLabel(genre),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = if (isFocused) AppColors.ButtonDarkText else if (light)
                        Color(0xFF263241) else AppColors.TextLight,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeasonEpisodeDialog(
    seasons: List<NumberedSeason>,
    currentAnimeId: Int,
    episodes: List<AnitubeEpisode>,
    selectedEpisodeIndex: Int,
    onDismiss: () -> Unit,
    onEpisode: (Int) -> Unit,
    onSeason: (Int) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(640.dp).heightIn(max = 600.dp)
                .background(AppColors.SurfaceDark, RoundedCornerShape(12.dp))
                .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Сезони та серії", color = Color.White,
                style = MaterialTheme.typography.titleLarge)
            if (seasons.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    seasons.forEach { season ->
                        val current = season.animeId == currentAnimeId
                        OutlinedButton(
                            onClick = { season.animeId?.let { if (!current) onSeason(it) } },
                            enabled = season.animeId != null,
                        ) {
                            Text("Сезон ${season.number}" +
                                if (season.animeId == null) " · немає релізу" else if (current) " ✓" else "")
                        }
                    }
                }
            }
            if (episodes.isEmpty()) {
                Text("Для цього сезону серії ще не завантажені. Оберіть реліз AniTube.",
                    color = AppColors.TextSecondary)
            } else {
                Text("Серії поточного сезону", color = AppColors.TextLight)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    episodes.forEachIndexed { index, episode ->
                        Button(onClick = { onEpisode(index) },
                            colors = ButtonDefaults.colors(
                                containerColor = if (index == selectedEpisodeIndex)
                                    AppColors.NeonCyan else AppColors.SurfaceCard,
                                contentColor = if (index == selectedEpisodeIndex)
                                    AppColors.ButtonDarkText else Color.White,
                            )) { Text("${episode.episodeNumber}") }
                    }
                }
            }
            OutlinedButton(onClick = onDismiss) { Text("Назад") }
        }
    }
}

@Composable
private fun MetadataBadge(text: String, bgColor: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private val sampleAnimeDetails = AnimeDetails(
    id = 1,
    title = Title(
        romaji = "Sousou no Frieren",
        english = "Frieren: Beyond Journey's End",
        native = "葬送のフリーレン"
    ),
    cover = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx154587-1080p.jpg",
    banner = null,
    description = "Маг Фрірен — член групи героїв, яка перемогла Короля Демонів. Після 10 років подорожі та перемоги над злом вони повертаються до столиці, щоб попрощатися...",
    score = 91,
    episodes = 28,
    durationMinutes = 24,
    format = "TV",
    status = "FINISHED",
    seasonYear = 2023,
    genres = listOf("Пригоди", "Драма", "Фентезі"),
    studios = listOf("Madhouse")
)

private val sampleVoices = listOf(
    AnitubeVoice(
        category = "ОЗВУЧЕННЯ",
        name = "FanVoxUA",
        episodes = listOf(
            AnitubeEpisode(id = "1", episodeNumber = 1, title = "Серія 1", fileUrl = "", playerName = "ASHDI"),
            AnitubeEpisode(id = "2", episodeNumber = 2, title = "Серія 2", fileUrl = "", playerName = "ASHDI"),
            AnitubeEpisode(id = "3", episodeNumber = 3, title = "Серія 3", fileUrl = "", playerName = "ASHDI")
        )
    ),
    AnitubeVoice(
        category = "ОЗВУЧЕННЯ",
        name = "Amanogawa",
        episodes = listOf(
            AnitubeEpisode(id = "1", episodeNumber = 1, title = "Серія 1", fileUrl = "", playerName = "FENIX")
        )
    )
)

@Preview(device = "id:tv_1080p")
@Composable
private fun DetailsScreenPreview() {
    AppTheme {
        DetailsScreen(
            state = DetailsState.Ready(
                details = sampleAnimeDetails,
                videoSource = VideoSourceState.Success(
                    AnitubeSearchResult(3106, "Ковбой Бібоп", "https://anitube.in.ua/3106-kovboy-bbop.html"),
                    sampleVoices
                ),
                selectedVoiceIndex = 0,
                selectedEpisodeIndex = 0
            ),
            onRetry = {},
            onRetryVideoSource = {},
            onSelectRelease = {},
            onManualSearch = {},
            onResetManualSearch = {},
            onSelectTmdb = {},
            onFindTmdbCandidates = {},
            onTolokaSearch = {},
            onAutoTolokaSearch = { _, _ -> },
            onSelectTolokaHit = {},
            onRetryTolokaMetadata = {},
            onPlayTolokaFile = {},
            onToggleFavorite = {},
            onSelectVoice = {},
            onSelectEpisode = {},
            onOpenSeason = {},
            onWatchClick = {},
            onTrailerClick = {},
            onBackClick = {},
            onOpenRelated = {},
            onOpenGenre = {},
            onMoreRelated = {},
            onMoreRecommendations = {},
        )
    }
}
