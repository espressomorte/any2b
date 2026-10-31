package ua.any2b.tvplayer.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ua.any2b.core.tmdb.FribbMappings
import ua.any2b.core.tmdb.FribbMappingIndex

/** Downloads the Fribb crosswalk and caches a small AniList → TMDB index for seven days. */
object FribbMappingRepository {
    private const val SOURCE =
        "https://raw.githubusercontent.com/Fribb/anime-lists/master/anime-list-mini.json"
    private const val MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000L
    private val mutex = Mutex()
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private var memory: FribbMappingIndex? = null
    private var loadedAt = 0L

    suspend fun get(context: Context): FribbMappingIndex = mutex.withLock {
        val file = File(context.applicationContext.filesDir, "fribb-tmdb-index-v2.json")
        val now = System.currentTimeMillis()
        memory?.takeIf { now - loadedAt < MAX_AGE_MS }?.let { return@withLock it }
        val disk = AtomicFile(file)
        val cached = if (file.exists()) try {
            FribbMappings.parseCompactIndex(withContext(Dispatchers.IO) {
                disk.openRead().bufferedReader().use { it.readText() }
            }).takeIf { it.byAniList.isNotEmpty() && it.byMal.isNotEmpty() }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) { null } else null
        if (cached != null && now - file.lastModified() < MAX_AGE_MS) {
            memory = cached
            loadedAt = file.lastModified()
            return@withLock cached
        }
        try {
            val json = withContext(Dispatchers.IO) {
                http.newCall(Request.Builder().url(SOURCE).build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Fribb HTTP ${response.code}")
                    response.body?.string() ?: throw IOException("Порожній список Fribb")
                }
            }
            val fresh = withContext(Dispatchers.IO) { FribbMappings.parseIndex(json) }
            if (fresh.byAniList.isEmpty() || fresh.byMal.isEmpty())
                throw IOException("Порожній список відповідників Fribb")
            val compact = withContext(Dispatchers.IO) { FribbMappings.compactIndex(fresh) }
            withContext(Dispatchers.IO) {
                val output = disk.startWrite()
                try {
                    output.write(compact.toByteArray(Charsets.UTF_8))
                    disk.finishWrite(output)
                } catch (error: Exception) {
                    disk.failWrite(output)
                    throw error
                }
            }
            memory = fresh
            loadedAt = System.currentTimeMillis()
            fresh
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (cached != null) {
                memory = cached
                loadedAt = now
                cached
            } else throw error
        }
    }
}
