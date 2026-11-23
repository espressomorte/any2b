package ua.any2b.tvplayer.data

import android.content.Context
import java.security.MessageDigest
import org.json.JSONObject

/** Bounded query hints avoid repeating AniList resolution before reading a cached Toloka search. */
internal class TolokaAutomaticQueryStore(context: Context) {
    private val prefs = context.getSharedPreferences("toloka_query_hints", Context.MODE_PRIVATE)
    data class Hint(val query: String, val romaji: String?)

    fun get(identity: String): Hint? = synchronized(lock) {
        val row = read().optJSONObject(key(identity)) ?: return@synchronized null
        val age = System.currentTimeMillis() - row.optLong("savedAt")
        if (age !in 0 until TTL) return@synchronized null
        row.optString("query").takeIf(String::isNotBlank)?.let {
            Hint(it, row.optString("romaji").takeIf(String::isNotBlank))
        }
    }

    fun put(identity: String, query: String, romaji: String?) = synchronized(lock) {
        runCatching {
            val now = System.currentTimeMillis()
            val rows = read()
            rows.put(key(identity), JSONObject().put("query", query).put("romaji", romaji).put("savedAt", now))
            val retained = rows.keys().asSequence().mapNotNull { key -> rows.optJSONObject(key)?.let { key to it } }
                .filter { now - it.second.optLong("savedAt") in 0 until TTL }
                .sortedByDescending { it.second.optLong("savedAt") }.take(256).toList()
            prefs.edit().putString("hints", JSONObject().apply { retained.forEach { (key, row) -> put(key, row) } }.toString()).apply()
            Unit
        }.getOrDefault(Unit)
    }
    private fun read() = runCatching { JSONObject(prefs.getString("hints", "{}").orEmpty()) }.getOrElse { JSONObject() }
    private fun key(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    companion object {
        private val lock = Any()
        private const val TTL = 10L * 24 * 60 * 60 * 1000
    }
}
