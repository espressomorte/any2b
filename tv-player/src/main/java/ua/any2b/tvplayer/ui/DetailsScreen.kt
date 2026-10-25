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
import androidx.compose.ui.graphics.Color
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
import androidx.tv.material3.*
import androidx.compose.ui.tooling.preview.Preview
import coil.compose.AsyncImage
import ua.any2b.core.anilist.AnimeDetails
import ua.any2b.core.anilist.AnimeCard
import ua.any2b.core.anilist.Title
import ua.any2b.core.anitube.AnitubeEpisode
import ua.any2b.core.anitube.AnitubeSearchResult
import ua.any2b.core.anitube.AnitubeVoice

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsScreen(
    state: DetailsState,
    onRetry: () -> Unit,
    onRetryVideoSource: () -> Unit,
    onSelectRelease: (AnitubeSearchResult) -> Unit,
    onManualSearch: (String) -> Unit,
    onResetManualSearch: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSelectVoice: (Int) -> Unit,
    onSelectEpisode: (Int) -> Unit,
    onWatchClick: () -> Unit,
    onBackClick: () -> Unit,
    onOpenRelated: (AnimeCard) -> Unit,
    onMoreRelated: () -> Unit,
    onMoreRecommendations: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0B0E))
            .padding(32.dp)
    ) {
        when (state) {
            DetailsState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Завантаження деталей тайтла...", style = MaterialTheme.typography.titleMedium, color = Color(0xFFD0BCFF))
                }
            }
            is DetailsState.Error -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(state.message, color = Color.Red, style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onRetry) { Text("Повторити") }
                }
            }
            is DetailsState.Ready -> {
                var showReleaseDialog by remember(state.details.id) { mutableStateOf(false) }
                var showManualDialog by remember(state.details.id) { mutableStateOf(false) }
                var isDescriptionExpanded by rememberSaveable(state.details.id) { mutableStateOf(false) }
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
                                    .background(Color(0xFF15171E))
                            ) {
                                AsyncImage(
                                    model = state.details.cover,
                                    contentDescription = state.details.title.display,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Action Buttons under poster
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (state.historyEntry != null) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "Переглянуто: серія ${state.historyEntry.episodeNumber}",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp,
                                            fontFamily = IbmPlexMono
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(Color(0xFF20232D))
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(state.historyEntry.progress.coerceIn(0f, 1f))
                                                    .fillMaxHeight()
                                                    .background(Color(0xFFD0BCFF))
                                            )
                                        }
                                    }
                                }

                                if (state.videoSource is VideoSourceState.Success) {
                                    val buttonText = when {
                                        state.resolvingStream -> "Підготовка..."
                                        state.historyEntry != null -> "Продовжити (Серія ${state.selectedEpisodeIndex + 1})"
                                        else -> "Дивитися"
                                    }
                                    Button(
                                        onClick = onWatchClick,
                                        enabled = !state.resolvingStream && state.voices.getOrNull(state.selectedVoiceIndex)
                                            ?.episodes?.getOrNull(state.selectedEpisodeIndex) != null,
                                        colors = ButtonDefaults.colors(
                                            containerColor = Color(0xFFD0BCFF),
                                            contentColor = Color(0xFF21005D)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(buttonText, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onToggleFavorite,
                                        colors = OutlinedButtonDefaults.colors(
                                            containerColor = Color(0xFF15171E),
                                            contentColor = Color(0xFFD0BCFF)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(if (state.isFavorite) "В обраному" else "В обране", fontSize = 11.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { /* TODO: Trailer */ },
                                        colors = OutlinedButtonDefaults.colors(
                                            containerColor = Color(0xFF15171E),
                                            contentColor = Color(0xFFD0BCFF)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Трейлер", fontSize = 11.sp)
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
                                state.details.title.romaji?.let {
                                    if (it != state.details.title.display) {
                                        Text(it, style = MaterialTheme.typography.titleMedium, color = Color(0xFFCCC2DC))
                                    }
                                }
                            }

                            // Metadata Pills (Rating colored by wavelength spectrum 740nm..380nm)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                state.details.score?.let { score ->
                                    val ratingColor = scoreToSpectrumColor(score)
                                    MetadataBadge("Рейтинг: $score%", ratingColor.copy(alpha = 0.25f), ratingColor)
                                }
                                state.details.format?.let { format ->
                                    MetadataBadge(format, Color(0xFF20232D), Color(0xFFD0BCFF))
                                }
                                state.details.status?.let { status ->
                                    MetadataBadge(status, Color(0xFF20232D), Color(0xFFA855F7))
                                }
                                state.details.seasonYear?.let { year ->
                                    MetadataBadge("$year", Color(0xFF20232D), Color.LightGray)
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
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF15171E))
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(genre, color = Color(0xFFCCC2DC), fontSize = 12.sp)
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
                                    color = Color(0xFFB0B3C1),
                                    maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (hasDescription) {
                                    Text(
                                        text = if (isDescriptionExpanded) "Згорнути опис" else "Читати повністю",
                                        color = Color(0xFFD0BCFF),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            // Footer Panel: Audio Studios & Episodes
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF15171E))
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val releaseTitle = (state.videoSource as? VideoSourceState.Success)
                                    ?.release?.title ?: "Реліз ще не вибрано"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("AniTube · українська назва", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                        Text(releaseTitle, color = Color.White, style = MaterialTheme.typography.titleMedium)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            onResetManualSearch()
                                            showManualDialog = true
                                        },
                                        modifier = Modifier.size(48.dp)
                                            .semantics { contentDescription = "Змінити реліз" }
                                    ) {
                                        FontAwesomeIcon(FaIcons.PENCIL)
                                    }
                                }
                                when (val source = state.videoSource) {
                                    VideoSourceState.Loading -> Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                            listOf(92.dp, 66.dp).forEach { width ->
                                                Box(
                                                    modifier = Modifier.width(width).height(7.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0xFF45405A))
                                                )
                                            }
                                        }
                                        Text("Шукаємо озвучки на AniTube...", color = Color(0xFFCCC2DC), fontSize = 13.sp)
                                    }
                                    is VideoSourceState.Ambiguous -> VideoSourceNotice(
                                        message = "Знайдено кілька схожих релізів, виберіть потрібний",
                                        action = "Вибрати реліз",
                                        onClick = { showReleaseDialog = true }
                                    )
                                    is VideoSourceState.Error -> VideoSourceNotice(
                                        message = "Не вдалося завантажити відео з AniTube",
                                        detail = source.message,
                                        action = if (source.candidates.isNotEmpty()) "Вибрати вручну" else "Повторити спробу",
                                        onClick = if (source.candidates.isNotEmpty()) {
                                            { showReleaseDialog = true }
                                        } else onRetryVideoSource
                                    )
                                    is VideoSourceState.Success -> {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("Озвучення / Студії:", color = Color(0xFFCCC2DC), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                itemsIndexed(source.voices) { index, voice ->
                                                    val isSelected = index == state.selectedVoiceIndex
                                                    val bg = if (isSelected) Color(0xFFA855F7) else Color(0xFF20232D)
                                                    val textColor = if (isSelected) Color.White else Color(0xFF94A3B8)

                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(16.dp))
                                                            .background(bg)
                                                            .clickable { onSelectVoice(index) }
                                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                                    ) {
                                                        Text(
                                                            text = voice.name.ifBlank { voice.category },
                                                            color = textColor,
                                                            fontSize = 11.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                // Episodes for selected voice (~25% smaller chips)
                                val currentVoice = if (state.videoSource is VideoSourceState.Success) {
                                    state.voices.getOrNull(state.selectedVoiceIndex)
                                } else null
                                val watchedEpIndex = state.historyEntry?.episodeIndex ?: -1
                                if (currentVoice != null && currentVoice.episodes.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text("Серії:", color = Color(0xFFCCC2DC), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        itemsIndexed(currentVoice.episodes) { epIndex, episode ->
                                            val isEpSelected = epIndex == state.selectedEpisodeIndex
                                            val isWatched = epIndex < watchedEpIndex || (state.historyEntry != null && epIndex == watchedEpIndex && state.historyEntry.progress >= 0.99f)
                                            val bg = if (isEpSelected) Color(0xFFA855F7) else Color(0xFF1E222D)
                                            val textColor = when {
                                                isEpSelected -> Color.White
                                                isWatched -> Color(0xFF475569) // Dimmer color for older/watched episodes
                                                else -> Color(0xFF94A3B8)
                                            }
                                            val epText = episode.title.takeIf { it.isNotBlank() && !it.contains("серія", true) } 
                                                ?: "Серія ${epIndex + 1}"

                                            Box(
                                                modifier = Modifier
                                                    .height(34.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(bg)
                                                    .clickable { onSelectEpisode(epIndex) }
                                                    .padding(horizontal = 10.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = epText,
                                                    color = textColor,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isEpSelected) FontWeight.Bold else FontWeight.Normal,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
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
        Text(title, style = MaterialTheme.typography.titleLarge, fontFamily = IbmPlexMono)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
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
                .background(Color(0xFF15171E))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Змінити реліз AniTube", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text("Введіть українську назву. Пошук виконується на AniTube.", color = Color(0xFF94A3B8), fontSize = 13.sp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF20232D))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isBlank()) Text("Назва українською", color = Color(0xFF94A3B8))
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
            Button(onClick = { onSearch(query) }, enabled = query.isNotBlank()) { Text("Шукати на AniTube") }
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (searchState) {
                    ManualReleaseSearchState.Idle -> Unit
                    ManualReleaseSearchState.Loading -> Text("Шукаємо релізи...", color = Color(0xFFCCC2DC))
                    is ManualReleaseSearchState.Error -> Text(searchState.message, color = Color(0xFFFFB4AB))
                    is ManualReleaseSearchState.Results -> {
                        if (searchState.candidates.isEmpty()) {
                            Text("На AniTube нічого не знайдено", color = Color(0xFFCCC2DC))
                        } else {
                            searchState.candidates.forEach { candidate ->
                                Button(onClick = { onSelect(candidate) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(candidate.title, maxLines = 2)
                                }
                            }
                        }
                    }
                }
            }
            OutlinedButton(onClick = onDismiss) { Text("Скасувати") }
        }
    }
}

@Composable
private fun VideoSourceNotice(
    message: String,
    action: String,
    onClick: () -> Unit,
    detail: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF20232D))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(message, color = Color.White, style = MaterialTheme.typography.titleSmall)
        if (!detail.isNullOrBlank()) Text(detail, color = Color(0xFFB0B3C1), fontSize = 12.sp)
        Button(onClick = onClick) { Text(action) }
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
                .background(Color(0xFF15171E))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Виберіть реліз AniTube", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                candidates.forEach { candidate ->
                    Button(onClick = { onSelect(candidate) }, modifier = Modifier.fillMaxWidth()) {
                        Text(candidate.title, maxLines = 2)
                    }
                }
            }
            OutlinedButton(onClick = onDismiss) { Text("Скасувати") }
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

/**
 * Maps score (0..100) to the visible light spectrum wavelength (740nm Red -> 380nm Violet)
 * and converts to Jetpack Compose Color.
 */
private fun scoreToSpectrumColor(score: Int): Color {
    val clamped = score.coerceIn(0, 100)
    // 0% = 740nm (Red), 100% = 380nm (Violet)
    val wavelength = 740.0 - (clamped / 100.0) * (740.0 - 380.0)
    var r = 0.0
    var g = 0.0
    var b = 0.0
    var factor = 1.0

    when {
        wavelength in 380.0..440.0 -> {
            r = -(wavelength - 440.0) / (440.0 - 380.0)
            g = 0.0
            b = 1.0
        }
        wavelength in 440.0..490.0 -> {
            r = 0.0
            g = (wavelength - 440.0) / (490.0 - 440.0)
            b = 1.0
        }
        wavelength in 490.0..510.0 -> {
            r = 0.0
            g = 1.0
            b = -(wavelength - 510.0) / (510.0 - 490.0)
        }
        wavelength in 510.0..580.0 -> {
            r = (wavelength - 510.0) / (580.0 - 510.0)
            g = 1.0
            b = 0.0
        }
        wavelength in 580.0..645.0 -> {
            r = 1.0
            g = -(wavelength - 645.0) / (645.0 - 580.0)
            b = 0.0
        }
        wavelength in 645.0..740.0 -> {
            r = 1.0
            g = 0.0
            b = 0.0
        }
    }

    if (wavelength in 380.0..420.0) {
        factor = 0.3 + 0.7 * (wavelength - 380.0) / (420.0 - 380.0)
    } else if (wavelength in 700.0..740.0) {
        factor = 0.3 + 0.7 * (740.0 - wavelength) / (740.0 - 700.0)
    }

    return Color(
        red = (r * factor).toFloat().coerceIn(0f, 1f),
        green = (g * factor).toFloat().coerceIn(0f, 1f),
        blue = (b * factor).toFloat().coerceIn(0f, 1f)
    )
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
            onToggleFavorite = {},
            onSelectVoice = {},
            onSelectEpisode = {},
            onWatchClick = {},
            onBackClick = {},
            onOpenRelated = {},
            onMoreRelated = {},
            onMoreRecommendations = {},
        )
    }
}
