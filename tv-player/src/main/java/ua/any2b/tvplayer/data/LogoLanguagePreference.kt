package ua.any2b.tvplayer.data

object LogoLanguagePreference {
    const val ORIGINAL = "original"
    const val ENGLISH = "en"
    const val UKRAINIAN = "uk"

    val defaults = listOf(ORIGINAL, ENGLISH, UKRAINIAN)

    fun label(id: String): String = when (id) {
        ORIGINAL -> "Оригінал"
        ENGLISH -> "Англійська"
        UKRAINIAN -> "Українська"
        else -> id
    }
}
