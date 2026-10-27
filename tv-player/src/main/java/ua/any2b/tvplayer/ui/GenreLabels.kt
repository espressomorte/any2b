package ua.any2b.tvplayer.ui

private val genreLabels = mapOf(
    "Action" to "Екшн",
    "Adventure" to "Пригоди",
    "Comedy" to "Комедія",
    "Drama" to "Драма",
    "Ecchi" to "Еччі",
    "Fantasy" to "Фентезі",
    "Hentai" to "Хентай",
    "Horror" to "Жахи",
    "Mahou Shoujo" to "Махо-сьодзьо",
    "Mecha" to "Меха",
    "Music" to "Музика",
    "Mystery" to "Містика",
    "Psychological" to "Психологія",
    "Romance" to "Романтика",
    "Sci-Fi" to "Наукова фантастика",
    "Slice of Life" to "Повсякденність",
    "Sports" to "Спорт",
    "Supernatural" to "Надприродне",
    "Thriller" to "Трилер",
)

internal fun genreLabel(genre: String): String = genreLabels[genre] ?: genre
