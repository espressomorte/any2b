package ua.any2b.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

data class AppRelease(
    val tag: String,
    val pageUrl: String,
    val apkUrl: String?,
)

/** GitHub's /releases/latest excludes prereleases, so read the published release list. */
class GitHubReleaseClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun newestRelease(): AppRelease? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/espressomorte/any2b/releases?per_page=30")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Any2B-Android-TV")
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("GitHub відповів HTTP ${response.code}")
            val releases = JSONArray(response.body?.string().orEmpty())
            (0 until releases.length()).mapNotNull { index ->
                val release = releases.optJSONObject(index) ?: return@mapNotNull null
                if (release.optBoolean("draft") || release.isNull("published_at")) return@mapNotNull null
                val tag = release.optString("tag_name")
                val pageUrl = release.optString("html_url")
                if (tag.isBlank() || pageUrl.isBlank()) return@mapNotNull null
                val assets = release.optJSONArray("assets")
                val apkUrl = assets?.let { list ->
                    (0 until list.length()).mapNotNull { list.optJSONObject(it) }
                        .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                        ?.optString("browser_download_url")
                        ?.takeIf { it.startsWith("https://github.com/espressomorte/any2b/releases/download/") }
                }
                AppRelease(tag, pageUrl, apkUrl)
            }.maxWithOrNull { a, b -> compareVersions(a.tag, b.tag) }
        }
    }
}

/** SemVer comparison including prerelease identifiers such as alpha.1. */
fun compareVersions(first: String, second: String): Int {
    fun parse(value: String): Pair<List<Int>, List<String>?>? {
        val match = Regex("^v?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$")
            .matchEntire(value) ?: return null
        val numbers = (1..3).map { match.groupValues[it].ifBlank { "0" }.toIntOrNull() ?: return null }
        val prerelease = match.groupValues[4].takeIf(String::isNotBlank)?.split('.')
        return numbers to prerelease
    }

    val a = parse(first)
    val b = parse(second)
    if (a == null || b == null) return when {
        a == null && b == null -> first.compareTo(second)
        a == null -> -1
        else -> 1
    }
    for (index in 0..2) {
        val difference = a.first[index].compareTo(b.first[index])
        if (difference != 0) return difference
    }
    val aPre = a.second ?: return if (b.second == null) 0 else 1
    val bPre = b.second ?: return -1
    for (index in 0 until minOf(aPre.size, bPre.size)) {
        val left = aPre[index]
        val right = bPre[index]
        val leftNumber = left.toIntOrNull()
        val rightNumber = right.toIntOrNull()
        val difference = when {
            leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
            leftNumber != null -> -1
            rightNumber != null -> 1
            else -> left.compareTo(right)
        }
        if (difference != 0) return difference
    }
    return aPre.size.compareTo(bPre.size)
}
