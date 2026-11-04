package ua.any2b.core.tmdb

/** Genres excluded from the general movies and TV catalog. */
object TmdbGeneralCatalog {
    const val ANIMATION = 16
    const val DOCUMENTARY = 99
    const val TALK = 10767
    val excludedGenreIds: Set<Int> = setOf(ANIMATION, DOCUMENTARY, TALK)

    fun accepts(title: TmdbTitle): Boolean = title.genreIds.none { it in excludedGenreIds }
}
