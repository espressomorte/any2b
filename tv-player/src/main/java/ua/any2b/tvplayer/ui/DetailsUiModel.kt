package ua.any2b.tvplayer.ui

import ua.any2b.core.anilist.AnimeDetails
import ua.any2b.core.tmdb.TmdbDetails
import ua.any2b.core.tmdb.TmdbMediaType

enum class DetailsSource { ANILIST, TMDB }

data class DetailsUiModel(
    val source: DetailsSource,
    val id: Int,
    val mediaType: String,
    val title: String,
    val secondaryTitles: List<String>,
    val posterUrl: String?,
    val overview: String?,
    val year: Int?,
    val rating: Double?,
    val genres: List<String>?,
    val countries: List<String>?,
    val durationMinutes: Int?,
    val episodeCount: Int?,
    val seasonCount: Int?,
    val seasons: List<TitleSeasonUi>?,
    val cast: List<String>?,
    val directors: List<String>?,
    val studios: List<String>?,
    val status: String?,
    val anilistDetails: AnimeDetails? = null,
    val tmdbDetails: TmdbDetails? = null,
)

data class TitleSeasonUi(
    val number: Int,
    val title: String?,
    val year: Int?,
    val episodeCount: Int?,
)

fun AnimeDetails.toUi() = DetailsUiModel(
    source = DetailsSource.ANILIST,
    id = id,
    mediaType = format.orEmpty(),
    title = title.display,
    secondaryTitles = listOfNotNull(title.native, title.romaji, title.english)
        .distinct().filter { it != title.display },
    posterUrl = cover,
    overview = description?.takeIf(String::isNotBlank),
    year = seasonYear,
    rating = score?.div(10.0),
    genres = genres.takeIf(List<String>::isNotEmpty),
    countries = null,
    durationMinutes = durationMinutes,
    episodeCount = episodes,
    seasonCount = null,
    seasons = null,
    cast = null,
    directors = null,
    studios = studios.takeIf(List<String>::isNotEmpty),
    status = status,
    anilistDetails = this,
)

fun TmdbDetails.toUi() = DetailsUiModel(
    source = DetailsSource.TMDB,
    id = title.id,
    mediaType = if (title.type == TmdbMediaType.TV) "TV" else "MOVIE",
    title = title.matchedSeasonTitle ?: title.ukrainianTitle ?: title.title,
    secondaryTitles = listOfNotNull(title.englishTitle, title.originalTitle, title.title)
        .distinct().filter { it != (title.matchedSeasonTitle ?: title.ukrainianTitle ?: title.title) },
    posterUrl = title.posterUrl,
    overview = title.overview?.takeIf(String::isNotBlank),
    year = title.matchedSeasonYear ?: title.year,
    rating = title.rating,
    genres = genres.takeIf(List<String>::isNotEmpty),
    countries = countryCodes.takeIf(List<String>::isNotEmpty),
    durationMinutes = runtimeMinutes,
    episodeCount = episodes,
    seasonCount = seasons,
    seasons = seasonEntries.map { TitleSeasonUi(it.number, it.name, it.year, it.episodeCount) }
        .takeIf(List<TitleSeasonUi>::isNotEmpty),
    cast = cast.takeIf(List<String>::isNotEmpty),
    directors = directors.takeIf(List<String>::isNotEmpty),
    studios = null,
    status = status,
    tmdbDetails = this,
)
