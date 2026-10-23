package ua.any2b.core.extractor

import ua.any2b.core.fenix.FenixClient

/**
 * Екстрактор для fenixplay.xyz
 */
class FenixExtractor(
    private val client: FenixClient = FenixClient(),
) : VideoExtractor {

    override val name: String = "FenixPlay"

    override fun canHandle(url: String): Boolean =
        url.contains("fenixplay.xyz", ignoreCase = true)

    override suspend fun extract(embedUrl: String): ExtractedStream {
        val stream = client.resolve(embedUrl)
        return ExtractedStream(
            streamUrl = stream.finalMasterUrl,
            provider = name,
            headers = mapOf(
                "Referer" to "https://fenixplay.xyz/",
                "Origin" to "https://fenixplay.xyz",
            )
        )
    }
}
