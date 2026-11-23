package ua.any2b.core.toloka

import ua.any2b.core.tmdb.TmdbSeason

/** Episode existence comes from files, never from the release header's advertised range. */
object TolokaEpisodeAvailability {
    /** Explicit per-file audio labels override a multilingual release's general language tags. */
    fun ukrainianFiles(files: List<TorrServerFile>, releaseHasUkrainian: Boolean): List<TorrServerFile> = files.filter { file ->
        val audio = TolokaReleaseMetadataParser.parse(file.name).audioTags
        if (audio.isNotEmpty()) audio.any { it.contains("Ukr") } else releaseHasUkrainian
    }

    fun index(files: List<TorrServerFile>, seasons: List<TmdbSeason>, defaultSeason: Int): Map<Int, Set<Int>> {
        val result = mutableMapOf<Int, MutableSet<Int>>()
        val absoluteFiles = mutableSetOf<Int>()
        files.forEach { file ->
            val metadata = TolokaReleaseMetadataParser.parse(file.name)
            TolokaReleaseMetadataParser.mapAbsoluteEpisodes(metadata, seasons)?.let { ranges ->
                absoluteFiles += file.id
                ranges.forEach { range -> result.getOrPut(range.seasonNumber) { mutableSetOf() }
                    .addAll(range.episodeStart..range.episodeEnd) }
            }
        }
        seasons.filter { it.number > 0 }.forEach { season ->
            val scoped = files.filter { file ->
                file.id !in absoluteFiles && (season.number == defaultSeason ||
                    TolokaReleaseMetadataParser.parse(file.name).season != null ||
                    TolokaReleaseMetadataParser.parse(file.path.substringBeforeLast('/', "")).season != null)
            }
            val episodes = result.getOrPut(season.number) { mutableSetOf() }
            for (episode in 1..(season.episodeCount ?: 0)) {
                if (TolokaEpisodeFileIndex.find(scoped, season.number, episode) != null) episodes += episode
            }
        }
        return result
    }
}
