package ua.any2b.tvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import ua.any2b.core.anilist.groupNumberedTvSeasons
import ua.any2b.core.anitube.AnitubeSearchResult
import ua.any2b.core.anitube.AnitubeVoice
import ua.any2b.core.anitube.AnitubeEpisode
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
    onPanelSelected: (Panel) -> Unit,
    onSelectTolokaHit: (String) -> Unit,
    onPromoteTolokaHit: (String) -> Unit = {},
    onDiscardTolokaHit: (String) -> Unit = {},
    onRejectTolokaHit: (String) -> Unit = {},
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
                var showSynopsisDialog by remember(state.details.id) { mutableStateOf(false) }
                val displayVideoTab = if (state.activePanel == Panel.TORRENT) "torrent" else "online"
                val anitubeUkrainianTitle = (state.videoSource as? VideoSourceState.Success)?.release?.title
                val tmdbUkrainianTitle = state.tmdbLink.ukrainianTitle
                val seriesTitle = (state.tmdbLink as? TmdbLinkState.Confirmed)?.title
                    ?.takeIf { it.type == ua.any2b.core.tmdb.TmdbMediaType.TV }
                    ?.let { it.ukrainianTitle ?: it.title }
                val torrentTmdbTitle = (state.tmdbLink as? TmdbLinkState.Confirmed)?.title
                val torrentHeadingUkrainian = torrentTmdbTitle?.matchedSeasonTitle
                    ?: torrentTmdbTitle?.ukrainianTitle ?: torrentTmdbTitle?.title
                    ?: state.details.title.native ?: state.details.title.romaji
                    ?: state.details.title.english
                val torrentHeadingEnglish = torrentTmdbTitle?.englishTitle
                    ?: torrentTmdbTitle?.originalTitle?.takeIf { torrentTmdbTitle.originalLanguage == "en" }
                    ?: state.details.title.english ?: torrentTmdbTitle?.originalTitle
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
                LaunchedEffect(state.tolokaSearch) {
                    val result = state.tolokaSearch as? TolokaSearchState.Results
                    if (!tolokaQueryCustomized && result != null) {
                        tolokaQuery = result.query
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
                                    SegmentButton("Перемкнути пошукову панель",
                                        selected = displayVideoTab == "torrent",
                                        surfaceColors = surfaceColors,
                                        icon = FaIcons.ASTERISK) {
                                        onPanelSelected(if (state.activePanel == Panel.TORRENT)
                                            Panel.ONLINE else Panel.TORRENT)
                                    }
                                    if (displayVideoTab == "torrent") {
                                        TorrentTitleHeading(
                                            ukrainianTitle = torrentHeadingUkrainian,
                                            englishTitle = torrentHeadingEnglish,
                                            modifier = Modifier.weight(1f).padding(start = 16.dp),
                                            surfaceColors = surfaceColors,
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
                                            val currentVoice = source.voices.getOrNull(state.selectedVoiceIndex)
                                            val selectedEpisode = currentVoice?.episodes?.getOrNull(state.selectedEpisodeIndex)
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
                                            val numbered = state.seriesSeasons.filter { it.animeId != null }
                                            AnitubePlaybackControls(
                                                voices = source.voices,
                                                selectedVoiceIndex = state.selectedVoiceIndex,
                                                onSelectVoice = onSelectVoice,
                                                episodes = if (isMovie) emptyList() else currentVoice?.episodes.orEmpty(),
                                                selectedEpisodeIndex = state.selectedEpisodeIndex,
                                                onSelectEpisode = onSelectEpisode,
                                                seasons = numbered.mapNotNull { season -> season.animeId?.let { id ->
                                                    AnitubeSeasonOption(id, "S${season.number}")
                                                } },
                                                selectedSeasonId = state.details.id,
                                                onSelectSeason = onOpenSeason,
                                                isMovie = isMovie,
                                                resolving = state.resolvingStream,
                                                watchLabel = buttonText,
                                                canWatch = canWatch,
                                                watchFocusRequester = watchFocusRequester,
                                                onWatch = onWatchClick,
                                                surfaceColors = surfaceColors,
                                            )
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
                                        onPromote = onPromoteTolokaHit,
                                        onDiscard = onDiscardTolokaHit,
                                        onRejectForever = onRejectTolokaHit,
                                        onRetryMetadata = onRetryTolokaMetadata,
                                        onPlayFile = onPlayTolokaFile,
                                        onEditQuery = { showTolokaQueryDialog = true },
                                        seasonEntries = state.tmdbSeasonEntries,
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
                    DetailsSynopsisDialog(state.details.description.orEmpty(), surfaceColors,
                        onDismiss = { showSynopsisDialog = false })
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
            itemsIndexed(items.take(13), key = { index, card -> "title:${card.id}:$index" }) { _, card ->
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
            onPanelSelected = {},
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
