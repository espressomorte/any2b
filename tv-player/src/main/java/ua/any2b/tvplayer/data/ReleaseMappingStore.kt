package ua.any2b.tvplayer.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ua.any2b.core.anitube.AnitubeSearchResult
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

enum class ReleaseSelection(val storedValue: String) {
    MANUAL("manual"),
    AUTOMATIC("automatic"),
}

data class ReleaseMapping(
    val anitubeId: Int,
    val pageUrl: String,
    val title: String,
    val selection: ReleaseSelection,
) {
    fun asSearchResult() = AnitubeSearchResult(
        newsId = anitubeId,
        title = title,
        pageUrl = pageUrl,
    )
}

/** A single process-wide writer for confirmed AniList to AniTube links. */
class ReleaseMappingStore private constructor(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "release_mappings.json"))
    private val writeLock = Any()

    suspend fun get(animeId: Int): ReleaseMapping? = withContext(Dispatchers.IO) {
        synchronized(writeLock) { readAll()[animeId] }
    }

    suspend fun put(animeId: Int, release: AnitubeSearchResult, selection: ReleaseSelection) {
        withContext(Dispatchers.IO) {
            synchronized(writeLock) {
                val mappings = readAll().toMutableMap()
                mappings[animeId] = ReleaseMapping(release.newsId, release.pageUrl, release.title, selection)
                val document = JSONObject().apply {
                    put("schemaVersion", SCHEMA_VERSION)
                    put("mappings", JSONObject().apply {
                        mappings.forEach { (id, mapping) ->
                            put(id.toString(), JSONObject().apply {
                                put("anitubeId", mapping.anitubeId)
                                put("pageUrl", mapping.pageUrl)
                                put("title", mapping.title)
                                put("selection", mapping.selection.storedValue)
                            })
                        }
                    })
                }
                val stream = file.startWrite()
                try {
                    stream.write(document.toString().toByteArray(Charsets.UTF_8))
                    file.finishWrite(stream)
                } catch (e: Exception) {
                    file.failWrite(stream)
                    throw e
                }
            }
        }
    }

    private fun readAll(): Map<Int, ReleaseMapping> {
        val document = try {
            JSONObject(file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
        } catch (_: FileNotFoundException) {
            return emptyMap()
        }
        if (document.optInt("schemaVersion") != SCHEMA_VERSION) {
            throw IOException("Непідтримувана версія файлу зв'язків релізів")
        }
        val entries = document.optJSONObject("mappings")
            ?: throw IOException("Некоректний файл зв'язків релізів")
        val result = mutableMapOf<Int, ReleaseMapping>()
        val keys = entries.keys()
        while (keys.hasNext()) {
            val animeId = keys.next().toIntOrNull() ?: continue
            val entry = entries.optJSONObject(animeId.toString()) ?: continue
            val selection = ReleaseSelection.entries.firstOrNull {
                it.storedValue == entry.optString("selection")
            } ?: continue
            val anitubeId = entry.optInt("anitubeId")
            val pageUrl = entry.optString("pageUrl")
            if (anitubeId <= 0 || pageUrl.isBlank()) continue
            result[animeId] = ReleaseMapping(
                anitubeId = anitubeId,
                pageUrl = pageUrl,
                title = entry.optString("title"),
                selection = selection,
            )
        }
        return result
    }

    companion object {
        private const val SCHEMA_VERSION = 1
        @Volatile private var instance: ReleaseMappingStore? = null

        fun getInstance(context: Context): ReleaseMappingStore =
            instance ?: synchronized(this) {
                instance ?: ReleaseMappingStore(context.applicationContext).also { instance = it }
            }
    }
}
