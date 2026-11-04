package ua.any2b.tvplayer.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import okhttp3.OkHttpClient
import okhttp3.Request
import ua.any2b.core.tmdb.AniBridgeMappingIndex
import ua.any2b.core.tmdb.AniBridgeMappings

/** Shared AniBridge v3 index, cached on disk so callers can fall back while downloading. */
object AniBridgeMappingRepository {
    private const val SOURCE =
        "https://github.com/anibridge/anibridge-mappings/releases/latest/download/mappings.min.json"
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder().callTimeout(45, TimeUnit.SECONDS).build()
    private val lock = Any()
    @Volatile private var cached: AniBridgeMappingIndex? = null
    @Volatile private var cachedAt = 0L
    private var loading: Deferred<AniBridgeMappingIndex>? = null

    suspend fun get(context: Context): AniBridgeMappingIndex {
        val now = System.currentTimeMillis()
        cached?.takeIf { now - cachedAt < MAX_AGE_MS }?.let { return it }
        val file = File(context.applicationContext.filesDir, "anibridge-mappings-v3.json")
        val pending = synchronized(lock) {
            loading?.takeIf { it.isActive } ?: scope.async { load(file) }.also { loading = it }
        }
        return pending.await()
    }

    private fun load(file: File): AniBridgeMappingIndex {
        val disk = runCatching {
            AniBridgeMappings.parse(AtomicFile(file).openRead()
                .bufferedReader(Charsets.UTF_8).use { it.readText() })
        }.getOrNull()
        if (disk != null && System.currentTimeMillis() - file.lastModified() < MAX_AGE_MS) {
            cached = disk
            cachedAt = file.lastModified()
            return disk
        }
        return try {
            val json = http.newCall(Request.Builder().url(SOURCE)
                .header("User-Agent", "Any2B-Android-TV").build()).execute().use { response ->
                if (!response.isSuccessful) error("AniBridge HTTP ${response.code}")
                response.body?.string() ?: error("Порожній каталог AniBridge")
            }
            val parsed = AniBridgeMappings.parse(json)
            require(parsed.byAniList.isNotEmpty() && parsed.byTmdb.isNotEmpty())
            val target = AtomicFile(file)
            val output = target.startWrite()
            try {
                output.write(json.toByteArray(Charsets.UTF_8))
                target.finishWrite(output)
            } catch (error: Exception) {
                target.failWrite(output)
                throw error
            }
            cached = parsed
            cachedAt = System.currentTimeMillis()
            parsed
        } catch (error: Exception) {
            if (disk != null) {
                cached = disk
                cachedAt = System.currentTimeMillis() - MAX_AGE_MS + 60 * 60 * 1000L
                disk
            } else throw error
        }
    }
}
