package ua.any2b.tvplayer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbPersonCredit
import ua.any2b.core.tmdb.TmdbPersonRole
import ua.any2b.core.tmdb.TmdbPersonWork
import ua.any2b.core.tmdb.TmdbTitle

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TmdbPeopleRow(state: TmdbPeopleState, selectedPersonId: Int?,
    onSelectedPerson: (Int) -> Unit, onOpen: (TmdbPersonCredit) -> Unit,
    onRetry: () -> Unit, onContentFocusRequester: (FocusRequester) -> Unit = {},
    onSectionFocus: () -> Unit = {},
    returnToken: Int = 0) {
    val people = (state as? TmdbPeopleState.Ready)?.people.orEmpty()
    if (state is TmdbPeopleState.Ready && people.isEmpty()) return
    val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
    val returnFocus = remember { FocusRequester() }
    LaunchedEffect(returnToken) {
        if (returnToken <= 0 || selectedPersonId == null) return@LaunchedEffect
        val index = people.indexOfFirst { it.id == selectedPersonId }
        if (index >= 0) {
            rowState.scrollToItem(index)
            withFrameNanos { }
            runCatching { returnFocus.requestFocus() }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Творці та актори", modifier = Modifier.padding(start = 40.dp),
            color = Color.White, style = MaterialTheme.typography.titleLarge)
        when (state) {
            TmdbPeopleState.Loading -> Text("Завантаження персон…",
                modifier = Modifier.padding(start = 40.dp), color = AppColors.TextSecondary)
            is TmdbPeopleState.Error -> Row(Modifier.padding(start = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(state.message, color = AppColors.TextSecondary)
                ListSortButton("Повторити", false, onRetry)
            }
            is TmdbPeopleState.Ready -> LazyRow(state = rowState,
                modifier = Modifier.focusGroup(),
                contentPadding = PaddingValues(horizontal = 40.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                items(people, key = { it.id }) { person ->
                    val personFocus = remember(person.id) { FocusRequester() }
                    val activeFocus = if (returnToken > 0 && person.id == selectedPersonId)
                        returnFocus else personFocus
                    PersonAvatarCard(person,
                        modifier = Modifier.focusRequester(activeFocus),
                        onFocus = {
                            onSectionFocus()
                            onSelectedPerson(person.id)
                            onContentFocusRequester(activeFocus)
                        },
                        onClick = { onSelectedPerson(person.id); onOpen(person) })
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PersonAvatarCard(person: TmdbPersonCredit, modifier: Modifier = Modifier,
    onFocus: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(142.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Card(onClick = onClick,
            shape = CardDefaults.shape(CircleShape),
            scale = CardDefaults.scale(scale = 1f, focusedScale = 1.06f),
            border = CardDefaults.border(
                border = androidx.tv.material3.Border(BorderStroke(0.dp, Color.Transparent)),
                focusedBorder = androidx.tv.material3.Border(BorderStroke(3.dp, AppColors.NeonCyan))),
            colors = CardDefaults.colors(containerColor = AppColors.SurfaceCard,
                focusedContainerColor = AppColors.SurfaceCard),
            modifier = modifier.size(112.dp).onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }) {
            PersonAvatar(person.name, person.profileUrl, Modifier.fillMaxSize())
        }
        Text(person.name, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(40.dp),
            color = if (focused) AppColors.NeonCyan else Color.White,
            textAlign = TextAlign.Center, maxLines = 2, minLines = 2,
            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
        Text(person.roleLabel, modifier = Modifier.fillMaxWidth().height(42.dp),
            color = AppColors.TextSecondary, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun PersonAvatar(name: String, url: String?, modifier: Modifier = Modifier) {
    var failed by remember(url) { mutableStateOf(false) }
    Box(modifier.clip(CircleShape).background(AppColors.SurfaceCard),
        contentAlignment = Alignment.Center) {
        if (url != null && !failed) AsyncImage(model = url, contentDescription = name,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            onError = { failed = true })
        else Canvas(Modifier.size(52.dp)) {
            val headRadius = size.minDimension * 0.17f
            drawCircle(AppColors.TextSecondary, radius = headRadius,
                center = Offset(size.width / 2f, size.height * 0.34f))
            drawOval(AppColors.TextSecondary, topLeft = Offset(size.width * 0.16f,
                size.height * 0.57f), size = Size(size.width * 0.68f, size.height * 0.38f))
        }
    }
}

@Composable
fun TmdbPersonWorksScreen(state: TmdbFilmographyState, onBack: () -> Unit,
    onRetry: () -> Unit, onOpen: (TmdbTitle) -> Unit, returnToken: Int = 0) {
    var roleFilter by rememberSaveable { mutableStateOf("all") }
    var moreCategory by rememberSaveable { mutableStateOf(false) }
    var typeFilter by rememberSaveable { mutableStateOf("all") }
    var sortOrder by rememberSaveable { mutableStateOf("popularity") }
    var openMenu by remember { mutableStateOf<String?>(null) }
    var visibleCount by rememberSaveable { mutableIntStateOf(30) }
    var focusedWork by rememberSaveable { mutableStateOf<String?>(null) }
    var focusedGridRow by remember { mutableStateOf<Int?>(null) }
    val gridState = rememberLazyGridState()
    val returnFocus = remember { FocusRequester() }
    val data = (state as? TmdbFilmographyState.Ready)?.data
    val all = data?.works.orEmpty()
    val categoryWorks = all.filter { it.isMoreCategory == moreCategory }
    val moreCount = all.count { it.isMoreCategory }
    val filtered = categoryWorks.filter { work ->
        (roleFilter == "all" || when (roleFilter) {
            "director" -> TmdbPersonRole.DIRECTOR in work.roles
            "writer" -> TmdbPersonRole.WRITER in work.roles
            else -> TmdbPersonRole.VOICE in work.roles
        }) && (typeFilter == "all" || work.title.type.path == typeFilter)
    }.sortedWith(when (sortOrder) {
        "chronological" -> compareBy<TmdbPersonWork> { it.title.year ?: Int.MAX_VALUE }
            .thenByDescending { it.title.popularity ?: 0.0 }
        else -> compareByDescending<TmdbPersonWork> { it.title.popularity ?: 0.0 }
            .thenByDescending { it.title.year ?: 0 }
    })
    val shown = filtered.take(visibleCount)
    LaunchedEffect(focusedGridRow) {
        val row = focusedGridRow ?: return@LaunchedEffect
        withFrameNanos { }
        gridState.animateScrollToItem(row * 6)
    }
    LaunchedEffect(gridState, filtered.size, visibleCount) {
        if (shown.size < filtered.size) snapshotFlow {
            gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }.collect { last -> if (last >= shown.size - 7) visibleCount += 30 }
    }
    LaunchedEffect(returnToken, state) {
        if (returnToken <= 0 || focusedWork == null) return@LaunchedEffect
        val index = shown.indexOfFirst { "${it.title.type.path}:${it.title.id}" == focusedWork }
        if (index >= 0) {
            gridState.scrollToItem(index)
            withFrameNanos { }
            runCatching { returnFocus.requestFocus() }
        }
    }
    Column(Modifier.fillMaxSize().background(AppColors.BackgroundDark)
        .padding(horizontal = 40.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PersonTextButton("← Назад", onBack)
        data?.person?.let { person ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                PersonAvatar(person.name, person.profileUrl, Modifier.size(68.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(person.name, color = Color.White,
                        style = MaterialTheme.typography.headlineMedium)
                    person.originalName?.let { Text(it, color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium) }
                    Text(buildList {
                        if (all.any { TmdbPersonRole.DIRECTOR in it.roles }) add("Режисура")
                        if (all.any { TmdbPersonRole.WRITER in it.roles }) add("Сценарій")
                        if (all.any { TmdbPersonRole.VOICE in it.roles }) add("Озвучення")
                        if (all.any { TmdbPersonRole.ACTOR in it.roles }) add("Актор")
                        all.flatMap { it.otherJobs }.map { ua.any2b.core.tmdb.localizedTmdbJob(it) }
                            .distinct().take(3).forEach(::add)
                    }.joinToString(" · "), color = AppColors.TextSecondary)
                }
            }
        }
        when (state) {
            TmdbFilmographyState.Loading -> Text("Завантаження робіт…",
                color = AppColors.TextSecondary)
            is TmdbFilmographyState.Error -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.message, color = AppColors.TextSecondary)
                ListSortButton("Повторити", false, onRetry)
            }
            is TmdbFilmographyState.Ready -> {
                if (all.isEmpty()) Text("Робіт не знайдено",
                    color = AppColors.TextSecondary)
                else {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Фільмографія", color = Color.White,
                            style = MaterialTheme.typography.titleLarge)
                        Text("${filtered.size} робіт", color = AppColors.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()) {
                        PersonTab("Усі", typeFilter == "all", onClick = { typeFilter = "all" })
                        if (categoryWorks.any { it.title.type == TmdbMediaType.TV })
                            PersonTab("Серіали", typeFilter == "tv", onClick = { typeFilter = "tv" })
                        if (categoryWorks.any { it.title.type == TmdbMediaType.MOVIE })
                            PersonTab("Фільми", typeFilter == "movie", onClick = { typeFilter = "movie" })
                        Spacer(Modifier.weight(1f))
                        val participationKey = if (moreCategory) "more" else roleFilter
                        val participationOptions = buildList {
                            add("all" to "Усі основні роботи")
                            if (all.any { TmdbPersonRole.DIRECTOR in it.roles })
                                add("director" to "Режисура")
                            if (all.any { TmdbPersonRole.WRITER in it.roles })
                                add("writer" to "Сценарій")
                            if (all.any { TmdbPersonRole.VOICE in it.roles })
                                add("voice" to "Озвучення")
                            if (moreCount > 0) add("more" to "Більше · $moreCount")
                        }
                        PersonDropdown("participation", "Участь: ${if (moreCategory) "Більше" else when (roleFilter) {
                            "director" -> "Режисура"; "writer" -> "Сценарій"
                            "voice" -> "Озвучення"; else -> "Усі"
                        }}", participationOptions, participationKey, openMenu,
                            onOpenMenuChange = { openMenu = it }, onSelected = { key ->
                                moreCategory = key == "more"
                                roleFilter = if (moreCategory) "all" else key
                            })
                        PersonTextButton(if (sortOrder == "chronological")
                            "Хронологічно" else "За популярністю",
                            onClick = { sortOrder = if (sortOrder == "chronological")
                                "popularity" else "chronological" }, value = true)
                    }
                    if (filtered.isEmpty()) Text("За цими фільтрами робіт немає",
                        color = AppColors.TextSecondary)
                    BoxWithConstraints(Modifier.weight(1f)) {
                        val gridHeight = maxHeight
                        LazyVerticalGrid(columns = GridCells.Fixed(6), state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = gridHeight),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            items(shown, key = { "${it.title.type.path}:${it.title.id}" }) { work ->
                                Column {
                                    val key = "${work.title.type.path}:${work.title.id}"
                                    TmdbPosterCard(work.title, onClick = { onOpen(work.title) },
                                        onFocused = {
                                            focusedWork = key
                                            val index = shown.indexOfFirst {
                                                it.title.type == work.title.type &&
                                                    it.title.id == work.title.id
                                            }
                                            if (index >= 0 && focusedGridRow != index / 6)
                                                focusedGridRow = index / 6
                                        },
                                        focusRequester = if (returnToken > 0 && key == focusedWork)
                                            returnFocus else null)
                                    Text(work.roleLabel, modifier = Modifier.width(160.dp)
                                        .padding(horizontal = 4.dp), color = AppColors.TextSecondary,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            if (shown.size < filtered.size) item(key = "more") {
                                PersonTextButton("Показати ще", onClick = { visibleCount += 30 })
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PersonTextButton(label: String, onClick: () -> Unit,
    value: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    Card(onClick = onClick, shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
        scale = CardDefaults.scale(scale = 1f, focusedScale = 1f),
        colors = CardDefaults.colors(containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent),
        border = CardDefaults.border(border = Border(BorderStroke(0.dp, Color.Transparent)),
            focusedBorder = Border(BorderStroke(0.dp, Color.Transparent))),
        modifier = Modifier.height(34.dp).onFocusChanged { focused = it.isFocused }) {
        Box(Modifier.padding(horizontal = 8.dp).height(34.dp),
            contentAlignment = Alignment.Center) {
            Text(label, color = if (focused) AppColors.IceWhite
                else if (value) AppColors.NeonCyan else AppColors.TextLight,
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PersonTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PersonTextButton(label, onClick)
        Box(Modifier.width(36.dp).height(2.dp).background(
            if (selected) AppColors.NeonCyan else Color.Transparent))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PersonDropdown(id: String, label: String, options: List<Pair<String, String>>,
    selectedKey: String, openMenu: String?, onOpenMenuChange: (String?) -> Unit,
    onSelected: (String) -> Unit) {
    val triggerFocus = remember(id) { FocusRequester() }
    val selectedFocus = remember(id) { FocusRequester() }
    var triggerFocused by remember { mutableStateOf(false) }
    var openedOnce by remember { mutableStateOf(false) }
    var buttonWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val menuWidth = maxOf(with(density) { buttonWidthPx.toDp() },
        8.dp * (options.maxOfOrNull { it.second.length } ?: 0) + 48.dp)
        .coerceAtMost(screenWidth - 32.dp)
    val isOpen = openMenu == id
    LaunchedEffect(isOpen) {
        if (isOpen) {
            openedOnce = true
            withFrameNanos { }
            runCatching { selectedFocus.requestFocus() }
        } else if (openedOnce && openMenu == null) {
            withFrameNanos { }
            runCatching { triggerFocus.requestFocus() }
        }
    }
    Box {
        Card(onClick = { onOpenMenuChange(id) },
            scale = CardDefaults.scale(scale = 1f, focusedScale = 1f),
            colors = CardDefaults.colors(containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent),
            border = CardDefaults.border(border = Border(BorderStroke(0.dp, Color.Transparent)),
                focusedBorder = Border(BorderStroke(0.dp, Color.Transparent))),
            modifier = Modifier.height(36.dp).focusRequester(triggerFocus)
                .onSizeChanged { buttonWidthPx = it.width }
                .onFocusChanged { triggerFocused = it.isFocused }) {
            Row(Modifier.padding(horizontal = 8.dp).height(36.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(label, color = if (triggerFocused) AppColors.IceWhite
                    else AppColors.NeonCyan, style = MaterialTheme.typography.bodyMedium)
                FontAwesomeIcon(FaIcons.CHEVRON_CIRCLE_DOWN,
                    color = if (triggerFocused) AppColors.IceWhite else AppColors.TextLight,
                    size = 12.sp)
            }
        }
        if (isOpen) Popup(alignment = Alignment.BottomEnd,
            offset = IntOffset(0, with(density) { 2.dp.roundToPx() }),
            onDismissRequest = { onOpenMenuChange(null) },
            properties = PopupProperties(focusable = true,
                dismissOnBackPress = true, dismissOnClickOutside = true)) {
            Column(Modifier.width(menuWidth).background(AppColors.SurfaceCard,
                RoundedCornerShape(10.dp)).padding(vertical = 4.dp).focusGroup()) {
                options.forEach { (key, optionLabel) ->
                    var itemFocused by remember(id, key) { mutableStateOf(false) }
                    val selected = key == selectedKey
                    Card(onClick = { onSelected(key); onOpenMenuChange(null) },
                        scale = CardDefaults.scale(scale = 1f, focusedScale = 1f),
                        colors = CardDefaults.colors(containerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent),
                        border = CardDefaults.border(
                            border = Border(BorderStroke(0.dp, Color.Transparent)),
                            focusedBorder = Border(BorderStroke(0.dp, Color.Transparent))),
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                            .then(if (selected) Modifier.focusRequester(selectedFocus) else Modifier)
                            .onFocusChanged { itemFocused = it.isFocused }) {
                        Row(Modifier.fillMaxWidth().height(40.dp)
                            .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (selected) "✓" else " ",
                                color = if (selected) AppColors.NeonCyan else Color.Transparent,
                                style = MaterialTheme.typography.bodyMedium)
                            Text(optionLabel, color = when {
                                selected -> AppColors.NeonCyan
                                itemFocused -> AppColors.IceWhite
                                else -> AppColors.TextLight
                            }, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
