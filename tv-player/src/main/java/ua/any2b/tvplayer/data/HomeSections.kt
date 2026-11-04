package ua.any2b.tvplayer.data

data class HomeSectionSetting(
    val id: String,
    val title: String,
    val enabled: Boolean = true,
)

object MainHomeSections {
    val defaults = listOf(
        HomeSectionSetting("history", "Історія переглядів"),
        HomeSectionSetting("favorites", "Обране"),
        HomeSectionSetting("recommendations", "Варто переглянути"),
        HomeSectionSetting("trending", "Аніме у тренді"),
        HomeSectionSetting("popular_season", "Аніме популярне цього сезону"),
        HomeSectionSetting("animation_tv", "Популярні анімаційні серіали"),
        HomeSectionSetting("animation_movie", "Популярні повнометражні анімаційні фільми"),
        HomeSectionSetting("mal_top", "Аніме з найвищими оцінками"),
    )
}
