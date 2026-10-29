package ua.any2b.tvplayer.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ua.any2b.core.tmdb.TmdbMediaType

data class TmdbMapping(val type: TmdbMediaType, val id: Int)

/** Confirmed AniList → TMDB links, independent of AniTube release mappings. */
class TmdbMappingStore private constructor(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "tmdb_mappings.json"))
    private val lock = Any()

    suspend fun get(animeId: Int): TmdbMapping? = withContext(Dispatchers.IO) {
        synchronized(lock) { readAll()[animeId] }
    }

    suspend fun put(animeId: Int, mapping: TmdbMapping) = withContext(Dispatchers.IO) {
        require(animeId > 0 && mapping.id > 0)
        synchronized(lock) {
            val values = readAll().toMutableMap().apply { put(animeId, mapping) }
            val document = JSONObject().apply {
                put("schemaVersion", 1)
                put("mappings", JSONObject().apply {
                    values.forEach { (id, value) ->
                        put(id.toString(), JSONObject().apply {
                            put("mediaType", value.type.path)
                            put("tmdbId", value.id)
                        })
                    }
                })
            }
            val stream = file.startWrite()
            try {
                stream.write(document.toString().toByteArray(Charsets.UTF_8))
                file.finishWrite(stream)
            } catch (error: Exception) {
                file.failWrite(stream)
                throw error
            }
        }
    }

    private fun readAll(): Map<Int, TmdbMapping> {
        val document = try {
            JSONObject(file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
        } catch (_: FileNotFoundException) {
            return emptyMap()
        }
        if (document.optInt("schemaVersion") != 1) return emptyMap()
        val entries = document.optJSONObject("mappings") ?: return emptyMap()
        return buildMap {
            val keys = entries.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val animeId = key.toIntOrNull() ?: continue
                val value = entries.optJSONObject(key) ?: continue
                val type = TmdbMediaType.entries.firstOrNull {
                    it.path == value.optString("mediaType")
                } ?: continue
                val tmdbId = value.optInt("tmdbId")
                if (animeId > 0 && tmdbId > 0) put(animeId, TmdbMapping(type, tmdbId))
            }
        }
    }

    companion object {
        @Volatile private var instance: TmdbMappingStore? = null
        fun getInstance(context: Context): TmdbMappingStore =
            instance ?: synchronized(this) {
                instance ?: TmdbMappingStore(context.applicationContext).also { instance = it }
            }
    }
}
