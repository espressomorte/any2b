package ua.any2b.tvplayer.data

data class HomeSectionSetting(
    val id: String,
    val title: String,
    val enabled: Boolean = true,
    val page: String = PAGE_WATCHLIST,
)

const val PAGE_WATCHLIST = "watchlist"
const val PAGE_TMDB = "tmdb"

object MainHomeSections {
    val defaults = listOf(
        HomeSectionSetting("history", "Історія переглядів", page = PAGE_WATCHLIST),
        HomeSectionSetting("favorites", "Обране", page = PAGE_WATCHLIST),
        HomeSectionSetting("recommendations", "Варто переглянути", page = PAGE_WATCHLIST),
        HomeSectionSetting("trending", "Аніме у тренді", page = PAGE_WATCHLIST),
        HomeSectionSetting("popular_season", "Аніме популярне цього сезону", page = PAGE_WATCHLIST),
        HomeSectionSetting("animation_tv", "Популярні анімаційні серіали", page = PAGE_WATCHLIST),
        HomeSectionSetting("animation_movie", "Популярні повнометражні анімаційні фільми", page = PAGE_WATCHLIST),
        HomeSectionSetting("mal_top", "Аніме з найвищими оцінками", page = PAGE_WATCHLIST),
        HomeSectionSetting("toloka_animation", "Анімація Toloka", page = PAGE_WATCHLIST),
        HomeSectionSetting("tmdb_whats_popular", "What's Popular · TMDB", page = PAGE_TMDB),
        HomeSectionSetting("tmdb_mdblist_popular_movies", "Popular Movies · MDBList", page = PAGE_TMDB),
        HomeSectionSetting("tmdb_toloka_video", "Фільми й серіали Toloka", page = PAGE_TMDB),
        HomeSectionSetting("tmdb_history", "Історія переглядів", page = PAGE_TMDB),
        HomeSectionSetting("tmdb_favorites", "Обране", page = PAGE_TMDB),
        HomeSectionSetting("tmdb_tv", "Популярні серіали", page = PAGE_TMDB),
        HomeSectionSetting("tmdb_movies", "Популярні фільми", page = PAGE_TMDB),
    )
}
