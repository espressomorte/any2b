package ua.any2b.tvplayer.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import ua.any2b.core.anilist.AniListClient
import ua.any2b.core.anilist.AnimeCard
import ua.any2b.core.tmdb.FribbMappings
import ua.any2b.core.tmdb.TmdbTitle

/** AniList relation edges resolved through explicit AniBridge/Fribb IDs only. */
class ExplicitRelatedCatalog(context: Context) {
    private val appContext = context.applicationContext
    private val aniList = AniListClient()

    suspend fun animeRelations(title: TmdbTitle): List<TmdbTitle> {
        val bridge = try {
            withTimeoutOrNull(8000) { AniBridgeMappingRepository.get(appContext) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        var fribb = if (bridge == null || bridge.anilistFor(title.type, title.id,
                title.matchedSeasonNumber).isEmpty()) loadFribb() else null
        val sourceIds = bridge?.anilistFor(title.type, title.id, title.matchedSeasonNumber)
            .orEmpty().map { it.aniListId }.ifEmpty {
                fribb?.anilistFor(title.type, title.id).orEmpty()
                    .filter { title.matchedSeasonNumber == null ||
                        it.tmdbSeason == title.matchedSeasonNumber }
                    .map { it.aniListId }
            }.distinct()
        if (sourceIds.isEmpty()) return emptyList()
        val cards = sourceIds.flatMap { aniList.animeDetails(it).related }
            .distinctBy(AnimeCard::id)
        if (cards.any { bridge?.preferredFor(it.id, it.format) == null } && fribb == null)
            fribb = loadFribb()
        return cards.mapNotNull { card ->
            val target = bridge?.preferredFor(card.id, card.format)
            val fallback = if (target == null) FribbMappings.preferred(
                fribb?.byAniList?.get(card.id).orEmpty(), card.format) else null
            val type = target?.type ?: fallback?.type ?: return@mapNotNull null
            val id = target?.id ?: fallback?.id ?: return@mapNotNull null
            val season = target?.season ?: fallback?.season
            if (type == title.type && id == title.id &&
                (title.matchedSeasonNumber == null || title.matchedSeasonNumber == season))
                return@mapNotNull null
            TmdbTitle(id = id, type = type, title = card.title.display,
                originalTitle = card.title.romaji ?: card.title.native,
                year = card.seasonYear, posterUrl = card.cover, overview = null,
                rating = card.score?.div(10.0), englishTitle = card.title.english,
                matchedSeasonNumber = season)
        }.distinctBy { Triple(it.type, it.id, it.matchedSeasonNumber) }
    }

    private suspend fun loadFribb() = try {
        withTimeoutOrNull(8000) { FribbMappingRepository.get(appContext) }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }
}
