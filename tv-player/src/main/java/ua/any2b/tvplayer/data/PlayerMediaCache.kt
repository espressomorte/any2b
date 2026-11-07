package ua.any2b.tvplayer.data

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/** Shared bounded disk cache so small next-episode preloads can be reused by playback. */
@OptIn(UnstableApi::class)
object PlayerMediaCache {
    private const val MAX_CACHE_BYTES = 128L * 1024L * 1024L

    @Volatile private var instance: Cache? = null

    fun get(context: Context): Cache = instance ?: synchronized(this) {
        instance ?: SimpleCache(
            File(context.applicationContext.cacheDir, "media3-stream-cache"),
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
            StandaloneDatabaseProvider(context.applicationContext),
        ).also { instance = it }
    }
}
