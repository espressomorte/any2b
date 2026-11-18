package ua.any2b.tvplayer.data

import android.content.Context
import ua.any2b.core.toloka.DirectTolokaClient
import ua.any2b.core.toloka.TolokaFailure
import ua.any2b.core.toloka.TolokaSearch

/** One process-wide direct client owns Toloka cookies, login session, rate limit and caches. */
object TolokaConnections {
    private val direct = DirectTolokaClient()

    fun configured(context: Context): Boolean = TolokaCredentialStore(context).read() != null

    suspend fun search(context: Context, query: String): TolokaSearch {
        val credentials = TolokaCredentialStore(context).read()
            ?: throw IllegalStateException("Завантажте конфіг із логіном і паролем Toloka")
        return direct.search(credentials.username, credentials.password, query)
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
