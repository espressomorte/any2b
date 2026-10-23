package ua.any2b.core.anilist

import java.util.Calendar

/**
 * Сезони AniList. Межі беремо календарними кварталами:
 * зима січень–березень, весна квітень–червень, літо липень–вересень, осінь жовтень–грудень.
 */
enum class Season {
    WINTER, SPRING, SUMMER, FALL;

    /** Наступний сезон і його рік. */
    fun next(year: Int): Pair<Season, Int> = when (this) {
        WINTER -> SPRING to year
        SPRING -> SUMMER to year
        SUMMER -> FALL to year
        FALL -> WINTER to year + 1
    }

    companion object {
        /** [month] у діапазоні 1..12. */
        fun of(month: Int): Season = when (month) {
            in 1..3 -> WINTER
            in 4..6 -> SPRING
            in 7..9 -> SUMMER
            else -> FALL
        }

        /** Поточний сезон і рік (Calendar для сумісності з Android minSdk 21). */
        fun current(cal: Calendar = Calendar.getInstance()): Pair<Season, Int> =
            of(cal.get(Calendar.MONTH) + 1) to cal.get(Calendar.YEAR)
    }
}
