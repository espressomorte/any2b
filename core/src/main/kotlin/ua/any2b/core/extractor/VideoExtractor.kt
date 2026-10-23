package ua.any2b.core.extractor

/** Результат роботи екстрактора потоку. */
data class ExtractedStream(
    val streamUrl: String,                // прямий URL на master.m3u8 або index.m3u8
    val provider: String,                 // "FenixPlay", "Ashdi" тощо
    val headers: Map<String, String> = emptyMap(), // потрібні HTTP-заголовки для ExoPlayer (якщо є)
)

/** Інтерфейс для розбору конкретного відеохостингу. */
interface VideoExtractor {
    val name: String
    fun canHandle(url: String): Boolean
    suspend fun extract(embedUrl: String): ExtractedStream
}
