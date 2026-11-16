package ua.any2b.cli

import java.nio.file.Files
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualMappingConfirmationTest {
    @Test
    fun confirmedSnapshotsAcceptsLegacyAndListFormsWithoutDuplicates() {
        val candidate = JSONObject().put("index", 1).put("anilistId", 202).put("tmdbId", 602)
        val review = JSONObject()
            .put("confirmedCandidates", JSONArray().put(candidate))
            .put("confirmedCandidate", JSONObject(candidate.toString()))

        assertEquals(listOf(candidate.toString()), confirmedSnapshots(review).map { it.toString() })
        assertTrue(confirmedSnapshots(null).isEmpty())
    }

    @Test
    fun reanalysisKeepsManualChoiceFirstAndRemapsRejectedCandidateByIdentity() {
        val manualCandidate = candidate(202, 602, "Manual season two")
        val rejectedCandidate = candidate(101, 501, "Rejected result")
        val original = JSONObject()
            .put("newsId", 55)
            .put("status", "needs_review")
            .put("candidates", JSONArray().put(rejectedCandidate).put(manualCandidate))
            .put("userReview", JSONObject()
                .put("comment", "This is the second season")
                .put("confirmedCandidates", JSONArray().put(JSONObject()
                    .put("index", 1).put("anilistId", 202).put("tmdbId", 602)))
                .put("rejectedCandidateIndexes", JSONArray().put(0)))
        val untouched = JSONObject().put("newsId", 77).put("status", "not_found")
        val generated = JSONObject().put("items", JSONArray().put(JSONObject()
            .put("newsId", 55)
            .put("status", "needs_review")
            .put("candidates", JSONArray()
                .put(candidate(303, 703, "New suggestion"))
                .put(candidate(101, 501, "Rejected result"))
                .put(candidate(202, 602, "Manual season two")))))
        val directory = Files.createTempDirectory("mapping-review-test")
        val output = directory.resolve("reviewed.json")

        try {
            writeReanalysisOutput(output, JSONObject().put("\$meta", JSONObject()),
                listOf(original, untouched), mapOf(55 to original), setOf(55), generated)

            val rows = JSONObject(Files.readString(output)).getJSONArray("items")
            val merged = rows.getJSONObject(0)
            val candidates = merged.getJSONArray("candidates")
            assertEquals("matched", merged.getString("status"))
            assertTrue(merged.getBoolean("manualConfirmation"))
            assertEquals(202, candidates.getJSONObject(0).getInt("anilistId"))
            assertTrue(candidates.getJSONObject(0).getBoolean("manualConfirmation"))
            assertEquals(101, candidates.getJSONObject(2).getInt("anilistId"))
            assertEquals(2, merged.getJSONObject("userReview")
                .getJSONArray("rejectedCandidateIndexes").getInt(0))
            assertEquals("This is the second season",
                merged.getJSONObject("userReview").getString("comment"))
            assertEquals(untouched.toString(), rows.getJSONObject(1).toString())
            assertTrue(merged.getJSONArray("manualConfirmationCandidates").length() > 0)
        } finally {
            Files.deleteIfExists(output)
            Files.deleteIfExists(directory)
        }
    }

    private fun candidate(anilistId: Int, tmdbId: Int, title: String) = JSONObject()
        .put("anilistId", anilistId)
        .put("tmdbId", tmdbId)
        .put("tmdbType", "tv")
        .put("tmdbSeason", 2)
        .put("anilistTitle", title)
}
