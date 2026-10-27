package ua.any2b.tvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import ua.any2b.core.anitube.AnitubeEpisode
import ua.any2b.core.anitube.AnitubeSearchResult
import ua.any2b.core.anitube.AnitubeVoice
import ua.any2b.core.toloka.TolokaHit

@OptIn(ExperimentalLayoutApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun DetailsScreen(
    state: DetailsState,
    onRetry: () -> Unit,
    onRetryVideoSource: () -> Unit,
    onSelectRelease: (AnitubeSearchResult) -> Unit,
    onManualSearch: (String) -> Unit,
    onResetManualSearch: () -> Unit,
    onTolokaSearch: (String) -> Unit,
    onSelectTolokaHit: (String) -> Unit,
    onRetryTolokaMetadata: () -> Unit,
    onPlayTolokaFile: (Int) -> Unit,
    onToggleFavorite: () -> Unit,
    onSelectVoice: (Int) -> Unit,
    onSelectEpisode: (Int) -> Unit,
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
            .background(AppColors.BackgroundDark)
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
                val isMovie = state.details.format.equals("MOVIE", ignoreCase = true)
                var showReleaseDialog by remember(state.details.id) { mutableStateOf(false) }
                var showManualDialog by remember(state.details.id) { mutableStateOf(false) }
                var isDescriptionExpanded by rememberSaveable(state.details.id) { mutableStateOf(false) }
                var videoTab by rememberSaveable(state.details.id) { mutableStateOf("online") }
                val tolokaUkrainianTitle = (state.videoSource as? VideoSourceState.Success)?.release?.title
                val tolokaOptions = tolokaQueryOptions(state.details, tolokaUkrainianTitle)
                var tolokaQueryCustomized by rememberSaveable(state.details.id) { mutableStateOf(false) }
                var tolokaQuery by rememberSaveable(state.details.id) {
                    mutableStateOf(tolokaOptions.firstOrNull()?.second.orEmpty())
                }
                LaunchedEffect(tolokaUkrainianTitle) {
                    if (!tolokaQueryCustomized && tolokaOptions.isNotEmpty()) {
                        tolokaQuery = tolokaOptions.first().second
                    }
                }
                val releaseCandidates = when (val source = state.videoSource) {
                    is VideoSourceState.Ambiguous -> source.candidates
                    is VideoSourceState.Error -> source.candidates
                    else -> emptyList()
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Two-Column Content Grid (~25% left, ~75% right)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(36.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Left Column (~25%) - Poster & Action Buttons below
                        Column(
                            modifier = Modifier
                                .width(220.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(AppColors.SurfaceDark)
                            ) {
                                AsyncImage(
                                    model = state.details.cover,
                                    contentDescription = state.details.title.display,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                ReleaseBadge(
                                    score = state.details.score,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                                )
                            }

                            // Action Buttons under poster
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (videoTab == "online" && state.historyEntry != null) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
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

                                if (videoTab == "online" && state.videoSource is VideoSourceState.Success) {
                                    val selectedEpisode = state.voices.getOrNull(state.selectedVoiceIndex)
                                        ?.episodes?.getOrNull(state.selectedEpisodeIndex)
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
                                    var isWatchFocused by remember { mutableStateOf(false) }
                                    val watchBg = if (isWatchFocused) AppColors.NeonCyanLight else AppColors.SurfaceCard
                                    val watchText = if (isWatchFocused) AppColors.ButtonDarkText else Color.White
                                    Button(
                                        onClick = onWatchClick,
                                        enabled = !state.resolvingStream && selectedEpisode != null,
                                        scale = ButtonDefaults.scale(scale = 1f, focusedScale = 1f),
                                        shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                                        glow = ButtonDefaults.glow(
                                            glow = Glow.None,
                                            focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 12.dp)
                                        ),
                                        colors = ButtonDefaults.colors(
                                            containerColor = watchBg,
                                            contentColor = watchText,
                                            focusedContainerColor = watchBg,
                                            focusedContentColor = watchText
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .zIndex(if (isWatchFocused) 1f else 0f)
                                            .onFocusChanged { isWatchFocused = it.isFocused }
                                            .neonGlow(focused = isWatchFocused, borderRadius = 12.dp)
                                    ) {
                                        Text(buttonText, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    var isFavFocused by remember { mutableStateOf(false) }
                                    val favBg = when {
                                        isFavFocused -> AppColors.NeonCyanLight
                                        state.isFavorite -> AppColors.NeonCyan
                                        else -> AppColors.SurfaceCard
                                    }
                                    val favText = when {
                                        isFavFocused -> AppColors.ButtonDarkText
                                        state.isFavorite -> AppColors.ButtonDarkText
                                        else -> Color.White
                                    }
                                    OutlinedButton(
                                        onClick = onToggleFavorite,
                                        scale = OutlinedButtonDefaults.scale(scale = 1f, focusedScale = 1f),
                                        shape = OutlinedButtonDefaults.shape(RoundedCornerShape(12.dp)),
                                        border = OutlinedButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                                        glow = OutlinedButtonDefaults.glow(
                                            glow = Glow.None,
                                            focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 10.dp)
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
                                            .neonGlow(focused = isFavFocused, borderRadius = 12.dp)
                                    ) {
                                        Text(if (state.isFavorite) "В обраному" else "В обране", fontSize = 11.sp)
                                    }

                                    var isTrailerFocused by remember { mutableStateOf(false) }
                                    val trailerBg = if (isTrailerFocused) AppColors.NeonCyanLight else AppColors.SurfaceCard
                                    val trailerText = if (isTrailerFocused) AppColors.ButtonDarkText else Color.White
                                    OutlinedButton(
                                        onClick = onTrailerClick,
                                        scale = OutlinedButtonDefaults.scale(scale = 1f, focusedScale = 1f),
                                        shape = OutlinedButtonDefaults.shape(RoundedCornerShape(12.dp)),
                                        border = OutlinedButtonDefaults.border(border = Border.None, focusedBorder = Border.None),
                                        glow = OutlinedButtonDefaults.glow(
                                            glow = Glow.None,
                                            focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 10.dp)
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
                                            .neonGlow(focused = isTrailerFocused, borderRadius = 12.dp)
                                    ) {
                                        Text(
                                            if (state.details.trailer?.videoUrl() != null) "Трейлер" else "Знайти трейлер",
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Right Column (~75%) - Details, Metadata, Synopsis
                        Column(
                            modifier = Modifier
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = state.details.title.display,
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                val ukrainianTitle = (state.videoSource as? VideoSourceState.Success)
                                    ?.release?.title ?: when (state.videoSource) {
                                        VideoSourceState.Loading -> "Шукаємо українську назву..."
                                        else -> "Українську назву не вибрано"
                                    }
                                var isUkrainianTitleFocused by remember { mutableStateOf(false) }
                                Text(
                                    text = ukrainianTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isUkrainianTitleFocused) AppColors.NeonCyan else AppColors.TextLight,
                                    modifier = Modifier
                                        .onFocusChanged { isUkrainianTitleFocused = it.isFocused }
                                        .clickable {
                                            onResetManualSearch()
                                            showManualDialog = true
                                        }
                                        .semantics { contentDescription = "Уточнити реліз AniTube: $ukrainianTitle" },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            // Metadata Pills
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                state.details.format?.let { format ->
                                    MetadataBadge(format, AppColors.SurfaceCard, AppColors.AccentLight)
                                }
                                state.details.status?.let { status ->
                                    MetadataBadge(status, AppColors.SurfaceCard, AppColors.PrimaryPurple)
                                }
                                state.details.seasonYear?.let { year ->
                                    MetadataBadge("$year", AppColors.SurfaceCard, Color.LightGray)
                                }
                            }

                            // Genres
                            if (state.details.genres.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    state.details.genres.forEach { genre ->
                                        var isGenreFocused by remember(genre) { mutableStateOf(false) }
                                        Card(
                                            onClick = { onOpenGenre(genre) },
                                            scale = CardDefaults.scale(scale = 1f, focusedScale = 1f),
                                            shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
                                            glow = CardDefaults.glow(
                                                glow = Glow.None,
                                                focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 8.dp),
                                            ),
                                            colors = CardDefaults.colors(
                                                containerColor = AppColors.SurfaceDark,
                                                focusedContainerColor = AppColors.SurfaceDark,
                                            ),
                                            modifier = Modifier
                                                .zIndex(if (isGenreFocused) 1f else 0f)
                                                .onFocusChanged { isGenreFocused = it.isFocused },
                                        ) {
                                            Text(
                                                genreLabel(genre),
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                color = if (isGenreFocused) AppColors.NeonCyan else AppColors.TextLight,
                                                fontSize = 12.sp,
                                            )
                                        }
                                    }
                                }
                            }

                            // Synopsis
                            val cleanDesc = state.details.description
                                ?.replace(Regex("<[^>]+>"), "")
                                ?.takeIf { it.isNotBlank() } ?: "Опис відсутній."
                            val hasDescription = cleanDesc != "Опис відсутній."
                            Column(
                                modifier = Modifier.fillMaxWidth().then(
                                    if (hasDescription) Modifier.clickable { isDescriptionExpanded = !isDescriptionExpanded }
                                    else Modifier
                                ),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = cleanDesc,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AppColors.TextMuted,
                                    maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (hasDescription) {
                                    Text(
                                        text = if (isDescriptionExpanded) "Згорнути опис" else "Читати повністю",
                                        color = AppColors.AccentLight,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            // Footer Panel: Audio Studios & Episodes
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AppColors.SurfaceDark, RoundedCornerShape(14.dp))
                                    .padding(vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    SourceTabButton("Онлайн", selected = videoTab == "online") {
                                        videoTab = "online"
                                    }
                                    SourceTabButton("Торент", selected = videoTab == "torrent") {
                                        videoTab = "torrent"
                                    }
                                }
                                if (videoTab == "online") {
                                when (val source = state.videoSource) {
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
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            LazyRow(
                                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.fillMaxWidth()
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
                                                        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
                                                        border = CardDefaults.border(border = Border.None, focusedBorder = Border.None),
                                                        glow = CardDefaults.glow(
                                                            glow = Glow.None,
                                                            focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 10.dp)
                                                        ),
                                                        colors = CardDefaults.colors(
                                                            containerColor = bg,
                                                            focusedContainerColor = bg
                                                        ),
                                                        modifier = Modifier
                                                            .zIndex(if (isStudioFocused) 1f else 0f)
                                                            .onFocusChanged { isStudioFocused = it.isFocused }
                                                            .neonGlow(focused = isStudioFocused, borderRadius = 12.dp)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier
                                                                .heightIn(min = 36.dp)
                                                                .padding(horizontal = 12.dp),
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            Text(
                                                                text = voiceButtonLabel(group, displayedVoice),
                                                                color = textColor,
                                                                fontSize = 12.sp,
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                            )
                                                            if (group.variants.size > 1) {
                                                                FontAwesomeIcon(FaIcons.ANGLE_DOWN, color = textColor, size = 12.sp)
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

                                // Episodes for selected voice (~25% smaller chips)
                                val currentVoice = if (state.videoSource is VideoSourceState.Success) {
                                    state.voices.getOrNull(state.selectedVoiceIndex)
                                } else null
                                val watchedEpNumber = state.historyEntry?.episodeNumber ?: -1
                                if (!isMovie && currentVoice != null && currentVoice.episodes.isNotEmpty()) {
                                    Text(
                                        "Серії:",
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = AppColors.TextLight,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        itemsIndexed(currentVoice.episodes) { epIndex, episode ->
                                            var isEpFocused by remember { mutableStateOf(false) }
                                            val isEpSelected = epIndex == state.selectedEpisodeIndex
                                            val isWatched = episode.episodeNumber < watchedEpNumber ||
                                                    (state.historyEntry != null && episode.episodeNumber == watchedEpNumber && state.historyEntry.progress >= 0.99f)
                                            val bg = when {
                                                isEpFocused -> AppColors.NeonCyanLight
                                                isEpSelected -> AppColors.NeonCyan
                                                else -> AppColors.SurfaceCardHover
                                            }
                                            val textColor = when {
                                                isEpFocused -> AppColors.ButtonDarkText
                                                isEpSelected -> AppColors.ButtonDarkText
                                                isWatched -> AppColors.TextWatched
                                                else -> AppColors.TextSecondary
                                            }
                                            val epText = episode.title.takeIf { it.isNotBlank() && !it.contains("серія", true) }
                                                ?: "Серія ${episode.episodeNumber}"

                                            Surface(
                                                onClick = { onSelectEpisode(epIndex) },
                                                scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f),
                                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                                                border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
                                                glow = ClickableSurfaceDefaults.glow(
                                                    glow = Glow.None,
                                                    focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 8.dp)
                                                ),
                                                colors = ClickableSurfaceDefaults.colors(
                                                    containerColor = bg,
                                                    focusedContainerColor = bg
                                                ),
                                                modifier = Modifier
                                                    .height(36.dp)
                                                    .zIndex(if (isEpFocused) 1f else 0f)
                                                    .onFocusChanged { isEpFocused = it.isFocused }
                                                    .neonGlow(focused = isEpFocused, borderRadius = 10.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .padding(horizontal = 10.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = epText,
                                                        color = textColor,
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isEpSelected) FontWeight.Bold else FontWeight.Normal,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                } else {
                                    TolokaSearchPanel(
                                        query = tolokaQuery,
                                        options = tolokaOptions,
                                        onChooseQuery = { tolokaQuery = it; tolokaQueryCustomized = true },
                                        searchState = state.tolokaSearch,
                                        transferState = state.tolokaTransfer,
                                        playbackStatus = state.tolokaPlaybackStatus,
                                        onSearch = { tolokaQueryCustomized = true; onTolokaSearch(tolokaQuery) },
                                        onSelect = onSelectTolokaHit,
                                        onRetryMetadata = onRetryTolokaMetadata,
                                        onPlayFile = onPlayTolokaFile,
                                    )
                                }
                            }
                        }
                    }
                    if (state.details.related.isNotEmpty()) {
                        DetailsTitleRow(
                            title = "Пов’язані релізи",
                            items = state.details.related,
                            showRelatedBadge = true,
                            showMore = state.details.related.size > 13,
                            onOpen = onOpenRelated,
                            onMore = onMoreRelated,
                        )
                    }
                    if (state.details.recommendations.isNotEmpty()) {
                        DetailsTitleRow(
                            title = "Рекомендації",
                            items = state.details.recommendations,
                            showRelatedBadge = false,
                            showMore = state.details.recommendations.size > 13 || state.details.recommendationsHasMore,
                            onOpen = onOpenRelated,
                            onMore = onMoreRecommendations,
                        )
                    }
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
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SourceTabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        scale = ButtonDefaults.scale(scale = 1f, focusedScale = 1f),
        shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ButtonDefaults.colors(
            containerColor = if (selected) AppColors.NeonCyan else AppColors.SurfaceCard,
            contentColor = if (selected) AppColors.ButtonDarkText else Color.White,
            focusedContainerColor = AppColors.NeonCyanLight,
            focusedContentColor = AppColors.ButtonDarkText,
        ),
        modifier = Modifier.onFocusChanged { focused = it.isFocused }
            .neonGlow(focused = focused, borderRadius = 10.dp),
    ) {
        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TolokaSearchPanel(
    query: String,
    options: List<Pair<String, String>>,
    onChooseQuery: (String) -> Unit,
    searchState: TolokaSearchState,
    transferState: TolokaTransferState,
    playbackStatus: String?,
    onSearch: () -> Unit,
    onSelect: (String) -> Unit,
    onRetryMetadata: () -> Unit,
    onPlayFile: (Int) -> Unit,
) {
    var showQueryDialog by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Пошук роздач Toloka", color = Color.White,
            style = MaterialTheme.typography.titleMedium)
        Text("OK — шукати · утримуйте OK — інші назви. Роздачу й файл вибираєте ви.",
            color = AppColors.TextSecondary, fontSize = 13.sp)
        var searchFocused by remember { mutableStateOf(false) }
        Card(
            onClick = { if (query.isNotBlank() && searchState != TolokaSearchState.Loading) onSearch() },
            onLongClick = { showQueryDialog = true },
            scale = CardDefaults.scale(scale = 1f, focusedScale = 1f),
            shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
            modifier = Modifier.onFocusChanged { searchFocused = it.isFocused }
                .neonGlow(focused = searchFocused, borderRadius = 10.dp),
        ) { Text(query, modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) }

        when (searchState) {
            TolokaSearchState.Idle -> Text("Натисніть «Шукати», щоб отримати роздачі.",
                color = AppColors.TextSecondary)
            TolokaSearchState.Loading -> Text("Шукаємо роздачі…", color = AppColors.TextLight)
            is TolokaSearchState.Error -> Text(searchState.message, color = AppColors.ErrorRed)
            is TolokaSearchState.Results -> {
                if (searchState.query != query.trim()) {
                    Text("Запит змінено. Натисніть кнопку з назвою, щоб оновити результати.",
                        color = AppColors.TextSecondary)
                } else if (searchState.hits.isEmpty()) {
                    Text("Підхожих роздач немає. Знайдено: ${searchState.totalFound}; відсіяно за назвою, роком або сезоном.",
                        color = AppColors.TextLight)
                } else {
                    Text("Підхожих роздач: ${searchState.hits.size} із ${searchState.totalFound}",
                        color = AppColors.TextSecondary, fontSize = 12.sp)
                    searchState.hits.forEach { hit ->
                        TolokaResultRow(
                            hit = hit,
                            selected = hit.id == searchState.selectedId,
                            enabled = transferState !is TolokaTransferState.Loading,
                            onClick = { onSelect(hit.id) },
                        )
                        if (hit.id == searchState.selectedId) {
                            TolokaTransferContent(transferState, playbackStatus, onSearch, onRetryMetadata, onPlayFile)
                        }
                    }
                }
            }
        }
    }
    if (showQueryDialog) {
        TolokaQueryDialog(options, query, onDismiss = { showQueryDialog = false }) { selected ->
            onChooseQuery(selected)
            showQueryDialog = false
        }
    }
}

private fun tolokaQueryOptions(details: AnimeDetails, releaseTitle: String?): List<Pair<String, String>> {
    val ukrainian = releaseTitle?.takeIf(String::isNotBlank)
        ?: details.synonyms.firstOrNull { it.contains(Regex("[іїєґІЇЄҐ]")) }
    val year = details.seasonYear?.toString()
    return buildList {
        fun addName(label: String, name: String?) {
            val value = name?.trim()?.takeIf(String::isNotBlank) ?: return
            add(label to value)
            if (year != null) add("$label + рік" to "$value $year")
        }
        addName("Українська", ukrainian)
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
private fun TolokaQueryDialog(
    options: List<Pair<String, String>>,
    selectedQuery: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.width(560.dp).heightIn(max = 600.dp)
                .clip(RoundedCornerShape(18.dp)).background(AppColors.SurfaceDark).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Варіант пошукової назви", color = Color.White,
                style = MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (label, value) ->
                    Button(onClick = { onSelect(value) }, modifier = Modifier.fillMaxWidth(),
                        scale = ButtonDefaults.scale(scale = 1f, focusedScale = 1f)) {
                        Text("$label: $value" + if (value == selectedQuery) " ✓" else "",
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            OutlinedButton(onClick = onDismiss) { Text("Назад") }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TolokaTransferContent(
    transferState: TolokaTransferState,
    playbackStatus: String?,
    onSearch: () -> Unit,
    onRetryMetadata: () -> Unit,
    onPlayFile: (Int) -> Unit,
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
            Text("Торент додано. Виберіть відеофайл для відтворення:", color = Color.White,
                style = MaterialTheme.typography.titleMedium)
            Text("Метадані отримано: ${transferState.torrent.files.size} файлів, відео: ${files.size}",
                color = AppColors.TextSecondary, fontSize = 12.sp)
            if (playbackStatus != null) Text(playbackStatus, color = AppColors.TextLight,
                fontSize = 12.sp)
            if (files.isEmpty()) {
                Text("У роздачі немає доступних відеофайлів.", color = AppColors.TextSecondary)
            } else {
                files.forEach { file ->
                    var focused by remember(file.id) { mutableStateOf(false) }
                    Card(
                        onClick = { onPlayFile(file.id) },
                        scale = CardDefaults.scale(scale = 1f, focusedScale = 1f),
                        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
                        colors = CardDefaults.colors(
                            containerColor = AppColors.SurfaceCard,
                            focusedContainerColor = AppColors.NeonCyanLight,
                        ),
                        modifier = Modifier.fillMaxWidth()
                            .onFocusChanged { focused = it.isFocused }
                            .neonGlow(focused = focused, borderRadius = 10.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(file.path, color = if (focused) AppColors.ButtonDarkText else Color.White)
                            Text(formatFileSize(file.length), fontSize = 12.sp,
                                color = if (focused) AppColors.ButtonDarkText else AppColors.TextSecondary)
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
private fun TolokaResultRow(hit: TolokaHit, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> AppColors.NeonCyanLight
        selected -> AppColors.NeonCyan
        else -> AppColors.SurfaceCard
    }
    val foreground = if (focused || selected) AppColors.ButtonDarkText else Color.White
    Card(
        onClick = { if (enabled) onClick() },
        scale = CardDefaults.scale(scale = 1f, focusedScale = 1f),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        colors = CardDefaults.colors(containerColor = background, focusedContainerColor = background),
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
            .neonGlow(focused = focused, borderRadius = 10.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(hit.title, color = foreground, fontWeight = FontWeight.Medium)
            val details = buildList {
                if (hit.sizeText.isNotBlank()) add(hit.sizeText)
                hit.seeders?.let { add("Сіди: $it") }
                hit.leechers?.let { add("Піри: $it") }
            }.joinToString(" · ")
            if (details.isNotBlank()) Text(details, color = foreground, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DetailsTitleRow(
    title: String,
    items: List<AnimeCard>,
    showRelatedBadge: Boolean,
    showMore: Boolean,
    onOpen: (AnimeCard) -> Unit,
    onMore: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        LazyRow(
            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 0.dp),
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
                glow = OutlinedButtonDefaults.glow(glow = Glow.None, focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 10.dp)),
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
                glow = OutlinedButtonDefaults.glow(glow = Glow.None, focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 10.dp)),
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
                    val containerColor = if (isSelected) Color(0xFFE2E8F0) else AppColors.SurfaceCard
                    val contentColor = if (isSelected) Color(0xFF0F172A) else Color.White
                    var isVoiceFocused by remember { mutableStateOf(false) }
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
                glow = OutlinedButtonDefaults.glow(glow = Glow.None, focusedGlow = Glow(elevationColor = AppColors.NeonCyan, elevation = 10.dp)),
                modifier = Modifier
                    .zIndex(if (isDismissFocused) 1f else 0f)
                    .onFocusChanged { isDismissFocused = it.isFocused }
                    .neonGlow(focused = isDismissFocused, borderRadius = 12.dp)
            ) { Text("Скасувати") }
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
            onTolokaSearch = {},
            onSelectTolokaHit = {},
            onRetryTolokaMetadata = {},
            onPlayTolokaFile = {},
            onToggleFavorite = {},
            onSelectVoice = {},
            onSelectEpisode = {},
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
