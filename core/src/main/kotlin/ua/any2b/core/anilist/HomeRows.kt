package ua.any2b.core.anilist

const val HOME_PREVIEW_SIZE = 13
const val HOME_FETCH_SIZE = 25

val HomeRow.previewItems: List<AnimeCard> get() = items.take(HOME_PREVIEW_SIZE)
val HomeRow.hasFullList: Boolean get() = items.size > HOME_PREVIEW_SIZE

fun rowsWithLocal(
    catalogRows: List<HomeRow>,
    history: List<AnimeCard>,
    favorites: List<AnimeCard>,
): List<HomeRow> = buildList {
    if (history.isNotEmpty()) add(HomeRow("history", "Історія переглядів", groupNumberedTvSeasons(history)))
    addAll(catalogRows.map { it.copy(items = groupNumberedTvSeasons(it.items)) }
        .filter { it.items.isNotEmpty() })
    if (favorites.isNotEmpty()) add(HomeRow("favorites", "Обране", groupNumberedTvSeasons(favorites)))
}
