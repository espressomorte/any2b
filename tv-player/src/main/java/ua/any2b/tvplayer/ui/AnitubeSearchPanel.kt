package ua.any2b.tvplayer.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.*
import androidx.compose.foundation.lazy.LazyColumn
import ua.any2b.core.anitube.AnitubeEpisode
import ua.any2b.core.anitube.AnitubeSearchResult
import ua.any2b.tvplayer.data.AnitubeCandidate
import ua.any2b.tvplayer.data.AnitubeResolution
import ua.any2b.tvplayer.data.AnitubeState

@Composable
internal fun AnitubeControllerPanel(
    state: AnitubeState,
    isMovie: Boolean,
    query: String,
    onRetry: () -> Unit,
    onSelectRelease: (AnitubeSearchResult) -> Unit,
    onSelectSeason: (Int) -> Unit,
    onSelectVoice: (Int) -> Unit,
    onSelectEpisode: (Int) -> Unit,
    onPlay: () -> Unit,
    surfaceColors: DetailsSurfaceColors,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state !is AnitubeState.Found) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Запит: $query", modifier = Modifier.weight(1f),
                    color = surfaceColors.mutedText, fontSize = 12.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                val searching = state is AnitubeState.Searching
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(50))
                    .background(if (searching) AppColors.NeonCyan.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable(enabled = !searching, onClick = onRetry)
                    .semantics { contentDescription = "Повторити пошук AniTube: $query" },
                    contentAlignment = Alignment.Center) {
                    FontAwesomeIcon(FaIcons.SEARCH, size = 16.sp,
                        color = if (searching) AppColors.NeonCyan else surfaceColors.mutedText)
                }
            }
        }
        when (state) {
            AnitubeState.Idle -> Text("Перевірка AniTube ще не запускалася",
                color = surfaceColors.mutedText)
            AnitubeState.Searching -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val transition = rememberInfiniteTransition(label = "anitube-search-progress")
                val rotation by transition.animateFloat(
                    initialValue = 0f, targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(850), RepeatMode.Restart),
                    label = "anitube-search-rotation",
                )
                Canvas(Modifier.size(20.dp).graphicsLayer { rotationZ = rotation }) {
                    drawArc(AppColors.NeonCyan, 35f, 285f, false,
                        style = Stroke(width = 3.dp.toPx()))
                }
                Text("Шукаємо релізи та озвучення на AniTube…", color = surfaceColors.text)
            }
            is AnitubeState.NotFound -> {
                Text("Не знайшли однозначний реліз AniTube. Повторіть пошук або виберіть збіг вручну.",
                    color = surfaceColors.text)
                ReleaseCandidates(state.candidates, onSelectRelease, surfaceColors)
            }
            is AnitubeState.Error -> {
                Text("Не вдалося перевірити AniTube: ${state.message}", color = surfaceColors.text)
                ReleaseCandidates(state.candidates, onSelectRelease, surfaceColors)
            }
            is AnitubeState.Catalog -> {
                if (state.resolution.candidates.isNotEmpty()) {
                    Text("Знайдені релізи", color = surfaceColors.text,
                        style = MaterialTheme.typography.titleSmall)
                    AnitubeReleaseCatalog(state.resolution, null, onSelectRelease, surfaceColors)
                } else {
                    Text("Реліз AniTube для цього тайтлу не знайдено.", color = surfaceColors.text)
                }
            }
            is AnitubeState.Found -> {
                val candidate = state.catalog?.candidates?.firstOrNull {
                    it.release.newsId == state.release.newsId
                }
                val isFilm = isMovie || candidate?.kind == ua.any2b.tvplayer.data.AnitubeReleaseKind.FILM
                val seasons = state.catalog?.seasons.orEmpty().filter { it.releases.isNotEmpty() }
                val voice = state.voices.getOrNull(state.selectedVoiceIndex)
                val episode = voice?.episodes?.getOrNull(state.selectedEpisodeIndex)
                AnitubePlaybackControls(
                    voices = state.voices,
                    selectedVoiceIndex = state.selectedVoiceIndex,
                    onSelectVoice = onSelectVoice,
                    episodes = if (isFilm) emptyList() else voice?.episodes.orEmpty(),
                    selectedEpisodeIndex = state.selectedEpisodeIndex,
                    onSelectEpisode = onSelectEpisode,
                    seasons = seasons.map { AnitubeSeasonOption(it.number, "S${it.number}") },
                    selectedSeasonId = candidate?.seasonNumber ?: seasons.firstOrNull()?.number,
                    onSelectSeason = onSelectSeason,
                    isMovie = isFilm,
                    resolving = state.resolvingStream,
                    watchLabel = if (state.resumeProgress in 0.01f..0.98f)
                        "Продовжити перегляд" else "Дивитися ${if (isFilm) "фільм" else "серію"}",
                    canWatch = episode != null,
                    onWatch = onPlay,
                    surfaceColors = surfaceColors,
                    showSelectors = false,
                )
            }
        }
    }
}

@Composable
private fun AnitubeReleaseCatalog(
    resolution: AnitubeResolution,
    selectedNewsId: Int?,
    onSelect: (AnitubeSearchResult) -> Unit,
    surfaceColors: DetailsSurfaceColors,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        resolution.seasons.filter { it.releases.isNotEmpty() }.forEach { season ->
            Text("Сезон ${season.number} · ${season.title}", color = surfaceColors.text,
                style = MaterialTheme.typography.labelLarge)
            ReleaseCandidateButtons(season.releases, selectedNewsId, onSelect, surfaceColors)
        }
        if (resolution.films.isNotEmpty()) {
            Text("Фільми", color = surfaceColors.text, style = MaterialTheme.typography.labelLarge)
            ReleaseCandidateButtons(resolution.films, selectedNewsId, onSelect, surfaceColors)
        }
        if (resolution.other.isNotEmpty()) {
            var expanded by remember(resolution) { mutableStateOf(false) }
            Button(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Інше · згорнути" else "Інше · ${resolution.other.size}")
            }
            if (expanded) ReleaseCandidateButtons(resolution.other, selectedNewsId, onSelect, surfaceColors)
        }
    }
}

@Composable
private fun ReleaseCandidateButtons(
    candidates: List<AnitubeCandidate>,
    selectedNewsId: Int?,
    onSelect: (AnitubeSearchResult) -> Unit,
    surfaceColors: DetailsSurfaceColors,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(candidates, key = { _, candidate -> candidate.release.newsId }) { _, candidate ->
            SegmentButton(candidate.release.title,
                selected = candidate.release.newsId == selectedNewsId,
                surfaceColors = surfaceColors) { onSelect(candidate.release) }
        }
    }
}

@Composable
private fun ReleaseCandidates(
    candidates: List<AnitubeSearchResult>,
    onSelect: (AnitubeSearchResult) -> Unit,
    surfaceColors: DetailsSurfaceColors,
) {
    if (candidates.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(candidates) { _, candidate ->
            SegmentButton(candidate.title, selected = false, surfaceColors = surfaceColors) {
                onSelect(candidate)
            }
        }
    }
}

@Composable
internal fun SegmentButton(label: String, selected: Boolean,
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
internal fun VideoSourceNotice(
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalTvMaterial3Api::class)
@Composable
internal fun DetailsGenreTags(genres: List<String>, onOpenGenre: (String) -> Unit,
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

@Composable
internal fun EpisodeWindowPicker(
    episodes: List<AnitubeEpisode>,
    selectedEpisodeIndex: Int,
    onEpisode: (Int) -> Unit,
    surfaceColors: DetailsSurfaceColors,
    modifier: Modifier = Modifier,
) {
    if (episodes.isEmpty()) return
    val selected = selectedEpisodeIndex.coerceIn(0, episodes.lastIndex)
    val window = episodeWindowItems(episodes.size, selected)
    val listState = rememberLazyListState()
    val previousGapFocus = remember { FocusRequester() }
    val nextGapFocus = remember { FocusRequester() }
    val episodeFocus = remember { mutableMapOf<Int, FocusRequester>() }
    var pendingGap by remember { mutableStateOf<EpisodeWindowItem.Gap?>(null) }
    LaunchedEffect(selected, window, pendingGap) {
        val pending = pendingGap ?: return@LaunchedEffect
        if (selected != pending.jumpTo) return@LaunchedEffect
        val gapPosition = window.indexOfFirst {
            it is EpisodeWindowItem.Gap && it.side == pending.side
        }
        val targetPosition = if (gapPosition >= 0) gapPosition else window.indexOfFirst {
            it is EpisodeWindowItem.Episode && it.index == pending.jumpTo
        }
        if (targetPosition >= 0) {
            listState.scrollToItem(targetPosition)
            withFrameNanos { }
            val requester = if (gapPosition >= 0) {
                if (pending.side == EpisodeWindowItem.Side.PREVIOUS) previousGapFocus else nextGapFocus
            } else episodeFocus[pending.jumpTo]
            requester?.requestFocus()
        }
        pendingGap = null
    }
    LazyRow(
        modifier = modifier.fillMaxWidth().graphicsLayer { clip = false },
        state = listState,
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(window, key = { item -> when (item) {
            is EpisodeWindowItem.Episode -> "episode:${item.index}"
            is EpisodeWindowItem.Gap -> "gap:${item.side}"
        } }) { item ->
            when (item) {
                is EpisodeWindowItem.Episode -> EpisodeWindowText(
                    text = "${episodes[item.index].episodeNumber}",
                    fontSize = if (item.index == selected) 28 else 17,
                    selected = item.index == selected,
                    surfaceColors = surfaceColors,
                    onClick = { onEpisode(item.index) },
                    modifier = Modifier.focusRequester(
                        episodeFocus.getOrPut(item.index) { FocusRequester() }),
                )
                is EpisodeWindowItem.Gap -> EpisodeWindowText("…", 18,
                    surfaceColors = surfaceColors,
                    onClick = { pendingGap = item; onEpisode(item.jumpTo) },
                    repeatOnHold = true,
                    modifier = Modifier.focusRequester(
                        if (item.side == EpisodeWindowItem.Side.PREVIOUS) previousGapFocus else nextGapFocus))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeWindowText(
    text: String,
    fontSize: Int,
    surfaceColors: DetailsSurfaceColors,
    onClick: () -> Unit,
    selected: Boolean = false,
    repeatOnHold: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val interactions = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    var repeated by remember { mutableStateOf(false) }
    LaunchedEffect(pressed, repeatOnHold) {
        repeated = false
        if (pressed && repeatOnHold) {
            delay(420)
            while (pressed) {
                onClick()
                repeated = true
                delay(190)
            }
        }
    }
    val accent = AppColors.NeonCyan
    val textColor = when {
        selected -> accent
        focused -> surfaceColors.text
        else -> surfaceColors.mutedText
    }
    Text(
        text = text,
        color = textColor,
        fontSize = fontSize.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = modifier
            .then(if (focused) Modifier.border(BorderStroke(1.dp, accent), RoundedCornerShape(5.dp)) else Modifier)
            .padding(horizontal = 2.dp, vertical = 3.dp)
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(
                interactionSource = interactions,
                indication = null,
                onClick = { if (!repeated) onClick() },
                onLongClick = if (repeatOnHold) ({ }) else null,
            ),
    )
}
