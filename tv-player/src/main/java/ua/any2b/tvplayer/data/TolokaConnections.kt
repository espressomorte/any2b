package ua.any2b.tvplayer.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ua.any2b.core.toloka.TolokaSearchDiskCache
import ua.any2b.core.toloka.DirectTolokaClient
import ua.any2b.core.toloka.TolokaFailure
import ua.any2b.core.toloka.TolokaSearch

/** One process-wide direct client owns Toloka cookies, login session, rate limit and caches. */
object TolokaConnections {
    private val direct = DirectTolokaClient()

    fun configured(context: Context): Boolean = TolokaCredentialStore(context).read() != null

    private val searchLock = Mutex()
    @Volatile private var disk: TolokaSearchDiskCache? = null
    private fun cache(context: Context): TolokaSearchDiskCache = disk ?: synchronized(this) {
        disk ?: TolokaSearchDiskCache(File(context.applicationContext.cacheDir, "toloka_search_v1"))
            .also { it.prune(); disk = it }
    }

    suspend fun cachedSearch(context: Context, query: String): TolokaSearch? = withContext(Dispatchers.IO) {
        val credentials = TolokaCredentialStore(context).read() ?: return@withContext null
        cache(context).get(credentials.username, query)
    }

    suspend fun search(context: Context, query: String, forceRefresh: Boolean = false): TolokaSearch =
        withContext(Dispatchers.IO) {
            require(query.trim().length in 1..150) { "Запит має містити 1–150 символів" }
            val credentials = TolokaCredentialStore(context).read()
                ?: throw IllegalStateException("Завантажте конфіг із логіном і паролем Toloka")
            // Disk hits must not wait behind a background network refresh.
            if (!forceRefresh) cache(context).get(credentials.username, query)?.let { return@withContext it }
            searchLock.withLock {
                if (!forceRefresh) cache(context).get(credentials.username, query)?.let { return@withLock it }
                val result = direct.search(credentials.username, credentials.password, query, forceRefresh)
                cache(context).put(credentials.username, result)
                result
            }
        }

    /** Ongoing-topic checks bypass the 10-day snapshot but share the client's 5-minute memory cache. */
    suspend fun recentSearch(context: Context, query: String): TolokaSearch = withContext(Dispatchers.IO) {
        val credentials = TolokaCredentialStore(context).read()
            ?: throw IllegalStateException("Завантажте конфіг із логіном і паролем Toloka")
        val result = direct.search(credentials.username, credentials.password, query)
        if (!result.cached) cache(context).put(credentials.username, result)
        result
    }

    suspend fun torrent(context: Context, topicId: String): ByteArray {
        val credentials = TolokaCredentialStore(context).read()
            ?: throw IllegalStateException("Завантажте конфіг із логіном і паролем Toloka")
        return direct.torrent(credentials.username, credentials.password, topicId)
    }

    suspend fun checkDirect(context: Context): Boolean {
        val credentials = TolokaCredentialStore(context).read()
            ?: throw IllegalStateException("У конфігу немає даних Toloka")
        return direct.check(credentials.username, credentials.password)
    }

    suspend fun rss(context: Context): String {
        val credentials = TolokaCredentialStore(context).read()
            ?: throw IllegalStateException("Завантажте конфіг із логіном і паролем Toloka")
        return direct.rss(credentials.username, credentials.password)
    }
}
