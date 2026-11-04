package ua.any2b.tvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.tv.material3.*
import ua.any2b.core.anilist.AnimeCard
import ua.any2b.core.anilist.NumberedSeason
import ua.any2b.core.anitube.AnitubeSearchResult
import ua.any2b.core.anitube.AnitubeEpisode
import ua.any2b.core.tmdb.TmdbTitle

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun TmdbMatchDialog(
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
internal fun RelatedDialog(
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
                itemsIndexed(related, key = { index, card -> "related:${card.id}:$index" }) { _, card ->
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
internal fun TolokaQueryEditDialog(
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

@Composable
internal fun ManualReleaseSearchDialog(
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
internal fun ReleaseSelectionDialog(
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
internal fun StudioVoiceDialog(
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SeasonEpisodeDialog(
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
            if (episodes.isNotEmpty()) {
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
internal fun DetailsSynopsisDialog(
    synopsis: String,
    surfaceColors: DetailsSurfaceColors,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(680.dp).heightIn(max = 560.dp)
            .background(surfaceColors.panel, RoundedCornerShape(12.dp))
            .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Синопсис", color = surfaceColors.text,
                style = MaterialTheme.typography.titleLarge)
            Text(synopsis, color = surfaceColors.mutedText)
            OutlinedButton(onClick = onDismiss) { Text("Назад") }
        }
    }
}

@Composable
internal fun TolokaPermanentRejectDialog(
    hitTitle: String,
    surfaceColors: DetailsSurfaceColors,
    onDismiss: () -> Unit,
    onReject: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(surfaceColors.panel,
            RoundedCornerShape(16.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Відхилити роздачу назавжди?", color = surfaceColors.text,
                style = MaterialTheme.typography.titleLarge)
            Text(hitTitle, color = surfaceColors.text)
            Text("Вона більше не показуватиметься для цього тайтлу. Це можна буде скасувати в налаштуваннях пошуку.",
                color = surfaceColors.mutedText)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onDismiss) { Text("Скасувати") }
                Button(onClick = onReject) { Text("Відхилити") }
            }
        }
    }
}
