package ua.any2b.core.toloka

/** Resolves an episode from the video files of one selected torrent release. */
object TolokaEpisodeFileIndex {
    private val leadingNumber = Regex("""^\s*[\[(]?0*(\d{1,3})(?=\D|$)""")
    private val trailingNumber = Regex("""(?:^|[\s._-])0*(\d{1,3})(?=\.[^.]+$)""")
    private val seasonByEpisode = Regex("""(?i)(?<!\d)(\d{1,2})x(\d{1,3})(?!\d)""")

    fun find(files: List<TorrServerFile>, season: Int, episode: Int): TorrServerFile? {
        if (season <= 0 || episode <= 0) return null
        val entries = files.map { file ->
            val metadata = TolokaReleaseMetadataParser.parse(file.name)
            val folder = file.path.substringBeforeLast('/', "")
            val folderSeason = folder.takeIf(String::isNotBlank)
                ?.let(TolokaReleaseMetadataParser::parse)?.season
            val xNotation = seasonByEpisode.find(file.name)
            val episodeNumber = xNotation?.groupValues?.get(2)?.toIntOrNull()
                ?: leadingNumber.find(file.name)?.groupValues?.get(1)?.toIntOrNull()
                ?: trailingNumber.find(file.name)?.groupValues?.get(1)?.toIntOrNull()
            Entry(file, metadata.season ?: xNotation?.groupValues?.get(1)?.toIntOrNull()
                ?: folderSeason, metadata.episodeStart, metadata.episodeEnd, episodeNumber,
                folder)
        }
        entries.firstOrNull { entry ->
            (entry.season == null || entry.season == season) &&
                entry.episodeStart != null &&
                episode in entry.episodeStart..(entry.episodeEnd ?: entry.episodeStart)
        }?.let { return it.file }
        entries.firstOrNull { entry ->
            (entry.season == null || entry.season == season) && entry.number == episode
        }?.let { return it.file }

        // Some season packs contain only unnamed video files. File order is the episode order
        // only when all videos belong to one directory and no file advertises an episode number.
        if (entries.size < 2 || entries.any { it.episodeStart != null || it.number != null } ||
            entries.map { it.folder }.distinct().size != 1) return null
        val folderSeason = entries.first().season
        if (folderSeason != null && folderSeason != season) return null
        if (folderSeason == null && season != 1) return null
        return entries.getOrNull(episode - 1)?.file
    }

    fun episodeNumber(files: List<TorrServerFile>, fileId: Int, season: Int): Int? {
        if (season <= 0) return null
        val file = files.firstOrNull { it.id == fileId } ?: return null
        val metadata = TolokaReleaseMetadataParser.parse(file.name)
        val folder = file.path.substringBeforeLast('/', "")
        val folderSeason = folder.takeIf(String::isNotBlank)
            ?.let(TolokaReleaseMetadataParser::parse)?.season
        val xNotation = seasonByEpisode.find(file.name)
        val fileSeason = metadata.season ?: xNotation?.groupValues?.get(1)?.toIntOrNull()
            ?: folderSeason
        if (fileSeason != null && fileSeason != season) return null
        val explicit = metadata.episodeStart?.takeIf {
            metadata.episodeEnd == null || metadata.episodeEnd == it
        } ?: xNotation?.groupValues?.get(2)?.toIntOrNull()
            ?: leadingNumber.find(file.name)?.groupValues?.get(1)?.toIntOrNull()
            ?: trailingNumber.find(file.name)?.groupValues?.get(1)?.toIntOrNull()
        if (explicit != null) return explicit.takeIf { find(files, season, it)?.id == fileId }
        if (files.size < 2 || files.any { other ->
                val name = other.name
                val parsed = TolokaReleaseMetadataParser.parse(name)
                parsed.episodeStart != null || seasonByEpisode.containsMatchIn(name) ||
                    leadingNumber.containsMatchIn(name) || trailingNumber.containsMatchIn(name) ||
                    other.path.substringBeforeLast('/', "") != folder
            }) return null
        return files.indexOfFirst { it.id == fileId }.takeIf { it >= 0 }
            ?.plus(1)?.takeIf { find(files, season, it)?.id == fileId }
    }

    private data class Entry(
        val file: TorrServerFile,
        val season: Int?,
        val episodeStart: Int?,
        val episodeEnd: Int?,
        val number: Int?,
        val folder: String,
    )
}
