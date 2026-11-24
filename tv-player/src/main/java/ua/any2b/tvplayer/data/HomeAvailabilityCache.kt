package ua.any2b.tvplayer.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import ua.any2b.core.tmdb.TmdbMediaType
import java.io.File

data class HomeAvailability(
    /** Bit mask: 1 = AniTube, 2 = Toloka. */
    val sources: Int,
    val checkedAt: Long,
    val aniTubeIds: List<Int> = emptyList(),
    val tolokaIds: List<String> = emptyList(),
)

/** Compact provider availability index, independent from cached TMDB home rows. */
class HomeAvailabilityCache(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.filesDir,
        "home-availability.json"))
    private val lock = Any()
    private var loaded = false
    private val entries = linkedMapOf<String, HomeAvailability>()

    fun get(type: TmdbMediaType, tmdbId: Int): HomeAvailability? = synchronized(lock) {
        ensureLoaded()
        entries[key(type, tmdbId)]
    }

    fun needsCheck(type: TmdbMediaType, tmdbId: Int,
        now: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        ensureLoaded()
        val checkedAt = entries[key(type, tmdbId)]?.checkedAt ?: return@synchronized true
        checkedAt <= 0L || now < checkedAt || now - checkedAt >= CHECK_INTERVAL_MS
    }

    /** Merge one completed batch and atomically replace the JSON snapshot. */
    fun saveBatch(batch: Map<String, HomeAvailability>) = synchronized(lock) {
        if (batch.isEmpty()) return@synchronized
        ensureLoaded()
        entries.putAll(batch.mapValues { (_, value) ->
            value.copy(sources = value.sources and SOURCE_MASK,
                aniTubeIds = value.aniTubeIds.take(MAX_SOURCE_IDS),
                tolokaIds = value.tolokaIds.take(MAX_SOURCE_IDS))
        })
        val retained = entries.entries.sortedByDescending { it.value.checkedAt }
            .take(MAX_ENTRIES).associate { it.toPair() }
        entries.clear()
        entries.putAll(retained)
        val root = JSONObject().put("v", SCHEMA_VERSION).put("items", JSONObject().apply {
            entries.forEach { (identity, value) ->
                put(identity, JSONObject().put("s", value.sources and SOURCE_MASK)
                    .put("at", value.checkedAt).apply {
                        if (value.aniTubeIds.isNotEmpty())
                            put("a", JSONArray(value.aniTubeIds.take(MAX_SOURCE_IDS)))
                        if (value.tolokaIds.isNotEmpty())
                            put("t", JSONArray(value.tolokaIds.take(MAX_SOURCE_IDS)))
                    })
            }
        })
        var stream: java.io.FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(root.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (_: Exception) {
            stream?.let(file::failWrite)
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            val root = JSONObject(file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
            if (root.optInt("v") != SCHEMA_VERSION) return@runCatching
            val items = root.optJSONObject("items") ?: return@runCatching
            val identities = items.keys()
            while (identities.hasNext()) {
                val identity = identities.next()
                val value = items.optJSONObject(identity) ?: continue
                val checkedAt = value.optLong("at")
                if (checkedAt <= 0L) continue
                entries[identity] = HomeAvailability(
                    sources = value.optInt("s") and SOURCE_MASK,
                    checkedAt = checkedAt,
                    aniTubeIds = value.optJSONArray("a").asList().mapNotNull { it.toIntOrNull() },
                    tolokaIds = value.optJSONArray("t").asList().filter(String::isNotBlank),
                )
            }
        }
    }

    private fun key(type: TmdbMediaType, id: Int) = "${type.path}:$id"

    private fun JSONArray?.asList(): List<String> = this?.let { array ->
        (0 until array.length()).map { array.optString(it) }
    }.orEmpty()

    companion object {
        const val SCHEMA_VERSION = 1
        const val SOURCE_ANITUBE = 1
        const val SOURCE_TOLOKA = 2
        private const val SOURCE_MASK = SOURCE_ANITUBE or SOURCE_TOLOKA
        private const val MAX_ENTRIES = 512
        private const val MAX_SOURCE_IDS = 3
        private const val CHECK_INTERVAL_MS = 48L * 60L * 60L * 1_000L
    }
}
