package ua.any2b.tvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import ua.any2b.core.anilist.AnimeDetails
import ua.any2b.core.toloka.TolokaHit
import ua.any2b.core.toloka.TolokaReleaseMetadataParser
import ua.any2b.core.toloka.shortenedSearchNames

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
    onPromote: (String) -> Unit = {},
    onDiscard: (String) -> Unit = {},
    onRejectForever: (String) -> Unit = {},
    onRetryMetadata: () -> Unit,
    onRetryTorrServer: () -> Unit = onRetryMetadata,
    onOpenTorrServe: () -> Unit = {},
    onPlayFile: (Int) -> Unit,
    onEditQuery: (() -> Unit)? = null,
    manualSearch: Boolean = false,
    seasonEntries: List<ua.any2b.core.tmdb.TmdbSeason> = emptyList(),
    surfaceColors: DetailsSurfaceColors = detailsSurfaceColors(null, false),
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val displayedQuery = (searchState as? TolokaSearchState.Results)?.query ?: query
        val resultCount = (searchState as? TolokaSearchState.Results)
            ?.takeIf { it.query == query.trim() }
            ?.let { "Підхожих роздач: ${it.hits.size} із ${it.totalFound}" }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            resultCount?.let { count ->
                Text(count, modifier = Modifier.widthIn(max = 240.dp),
                    color = surfaceColors.mutedText, fontSize = 12.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            Text(
                text = "Запит: $displayedQuery",
                modifier = Modifier.weight(1f).then(if (onEditQuery != null) Modifier
                    .clickable(onClick = onEditQuery)
                    .semantics { contentDescription = "Змінити пошуковий запит Toloka: $displayedQuery" }
                    else Modifier),
                color = surfaceColors.mutedText, fontSize = 12.sp, maxLines = 1,
                textAlign = TextAlign.End,
                overflow = TextOverflow.Ellipsis,
            )
            val searchEnabled = query.isNotBlank() && searchState !is TolokaSearchState.Loading
            var searchFocused by remember { mutableStateOf(false) }
            Box(
                Modifier.size(24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (searchFocused) AppColors.NeonCyan.copy(alpha = 0.22f)
                        else Color.Transparent)
                    .onFocusChanged { searchFocused = it.isFocused }
                    .clickable(enabled = searchEnabled, role = Role.Button, onClick = onSearch)
                    .semantics { contentDescription = "Шукати за запитом: $displayedQuery" },
                contentAlignment = Alignment.Center,
            ) {
                FontAwesomeIcon(FaIcons.SEARCH, size = 14.sp,
                    color = if (searchFocused) AppColors.NeonCyan else surfaceColors.mutedText)
            }
        }
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
                var showRejected by rememberSaveable(searchState.query) { mutableStateOf(false) }
                var pendingPermanentReject by rememberSaveable(searchState.query) {
                    mutableStateOf<String?>(null)
                }
                if (searchState.query != query.trim()) {
                    Text("Пошук за новим запитом…",
                        color = AppColors.TextSecondary)
                } else {
                    if (searchState.hits.isEmpty()) Text(
                        if (searchState.weakHits.isNotEmpty()) "Точних збігів немає. Можна переглянути сумнівні роздачі нижче."
                        else if (manualSearch) "Відеороздач за цим запитом не знайдено."
                        else "Підхожих роздач немає. Знайдено: ${searchState.totalFound}; відсіяно за назвою, роком або сезоном.",
                        color = surfaceColors.text)
                    val selectedWeak = searchState.selectedId?.let { id ->
                        searchState.weakHits.any { it.id == id }
                    } == true
                    val visibleHits = searchState.hits +
                        if (showWeak || selectedWeak) searchState.weakHits else emptyList()
                    visibleHits.forEach { hit ->
                        val canPromote = hit in searchState.weakHits
                        TolokaResultRow(
                            hit = hit,
                            selected = hit.id == searchState.selectedId,
                            opened = hit.id in openedTopics,
                            enabled = transferState !is TolokaTransferState.Loading,
                            onClick = { onSelect(hit.id) },
                            onPromote = if (canPromote) {{ onPromote(hit.id) }} else null,
                            onDiscard = if (canPromote) {{ onDiscard(hit.id) }} else null,
                            onRejectForever = { pendingPermanentReject = hit.id },
                            seasonEntries = seasonEntries,
                            surfaceColors = surfaceColors,
                        )
                        if (hit.id == searchState.selectedId) {
                            TolokaTransferContent(transferState, playbackStatus, openedFiles,
                                onSearch, onRetryMetadata, onRetryTorrServer, onOpenTorrServe, onPlayFile, surfaceColors)
                        }
                    }
                    if (searchState.weakHits.isNotEmpty()) {
                        val weakHitsLabel = if (showWeak || selectedWeak)
                            "Згорнути сумнівні збіги"
                        else "…Показати ще ${searchState.weakHits.size} сумнівних збігів"
                        Button(onClick = { showWeak = !showWeak },
                            modifier = Modifier.fillMaxWidth()) {
                            Text(weakHitsLabel, fontSize = 14.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                    }
                    if (searchState.rejectedHits.isNotEmpty()) {
                        Button(onClick = { showRejected = !showRejected },
                            modifier = Modifier.fillMaxWidth()) {
                            Text(if (showRejected) "Згорнути відсіяні роздачі"
                                else "Поритися у відсіяному · ${searchState.rejectedHits.size}")
                        }
                        if (showRejected) searchState.rejectedHits.forEach { hit ->
                            TolokaResultRow(
                                hit = hit, selected = hit.id == searchState.selectedId,
                                opened = hit.id in openedTopics,
                                enabled = transferState !is TolokaTransferState.Loading,
                                onClick = { onSelect(hit.id) },
                                onPromote = { onPromote(hit.id) },
                                onDiscard = null,
                                onRejectForever = { pendingPermanentReject = hit.id },
                                seasonEntries = seasonEntries,
                                surfaceColors = surfaceColors,
                            )
                        }
                    }
                    val pendingHit = pendingPermanentReject?.let { pendingId ->
                        (searchState.hits + searchState.weakHits + searchState.rejectedHits)
                            .firstOrNull { it.id == pendingId }
                    }
                    if (pendingHit != null) {
                        TolokaPermanentRejectDialog(
                            hitTitle = pendingHit.title,
                            surfaceColors = surfaceColors,
                            onDismiss = { pendingPermanentReject = null },
                            onReject = {
                                onRejectForever(pendingHit.id)
                                pendingPermanentReject = null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun TorrentTitleHeading(
    ukrainianTitle: String?,
    englishTitle: String?,
    modifier: Modifier = Modifier,
    surfaceColors: DetailsSurfaceColors,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ukrainianTitle?.takeIf(String::isNotBlank)?.let { title ->
            Text(title, color = surfaceColors.text, fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        englishTitle?.takeIf { it.isNotBlank() && it != ukrainianTitle }?.let { title ->
            Text(title, color = surfaceColors.mutedText, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

internal fun tolokaQueryOptions(details: AnimeDetails, releaseTitle: String?,
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

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun TolokaTransferContent(
    transferState: TolokaTransferState,
    playbackStatus: String?,
    openedFiles: Set<Int>,
    onSearch: () -> Unit,
    onRetryMetadata: () -> Unit,
    onRetryTorrServer: () -> Unit,
    onOpenTorrServe: () -> Unit,
    onPlayFile: (Int) -> Unit,
    surfaceColors: DetailsSurfaceColors,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
    when (transferState) {
        TolokaTransferState.Idle -> Unit
        is TolokaTransferState.Loading ->
            Text("Готуємо відтворення…", color = AppColors.TextLight)
        is TolokaTransferState.Error -> {
            Text(transferState.message, color = AppColors.ErrorRed)
            Text("Останній успішний етап: ${transferState.lastSuccess}", color = AppColors.TextSecondary,
                fontSize = 12.sp)
            if (transferState.torrServerUnavailable) {
                Button(onClick = onOpenTorrServe) { Text("Відкрити TorrServe") }
                Button(onClick = onRetryTorrServer) { Text("Повторити перевірку") }
            }
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
    onClick: () -> Unit, onPromote: (() -> Unit)?, onDiscard: (() -> Unit)?,
    onRejectForever: () -> Unit,
    seasonEntries: List<ua.any2b.core.tmdb.TmdbSeason>,
    surfaceColors: DetailsSurfaceColors) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> AppColors.NeonCyanLight
        selected -> AppColors.NeonCyan
        else -> surfaceColors.card
    }
    val foreground = if (focused || selected) AppColors.ButtonDarkText else surfaceColors.text
    val release = remember(hit.title) { TolokaReleaseMetadataParser.parse(hit.title) }
    val resolution = release.qualityTags.firstOrNull { it.matches(Regex("\\d{3,4}p")) }
    val source = release.qualityTags.firstOrNull { it in setOf("WEB-DL", "WEBRip", "WEB", "BluRay", "HDTV") }
    val combinedQuality = listOfNotNull(source, resolution).joinToString(" · ")
    val otherQuality = release.qualityTags.filterNot { it == resolution || it == source }
    val absoluteRanges = remember(release, seasonEntries) {
        TolokaReleaseMetadataParser.mapAbsoluteEpisodes(release, seasonEntries)
    }
    val parsedSeasonTags = release.qualityTags.filter { it.startsWith("S") }
        .map { tag ->
            when {
                tag.matches(Regex("S\\d{2}–\\d{2}")) -> {
                    val range = Regex("S(\\d{2})–(\\d{2})").matchEntire(tag)!!
                    "Сезони ${range.groupValues[1].toInt()}–${range.groupValues[2].toInt()}"
                }
                tag.matches(Regex("S\\d{2}")) -> "Сезон ${tag.drop(1).toIntOrNull() ?: tag.drop(1)}"
                else -> tag
            }
        }
    val seasonTags = absoluteRanges?.map { "Сезон ${it.seasonNumber}" }?.distinct() ?: parsedSeasonTags
    val parsedEpisodeTags = release.qualityTags.filter { it.startsWith("E") }.map { tag ->
        when {
            tag.matches(Regex("E\\d{2}–\\d{2}")) -> "Серії ${tag.drop(1)}"
            tag.matches(Regex("E\\d{2}")) -> "Серія ${tag.drop(1)}"
            else -> tag
        }
    }
    val episodeTags = when {
        absoluteRanges != null && absoluteRanges.size == 1 -> absoluteRanges.map { range ->
            "Серії ${range.episodeStart}–${range.episodeEnd}" +
                (release.episodeTotal?.let { " (із $it)" } ?: "")
        }
        absoluteRanges != null -> absoluteRanges.map { range ->
            "Серії ${range.episodeStart}–${range.episodeEnd}"
        } + listOfNotNull(release.episodeEnd?.let { end ->
            release.episodeTotal?.let { "Пакет 1–$end із $it" }
        })
        release.isAbsoluteEpisodeRange -> listOfNotNull(release.episodeEnd?.let { end ->
            "Абсолютні 1–$end" + (release.episodeTotal?.let { " / $it" } ?: "")
        })
        else -> parsedEpisodeTags
    }
    val displayQuality = listOfNotNull(combinedQuality.takeIf(String::isNotBlank)) +
        otherQuality.filterNot { it.startsWith("S") || it.startsWith("E") }
    val releaseChips = buildList {
        seasonTags.forEach { add(ReleaseTagChip(it, ReleaseTagKind.SEASON)) }
        episodeTags.forEach { add(ReleaseTagChip(it, ReleaseTagKind.SEASON)) }
        release.audioTags.forEach { add(ReleaseTagChip(it, ReleaseTagKind.AUDIO)) }
        release.languageTags.forEach { add(ReleaseTagChip(it, ReleaseTagKind.AUDIO)) }
        release.subtitleTags.forEach { add(ReleaseTagChip(it, ReleaseTagKind.SUBTITLE)) }
        displayQuality.forEach { add(ReleaseTagChip(it, ReleaseTagKind.QUALITY)) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically) {
    Card(
        onClick = { if (enabled) onClick() },
        onLongClick = { if (enabled) onRejectForever() },
        scale = CardDefaults.scale(scale = 1f, focusedScale = 1f),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        colors = CardDefaults.colors(containerColor = background, focusedContainerColor = background),
        modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused }
            .neonGlow(focused = focused, borderRadius = 10.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                text = if (focused) hit.title else release.cleanTitle,
                modifier = Modifier.fillMaxWidth().then(if (focused) Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE, initialDelayMillis = 700, velocity = 42.dp)
                    else Modifier),
                color = foreground, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false,
            )
            if (releaseChips.isNotEmpty()) ReleaseTagRow(releaseChips, surfaceColors)
            if (hit.sizeText.isNotBlank() || hit.seeders != null || hit.leechers != null || opened) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (hit.sizeText.isNotBlank()) Text(hit.sizeText, modifier = Modifier.weight(1f),
                        color = foreground, fontSize = 12.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    if (hit.sizeText.isBlank()) Spacer(Modifier.weight(1f))
                    hit.author?.takeIf(String::isNotBlank)?.let { author ->
                        Text(author, modifier = Modifier.widthIn(max = 180.dp),
                            color = foreground, fontSize = 12.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                    hit.seeders?.let { Text("↑ $it", color = Color(0xFF218A58), fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold) }
                    hit.leechers?.let { Text("↓ $it", color = surfaceColors.mutedText, fontSize = 12.sp) }
                    if (opened) FontAwesomeIcon(FaIcons.EYE, color = foreground, size = 14.sp)
                }
            }
        }
    }
    if (onPromote != null) {
        Column(Modifier.width(42.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            var promoteFocused by remember { mutableStateOf(false) }
            Box(Modifier.size(24.dp)
                .background(if (promoteFocused) AppColors.NeonCyanLight else Color.Transparent,
                    RoundedCornerShape(5.dp))
                .clickable(enabled = enabled) { onPromote() }
                .focusable(enabled = enabled)
                .onFocusChanged { promoteFocused = it.isFocused },
                contentAlignment = Alignment.Center) {
                Text("+", color = if (promoteFocused) AppColors.ButtonDarkText
                    else surfaceColors.mutedText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            if (onDiscard != null) {
                var discardFocused by remember { mutableStateOf(false) }
                Box(Modifier.size(24.dp)
                    .background(if (discardFocused) AppColors.NeonCyanLight else Color.Transparent,
                        RoundedCornerShape(5.dp))
                    .clickable(enabled = enabled) { onDiscard() }
                    .focusable(enabled = enabled)
                    .onFocusChanged { discardFocused = it.isFocused },
                    contentAlignment = Alignment.Center) {
                    FontAwesomeIcon(FaIcons.TRASH_O,
                        color = if (discardFocused) AppColors.ButtonDarkText else surfaceColors.mutedText,
                        size = 12.sp)
                }
            }
        }
    }
    }
}

private enum class ReleaseTagKind { QUALITY, AUDIO, SUBTITLE, SEASON }

private data class ReleaseTagChip(val label: String, val kind: ReleaseTagKind)

@Composable
private fun ReleaseTagRow(chips: List<ReleaseTagChip>, surfaceColors: DetailsSurfaceColors) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        chips.forEach { chip ->
            val tag = chip.label
            val tint = when (chip.kind) {
                ReleaseTagKind.QUALITY -> Color(0xFF8995A6)
                ReleaseTagKind.AUDIO, ReleaseTagKind.SUBTITLE -> AppColors.NeonCyan
                ReleaseTagKind.SEASON -> AppColors.PrimaryPurple
            }
            val background = lerp(surfaceColors.card, tint, when (chip.kind) {
                ReleaseTagKind.QUALITY -> 0.16f
                ReleaseTagKind.AUDIO -> 0.20f
                ReleaseTagKind.SUBTITLE -> 0.14f
                ReleaseTagKind.SEASON -> 0.20f
            })
            val foreground = surfaceColors.text
            Row(Modifier.clip(RoundedCornerShape(6.dp)).background(background)
                .padding(horizontal = 7.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically) {
                when (chip.kind) {
                    ReleaseTagKind.AUDIO -> FontAwesomeIcon(FaIcons.MICROPHONE,
                        color = foreground, size = 11.sp)
                    ReleaseTagKind.SUBTITLE -> FontAwesomeIcon(FaIcons.COMMENTING_O,
                        color = foreground, size = 11.sp)
                    else -> Unit
                }
                Text(tag, color = foreground, fontSize = 10.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
