package ua.any2b.core.toloka

import java.security.MessageDigest
import ua.any2b.release.KotlinReleaseParser
import ua.any2b.release.ReleaseInput
import ua.any2b.release.ReleaseSource

/** Shared RSS query inputs; derived cache identities never merge different tracker releases. */
object TolokaReleaseQueries {
    fun names(title: String): List<String> = TolokaReleaseMetadataParser.parse(title).titleCandidates
    fun year(title: String): Int? = TolokaReleaseMetadataParser.parse(title).yearRanges.firstOrNull()?.first
    fun season(title: String): Int? = TolokaReleaseMetadataParser.parse(title).season

    fun cacheKey(id: String, title: String, animated: Boolean): String {
        val parsed = KotlinReleaseParser.parse(ReleaseInput(title, ReleaseSource.TOLOKA))
        val digest = MessageDigest.getInstance("SHA-256").digest(title.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "toloka:${parsed.parserVersion}:${parsed.rulesVersion}:$id:$animated:$digest"
    }
}
