package ua.any2b.tvplayer.data

data class HomeSectionSetting(
    val id: String,
    val title: String,
    val enabled: Boolean = true,
)

object HomeSections {
    val defaults = listOf(
        HomeSectionSetting("history", "Історія переглядів"),
        HomeSectionSetting("popular_season", "Популярне цього сезону"),
        HomeSectionSetting("trending", "У тренді"),
        HomeSectionSetting("upcoming", "Скоро: наступний сезон"),
        HomeSectionSetting("popular_all", "Популярне за весь час"),
        HomeSectionSetting("top_score", "Найвищі оцінки"),
        HomeSectionSetting("favorites", "Обране"),
    )
}
