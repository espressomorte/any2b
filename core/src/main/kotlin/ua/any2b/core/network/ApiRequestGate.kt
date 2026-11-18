package ua.any2b.core.network

import java.util.concurrent.atomic.AtomicLong
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/** Shares pacing and server-requested cooldown across clients in one app process. */
class ApiRequestGate(maxConcurrent: Int, private val intervalMs: Long) {
    private val permits = Semaphore(maxConcurrent)
    private val schedule = Mutex()
    private val cooldownUntil = AtomicLong(0L)
    private var nextAtMs = 0L

    fun cooldownSeconds(): Long =
        ((cooldownUntil.get() - nowMs()).coerceAtLeast(0L) + 999L) / 1000L

    fun coolDown(seconds: Long) {
        val until = nowMs() + seconds.coerceIn(1L, 86_400L) * 1000L
        cooldownUntil.accumulateAndGet(until, ::maxOf)
    }

    suspend fun <T> run(onCooldown: (Long) -> Throwable, action: suspend () -> T): T =
        permits.withPermit {
            cooldownSeconds().takeIf { it > 0L }?.let { throw onCooldown(it) }
            val waitMs = schedule.withLock {
                val now = nowMs()
                val scheduled = maxOf(now, nextAtMs)
                nextAtMs = scheduled + intervalMs
                scheduled - now
            }
            if (waitMs > 0L) delay(waitMs)
            cooldownSeconds().takeIf { it > 0L }?.let { throw onCooldown(it) }
            action()
        }

    private fun nowMs(): Long = System.nanoTime() / 1_000_000L
}

/** HTTP Retry-After accepts either seconds or an RFC 1123 date. */
fun retryAfterSeconds(value: String?, fallbackSeconds: Long = 60L): Long {
    val seconds = value?.trim()?.toLongOrNull() ?: value?.let { header ->
        runCatching {
            (ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant().toEpochMilli() - System.currentTimeMillis() + 999L) / 1000L
        }.getOrNull()
    }
    return (seconds ?: fallbackSeconds).coerceIn(1L, 86_400L)
}
