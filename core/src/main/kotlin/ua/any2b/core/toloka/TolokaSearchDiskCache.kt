package ua.any2b.core.toloka

import java.io.File
import java.security.MessageDigest
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Public search metadata only: never persists sessions or personal torrent download URLs. */
class TolokaSearchDiskCache(
    private val directory: File,
    private val maxBytes: Long = 32L * 1024 * 1024,
    private val ttlMs: Long = 10L * 24 * 60 * 60 * 1000,
    private val now: () -> Long = System::currentTimeMillis,
) {
    init { require(maxBytes >= 1024 && ttlMs > 0) }

    @Synchronized fun get(account: String, query: String): TolokaSearch? = runCatching {
        val file = file(account, query)
        if (!file.isFile) return null
        if (file.length() > maxEntryBytes()) { file.delete(); return null }
        val row = JSONObject(file.readText())
        val age = now() - row.getLong("savedAt")
        if (row.getInt("schema") != 1 || age < 0 || age >= ttlMs) { file.delete(); return null }
        val items = row.getJSONArray("results")
        TolokaSearch(query.trim(), (0 until items.length()).map { index ->
            val item = items.getJSONObject(index)
            TolokaHit(item.getString("id"), item.getString("title"), item.getString("pageUrl"),
                item.getString("sizeText"), item.optionalInt("seeders"), item.optionalInt("leechers"),
                item.optString("author").takeIf(String::isNotBlank))
        }, cached = true, sessionReused = null)
    }.getOrElse { file(account, query).delete(); null }

    @Synchronized fun put(account: String, result: TolokaSearch) {
        runCatching {
            if (!directory.isDirectory && !directory.mkdirs()) return
            val savedAt = now()
            val row = JSONObject().put("schema", 1).put("savedAt", savedAt).put("results", JSONArray().apply {
                result.results.forEach { hit -> put(JSONObject().put("id", hit.id).put("title", hit.title)
                    .put("pageUrl", hit.pageUrl).put("sizeText", hit.sizeText).put("seeders", hit.seeders)
                    .put("leechers", hit.leechers).put("author", hit.author)) }
            })
            val bytes = row.toString().toByteArray(Charsets.UTF_8)
            if (bytes.size > maxEntryBytes()) return
            val target = file(account, result.query)
            val temp = File(directory, target.name + ".tmp")
            temp.outputStream().use { it.write(bytes) }
            if (!temp.renameTo(target)) { temp.delete(); return }
            target.setLastModified(savedAt)
            prune()
        }
    }

    @Synchronized fun prune() {
        runCatching {
            val time = now()
            val entries = directory.listFiles().orEmpty().filter { file ->
                if (file.name.endsWith(".tmp")) { file.delete(); false }
                else if (!file.name.endsWith(".json") || !file.isFile) false
                else {
                    val age = time - file.lastModified()
                    if (age < 0 || age >= ttlMs || file.length() > maxEntryBytes()) { file.delete(); false }
                    else true
                }
            }.sortedWith(compareBy<File> { it.lastModified() }.thenBy { it.name })
            var size = entries.sumOf { it.length() }
            var count = entries.size
            if (size < maxBytes * 9 / 10 && count <= 2048) return
            for (file in entries) {
                if (size <= maxBytes * 3 / 4 && count <= 1536) break
                val length = file.length()
                if (file.delete()) { size -= length; count-- }
            }
        }
    }

    private fun maxEntryBytes() = minOf(512L * 1024, maxBytes / 4)
    private fun file(account: String, query: String): File {
        val normalized = query.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        val key = "$account\u0000$normalized"
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$hash.json")
    }
    private fun JSONObject.optionalInt(key: String): Int? = if (isNull(key)) null else getInt(key)
}
