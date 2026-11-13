package ua.any2b.tvplayer

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.text.Cue
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.text.ssa.SsaParser

/** Keeps malformed or zero-duration ASS samples from disabling the entire subtitle track. */
@OptIn(UnstableApi::class)
internal class TolerantSubtitleParserFactory : SubtitleParser.Factory {
    private val defaultFactory = DefaultSubtitleParserFactory()

    override fun supportsFormat(format: Format): Boolean = defaultFactory.supportsFormat(format)

    override fun getCueReplacementBehavior(format: Format): Int =
        defaultFactory.getCueReplacementBehavior(format)

    override fun create(format: Format): SubtitleParser =
        if (format.sampleMimeType == MimeTypes.TEXT_SSA) TolerantSsaParser(format.initializationData)
        else defaultFactory.create(format)
}

@OptIn(UnstableApi::class)
private class TolerantSsaParser(initializationData: List<ByteArray>) : SubtitleParser {
    private val delegate = SsaParser(initializationData)
    private var fallbackLogged = false

    override fun getCueReplacementBehavior(): Int = delegate.cueReplacementBehavior

    override fun reset() = delegate.reset()

    override fun parse(
        data: ByteArray,
        offset: Int,
        length: Int,
        outputOptions: SubtitleParser.OutputOptions,
        output: Consumer<CuesWithTiming>,
    ) {
        val original = String(data, offset, length, Charsets.UTF_8)
        val repaired = repairZeroDuration(AssDrawingFilter.removeDrawings(original))
        val parsed = mutableListOf<CuesWithTiming>()
        try {
            val bytes = repaired.toByteArray(Charsets.UTF_8)
            delegate.parse(bytes, 0, bytes.size, outputOptions, Consumer { parsed.add(it) })
            parsed.forEach(output::accept)
        } catch (error: IllegalStateException) {
            if (!fallbackLogged) {
                Log.w("PlayerSubtitles", "SSA sample could not be decoded; using plain text", error)
                fallbackLogged = true
            }
            val line = repaired.lineSequence().firstOrNull { it.startsWith("Dialogue:") } ?: return
            val text = line.substringAfter("Dialogue:").split(',', limit = 11).lastOrNull()
                ?.replace(Regex("\\{[^}]*\\}"), "")
                ?.replace("\\N", "\n")?.replace("\\n", "\n")?.trim()
                ?.takeIf(String::isNotBlank) ?: return
            output.accept(CuesWithTiming(listOf(Cue.Builder().setText(text).build()), 0L, 2_000_000L))
        }
    }

    private fun repairZeroDuration(sample: String): String = sample.lineSequence().joinToString("\n") { line ->
        val match = DIALOGUE_TIMES.matchEntire(line) ?: return@joinToString line
        val start = assTimeMs(match.groupValues[2]) ?: return@joinToString line
        val end = assTimeMs(match.groupValues[3]) ?: return@joinToString line
        if (end > start) line else {
            val newEnd = formatAssTime(start + 2_000)
            "${match.groupValues[1]}${match.groupValues[2]},$newEnd,${match.groupValues[4]}"
        }
    }

    private fun assTimeMs(value: String): Long? {
        val parts = value.split(':', '.')
        if (parts.size != 4) return null
        val numbers = parts.map { it.toLongOrNull() ?: return null }
        return ((numbers[0] * 60 + numbers[1]) * 60 + numbers[2]) * 1_000 + numbers[3] * 10
    }

    private fun formatAssTime(valueMs: Long): String {
        val centiseconds = valueMs / 10
        return "%d:%02d:%02d:%02d".format(
            centiseconds / 360_000,
            centiseconds / 6_000 % 60,
            centiseconds / 100 % 60,
            centiseconds % 100,
        )
    }

    companion object {
        private val DIALOGUE_TIMES = Regex(
            "^(Dialogue:\\s*)(\\d+:\\d{2}:\\d{2}[:.]\\d{2}),(\\d+:\\d{2}:\\d{2}[:.]\\d{2}),(.*)$"
        )
    }
}

/** Media3 treats ASS vector drawing commands as ordinary subtitle text. */
internal object AssDrawingFilter {
    private val blocks = Regex("\\{[^}]*\\}")
    private val drawingMode = Regex("\\\\p(\\d+)")

    fun removeDrawings(sample: String): String = sample.lineSequence().mapNotNull { line ->
        if (!line.startsWith("Dialogue:")) return@mapNotNull line
        val fields = line.split(',', limit = 11)
        if (fields.size != 11) return@mapNotNull line
        val raw = fields[10]
        var drawing = false
        var cursor = 0
        val cleaned = StringBuilder()
        blocks.findAll(raw).forEach { block ->
            if (!drawing) cleaned.append(raw, cursor, block.range.first)
            drawingMode.findAll(block.value).forEach { mode ->
                drawing = mode.groupValues[1].toIntOrNull()?.let { it > 0 } ?: drawing
            }
            val remainingTags = block.value.replace(drawingMode, "")
            if (remainingTags != "{}") cleaned.append(remainingTags)
            cursor = block.range.last + 1
        }
        if (!drawing) cleaned.append(raw, cursor, raw.length)
        val visibleText = cleaned.toString().replace(blocks, "").trim()
        if (visibleText.isEmpty()) null else fields.take(10).joinToString(",") + "," + cleaned
    }.joinToString("\n")
}
