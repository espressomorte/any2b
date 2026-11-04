package ua.any2b.tvplayer.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import org.json.JSONArray
import org.json.JSONObject
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.toloka.TolokaTitleAliases

data class TolokaTmdbMatch(
    val topicId: String,
    val releaseTitle: String,
    val synonymsByLanguage: Map<String, List<String>>,
)

/** User-confirmed TMDB ↔ Toloka titles. Stored separately from generated catalog data. */
class TolokaTmdbMappingStore(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.filesDir,
        "toloka_tmdb_mappings.json"))
    private val lock = Any()

    fun matches(type: TmdbMediaType, tmdbId: Int): List<TolokaTmdbMatch> =
        synchronized(lock) { read()[key(type, tmdbId)].orEmpty() }

    fun aliases(type: TmdbMediaType, tmdbId: Int): List<String> =
        matches(type, tmdbId).flatMap { match ->
            listOf("romaji", "ukrainian", "latin", "japanese").flatMap { language ->
                match.synonymsByLanguage[language].orEmpty()
            }
        }.distinctBy(String::lowercase)

    fun approve(type: TmdbMediaType, tmdbId: Int, topicId: String, releaseTitle: String,
        romaji: String? = null) {
        if (tmdbId <= 0 || topicId.isBlank() || releaseTitle.isBlank()) return
        val synonyms = TolokaTitleAliases.extract(releaseTitle).toMutableMap()
        romaji?.trim()?.takeIf(String::isNotBlank)?.let {
            synonyms["romaji"] = (synonyms["romaji"].orEmpty() + it)
                .distinctBy(String::lowercase)
        }
        if (synonyms.isEmpty()) return
        synchronized(lock) {
            val mappings = read().toMutableMap()
            val key = key(type, tmdbId)
            mappings[key] = (mappings[key].orEmpty().filterNot { it.topicId == topicId } +
                TolokaTmdbMatch(topicId, releaseTitle, synonyms)).takeLast(100)
            write(mappings)
        }
    }

    fun reject(type: TmdbMediaType, tmdbId: Int, topicId: String) {
        synchronized(lock) {
            val mappings = read().toMutableMap()
            val key = key(type, tmdbId)
            val remaining = mappings[key].orEmpty().filterNot { it.topicId == topicId }
            if (remaining.size == mappings[key].orEmpty().size) return
            if (remaining.isEmpty()) mappings.remove(key) else mappings[key] = remaining
            write(mappings)
        }
    }

    private fun write(mappings: Map<String, List<TolokaTmdbMatch>>) {
            val root = JSONObject().apply {
                put("schemaVersion", 1)
                put("mappings", JSONObject().apply {
                    mappings.forEach { (identity, matches) ->
                        put(identity, JSONArray().apply {
                            matches.forEach { match -> put(JSONObject().apply {
                                put("topicId", match.topicId)
                                put("releaseTitle", match.releaseTitle)
                                put("synonymsByLanguage", JSONObject().apply {
                                    match.synonymsByLanguage.forEach { (language, names) ->
                                        put(language, JSONArray(names))
                                    }
                                })
                            }) }
                        })
                    }
                })
            }
            val output = file.startWrite()
            try {
                output.write(root.toString().toByteArray(Charsets.UTF_8))
                file.finishWrite(output)
            } catch (error: Exception) {
                file.failWrite(output)
                throw error
            }
    }

    private fun read(): Map<String, List<TolokaTmdbMatch>> {
        val root = try {
            JSONObject(file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
        } catch (_: FileNotFoundException) {
            return emptyMap()
        } catch (_: Exception) {
            return emptyMap()
        }
        if (root.optInt("schemaVersion") != 1) return emptyMap()
        val mappings = root.optJSONObject("mappings") ?: return emptyMap()
        return buildMap {
            val keys = mappings.keys()
            while (keys.hasNext()) {
                val identity = keys.next()
                val entries = mappings.optJSONArray(identity) ?: continue
                put(identity, (0 until entries.length()).mapNotNull { index ->
                    val item = entries.optJSONObject(index) ?: return@mapNotNull null
                    val topicId = item.optString("topicId").takeIf(String::isNotBlank)
                        ?: return@mapNotNull null
                    val releaseTitle = item.optString("releaseTitle").takeIf(String::isNotBlank)
                        ?: return@mapNotNull null
                    val languages = item.optJSONObject("synonymsByLanguage") ?: return@mapNotNull null
                    val synonyms = buildMap {
                        val names = languages.keys()
                        while (names.hasNext()) {
                            val language = names.next()
                            val values = languages.optJSONArray(language) ?: continue
                            put(language, (0 until values.length()).mapNotNull {
                                values.optString(it).takeIf(String::isNotBlank)
                            })
                        }
                    }
                    TolokaTmdbMatch(topicId, releaseTitle, synonyms)
                })
            }
        }
    }

    private fun key(type: TmdbMediaType, tmdbId: Int) = "${type.path}:$tmdbId"
}
