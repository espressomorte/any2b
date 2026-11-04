package ua.any2b.core.matcher

import kotlin.math.abs

/** Shared year, episode, and duration comparisons used by title matchers. */
object MetadataDistance {
    fun between(first: Int, second: Int): Int = abs(first - second)

    fun withinOrUnknown(first: Int?, second: Int?, tolerance: Int): Boolean =
        first == null || second == null || between(first, second) <= tolerance
}
