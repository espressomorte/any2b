package ua.any2b.core.network

import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ApiRequestKey(
    method: String,
    val url: String,
    headers: List<Pair<String, String>>,
    val body: String? = null,
) {
    val method: String = method.uppercase(Locale.ROOT)
    // Header names and the order of different headers do not affect an HTTP request.
    // Keep values in their original order within each repeated header name.
    val headers: List<Pair<String, String>> = headers
        .groupBy { it.first.lowercase(Locale.ROOT) }
        .toSortedMap()
        .flatMap { (name, values) -> values.map { name to it.second } }

    override fun equals(other: Any?): Boolean = other is ApiRequestKey &&
        method == other.method && url == other.url && headers == other.headers &&
        body == other.body

    override fun hashCode(): Int {
        var result = method.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + headers.hashCode()
        result = 31 * result + (body?.hashCode() ?: 0)
        return result
    }
}

/** Coalesces concurrent identical reads, without retaining completed responses. */
class ApiSingleFlight(private val maxActive: Int = 64) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val active = mutableMapOf<ApiRequestKey, Deferred<Result<String>>>()

    suspend fun await(key: ApiRequestKey, request: suspend () -> String): String {
        val flight = lock.withLock {
            active[key]?.let { return@withLock it }
            if (active.size >= maxActive) return@withLock null
            scope.async(start = CoroutineStart.LAZY) {
                try { runCatching { request() } }
                finally { lock.withLock { active.remove(key) } }
            }.also { active[key] = it }
        }
        // Saturation only disables coalescing; provider request gates still apply.
        if (flight == null) return request()
        flight.start()
        return flight.await().getOrThrow()
    }
}
