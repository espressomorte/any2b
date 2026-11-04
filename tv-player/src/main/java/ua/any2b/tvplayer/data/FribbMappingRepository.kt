package ua.any2b.tvplayer.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ua.any2b.core.tmdb.FribbMappings
import ua.any2b.core.tmdb.FribbMappingIndex

/** Downloads the Fribb crosswalk and caches a compact AniList/MAL → TMDB index for seven days. */
object FribbMappingRepository {
    private const val SOURCE =
        "https://raw.githubusercontent.com/Fribb/anime-lists/master/anime-list-mini.json"
    private const val MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000L
    private const val RETRY_AFTER_FAILURE_MS = 60 * 60 * 1000L
    private val mutex = Mutex()
    private val refreshLock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    @Volatile private var memory: FribbMappingIndex? = null
    @Volatile private var loadedAt = 0L
    private var refreshJob: Job? = null

    suspend fun get(context: Context): FribbMappingIndex {
        val appContext = context.applicationContext
        val file = cacheFile(appContext)
        val now = System.currentTimeMillis()
        memory?.takeIf { now - loadedAt < MAX_AGE_MS }?.let {
            Log.d(TAG, "Using in-memory Fribb index MAL=${it.byMal.size} AniList=${it.byAniList.size}")
            return it
        }

        val disk = readDisk(file)
        if (disk != null) {
            val diskLoadedAt = file.lastModified()
            memory = disk
            loadedAt = diskLoadedAt
            if (now - diskLoadedAt < MAX_AGE_MS) {
                Log.d(TAG, "Using disk Fribb index ageMs=${now - diskLoadedAt} " +
                    "MAL=${disk.byMal.size} AniList=${disk.byAniList.size}")
                return disk
            }

            // An expired but usable index is returned immediately; only one background refresh runs.
            scheduleRefresh(appContext, file, disk)
            return disk
        }

        // A cold start without a usable cache must wait for the first successful download.
        return mutex.withLock {
            val lockedNow = System.currentTimeMillis()
            memory?.takeIf { lockedNow - loadedAt < MAX_AGE_MS }?.let { return@withLock it }
            val secondRead = readDisk(file)
            if (secondRead != null && lockedNow - file.lastModified() < MAX_AGE_MS) {
                memory = secondRead
                loadedAt = file.lastModified()
                return@withLock secondRead
            }
            try {
                downloadAndStore(file)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (secondRead != null) {
                    rememberFailedRefresh(secondRead)
                    Log.w(TAG, "Fribb cold refresh failed; using expired disk cache", error)
                    secondRead
                } else {
                    Log.e(TAG, "Fribb download failed and no usable cache exists", error)
                    throw error
                }
            }
        }
    }

    private fun scheduleRefresh(context: Context, file: File, fallback: FribbMappingIndex) {
        synchronized(refreshLock) {
            if (refreshJob?.isActive == true) return
            refreshJob = scope.launch {
                mutex.withLock {
                    val now = System.currentTimeMillis()
                    if (now - loadedAt < MAX_AGE_MS) return@withLock
                    try {
                        downloadAndStore(file)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        rememberFailedRefresh(fallback)
                        Log.w(TAG, "Fribb background refresh failed; retaining stale cache", error)
                    }
                }
            }
        }
    }

    private fun rememberFailedRefresh(fallback: FribbMappingIndex) {
        memory = fallback
        // Treat stale data as retryable in one hour instead of suppressing retries for another week.
        loadedAt = System.currentTimeMillis() - MAX_AGE_MS + RETRY_AFTER_FAILURE_MS
    }

    private suspend fun readDisk(file: File): FribbMappingIndex? {
        if (!file.exists()) return null
        return try {
            val compact = withContext(Dispatchers.IO) {
                AtomicFile(file).openRead().bufferedReader().use { it.readText() }
            }
            FribbMappings.parseCompactIndex(compact)
                .takeIf { it.byAniList.isNotEmpty() && it.byMal.isNotEmpty() }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun downloadAndStore(file: File): FribbMappingIndex {
        Log.d(TAG, "Downloading Fribb index $SOURCE")
        val json = withContext(Dispatchers.IO) {
            http.newCall(Request.Builder().url(SOURCE).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Fribb HTTP ${response.code}")
                response.body?.string() ?: throw IOException("Порожній список Fribb")
            }
        }
        val fresh = withContext(Dispatchers.Default) { FribbMappings.parseIndex(json) }
        if (fresh.byAniList.isEmpty() || fresh.byMal.isEmpty())
            throw IOException("Порожній список відповідників Fribb")
        val compact = withContext(Dispatchers.Default) { FribbMappings.compactIndex(fresh) }
        withContext(Dispatchers.IO) {
            val disk = AtomicFile(file)
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
        Log.i(TAG, "Downloaded Fribb index MAL=${fresh.byMal.size} AniList=${fresh.byAniList.size}")
        return fresh
    }

    private fun cacheFile(context: Context) =
        File(context.filesDir, "fribb-tmdb-index-v2.json")

    private const val TAG = "FribbMapping"
}
