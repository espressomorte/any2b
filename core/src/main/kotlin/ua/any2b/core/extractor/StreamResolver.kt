package ua.any2b.core.extractor

/**
 * Універсальний фасад для отримання HLS-потоку з будь-якого підтримуваного плеєра (Fenix, Ashdi тощо).
 */
class StreamResolver(
    private val extractors: List<VideoExtractor> = listOf(
        AshdiExtractor(),
        FenixExtractor(),
    ),
) {

    /**
     * Перевіряє, чи вміє система витягувати відео з даного URL.
     */
    fun canHandle(embedUrl: String): Boolean =
        extractors.any { it.canHandle(embedUrl) }

    /**
     * Автоматично знаходить потрібний екстрактор та повертає готовий HLS-потік.
     */
    suspend fun resolve(embedUrl: String): ExtractedStream {
        // Якщо передано вже прямий .m3u8, повертаємо як є
        if (embedUrl.endsWith(".m3u8") || embedUrl.contains(".m3u8?")) {
            return ExtractedStream(
                streamUrl = embedUrl,
                provider = "Direct"
            )
        }

        val extractor = extractors.firstOrNull { it.canHandle(embedUrl) }
            ?: throw IllegalArgumentException("Немає екстрактора для плеєра: $embedUrl")

        return extractor.extract(embedUrl)
    }
}
