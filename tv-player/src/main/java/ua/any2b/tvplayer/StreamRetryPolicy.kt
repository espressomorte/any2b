package ua.any2b.tvplayer

import kotlin.random.Random

/** Retry timing for transient stream failures: exponential backoff with 50% jitter. */
internal object StreamRetryPolicy {
    const val MAX_RETRIES = 4

    fun delayBoundsMs(retryCount: Int): LongRange? {
        if (retryCount !in 0 until MAX_RETRIES) return null
        val baseMs = (1_000L shl retryCount).coerceAtMost(8_000L)
        return (baseMs / 2)..(baseMs + baseMs / 2)
    }

    fun nextDelayMs(retryCount: Int, random: Random = Random.Default): Long? {
        val bounds = delayBoundsMs(retryCount) ?: return null
        return random.nextLong(bounds.first, bounds.last + 1L)
    }
}
