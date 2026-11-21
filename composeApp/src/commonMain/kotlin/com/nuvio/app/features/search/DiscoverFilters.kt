package com.nuvio.app.features.search

/** Sort orders offered by the TMDB powered Discover filters (mirrors the CinemaOS website). */
enum class DiscoverSort(val tmdbValue: String) {
    Popular("popularity.desc"),
    Newest("release_date.desc"),
    Oldest("release_date.asc"),
    TopRated("vote_average.desc"),
    MostVoted("vote_count.desc"),
    Revenue("revenue.desc"),
    Upcoming("upcoming"),
    NowPlaying("now_playing"),
    /** Anime only: popular titles that aired recently. */
    Trending("trending"),
}

/** Sort orders offered for anime (everything else is hidden because it does not apply to anime browsing). */
val AnimeSortOptions: List<DiscoverSort> = listOf(
    DiscoverSort.Popular,
    DiscoverSort.TopRated,
    DiscoverSort.Newest,
    DiscoverSort.Trending,
)

/** Anime format: a TV series (`/discover/tv`) or a movie (`/discover/movie`), both with the anime base rules. */
enum class AnimeFormat { Tv, Movie }

/** Broadcast seasons expressed as inclusive month ranges (`endDay` is the last day of [endMonth]). */
enum class AnimeSeason(val startMonth: Int, val endMonth: Int, val endDay: Int) {
    Winter(1, 3, 31),
    Spring(4, 6, 30),
    Summer(7, 9, 30),
    Fall(10, 12, 31),
}

/** Airing status, mapped to TMDB TV `with_status` (0 = returning series, 3 = ended). */
enum class AnimeStatus(val tmdbStatus: String) {
    Airing("0"),
    Ended("3"),
}

/** Runtime buckets in minutes, expressed as TMDB `with_runtime` bounds. */
enum class DiscoverRuntime(val minMinutes: Int?, val maxMinutes: Int?) {
    Under90(null, 89),
    From90To120(90, 120),
    From120To150(120, 150),
    Over150(150, null),
}

data class DiscoverTmdbGenre(
    val id: Int,
    val name: String,
)

data class DiscoverNamedOption(
    val key: String,
    val label: String,
)

/** Provider / network entries are brand names, so they are not localized. */
val DiscoverProviderOptions: List<DiscoverNamedOption> = listOf(
    DiscoverNamedOption("8", "Netflix"),
    DiscoverNamedOption("9", "Prime Video"),
    DiscoverNamedOption("350", "Apple TV+"),
    DiscoverNamedOption("337", "Disney+"),
    DiscoverNamedOption("1899", "Max"),
    DiscoverNamedOption("15", "Hulu"),
    DiscoverNamedOption("531", "Paramount+"),
    DiscoverNamedOption("283", "Crunchyroll"),
    DiscoverNamedOption("232", "Zee5"),
    DiscoverNamedOption("237", "SonyLiv"),
)

val DiscoverNetworkOptions: List<DiscoverNamedOption> = listOf(
    DiscoverNamedOption("213", "Netflix"),
    DiscoverNamedOption("1024", "Amazon Prime"),
    DiscoverNamedOption("2739", "Disney+"),
    DiscoverNamedOption("49", "HBO"),
    DiscoverNamedOption("2552", "Apple TV+"),
    DiscoverNamedOption("453", "Hulu"),
    DiscoverNamedOption("4330", "Paramount+"),
    DiscoverNamedOption("174", "AMC"),
    DiscoverNamedOption("19", "Fox"),
    DiscoverNamedOption("6", "NBC"),
    DiscoverNamedOption("2", "ABC"),
    DiscoverNamedOption("16", "CBS"),
    DiscoverNamedOption("56", "Cartoon Network"),
    DiscoverNamedOption("359", "Showtime"),
    DiscoverNamedOption("318", "Starz"),
    DiscoverNamedOption("71", "FX"),
    DiscoverNamedOption("4", "BBC One"),
)

/** US certification codes, only applicable to movies (TMDB has no TV certification filter). */
val DiscoverCertificationOptions: List<String> = listOf("G", "PG", "PG-13", "R", "NC-17")

val DiscoverRatingOptions: List<Int> = listOf(9, 8, 7, 6, 5)

/** Decade keys are `d2010`, single years are `y2021`. */
internal fun discoverYearKey(year: Int): String = "y$year"
internal fun discoverDecadeKey(decadeStart: Int): String = "d$decadeStart"

data class DiscoverFilters(
    val genreIds: Set<Int> = emptySet(),
    val sort: DiscoverSort = DiscoverSort.Popular,
    val yearKey: String? = null,
    val minRating: Int? = null,
    val runtime: DiscoverRuntime? = null,
    val language: String? = null,
    val country: String? = null,
    val providerId: String? = null,
    val networkId: String? = null,
    val certification: String? = null,
    val animeFormat: AnimeFormat = AnimeFormat.Tv,
    val animeSeason: AnimeSeason? = null,
    val animeStatus: AnimeStatus? = null,
) {
    val activeCount: Int
        get() = (if (genreIds.isNotEmpty()) 1 else 0) +
            (if (sort != DiscoverSort.Popular) 1 else 0) +
            (if (animeFormat != AnimeFormat.Tv) 1 else 0) +
            listOf(yearKey, minRating, runtime, language, country, providerId, networkId, certification, animeSeason, animeStatus)
                .count { it != null }

    val isDefault: Boolean
        get() = activeCount == 0

    /**
     * Drops filters that only make sense for another media type after switching Movies / Series / Anime or the anime
     * format. [isTv] is the effective TMDB media type (for anime it follows [animeFormat]).
     */
    fun sanitizedFor(isTv: Boolean, mediaChanged: Boolean, isAnime: Boolean = false): DiscoverFilters {
        val sortAllowed = if (isAnime) {
            sort in AnimeSortOptions
        } else {
            sort != DiscoverSort.Trending && !(isTv && sort == DiscoverSort.Revenue)
        }
        return copy(
            genreIds = if (mediaChanged) emptySet() else genreIds,
            sort = if (sortAllowed) sort else DiscoverSort.Popular,
            // Anime browses single years (a season lives inside one year), so decades are dropped.
            yearKey = if (isAnime && yearKey?.startsWith("d") == true) null else yearKey,
            networkId = if (isTv) networkId else null,
            certification = if (isTv || isAnime) null else certification,
            animeFormat = if (isAnime) animeFormat else AnimeFormat.Tv,
            animeSeason = if (isAnime) animeSeason else null,
            animeStatus = if (isAnime && isTv) animeStatus else null,
        )
    }
}

/** Effective TMDB media type of a TMDB discover catalog: anime follows its format filter, the rest follow the catalog type. */
internal fun discoverIsTv(catalogType: String, filters: DiscoverFilters): Boolean =
    if (catalogType == "anime") filters.animeFormat == AnimeFormat.Tv else catalogType != "movie"

private val AnimeExcludedTvGenreIds = setOf(16, 99, 10763, 10764, 10766, 10767)
private val AnimeExcludedMovieGenreIds = setOf(16, 99, 10770)

data class DiscoverFilterOptions(
    val movieGenres: List<DiscoverTmdbGenre> = emptyList(),
    val tvGenres: List<DiscoverTmdbGenre> = emptyList(),
    val languages: List<DiscoverNamedOption> = emptyList(),
    val countries: List<DiscoverNamedOption> = emptyList(),
) {
    val isEmpty: Boolean
        get() = movieGenres.isEmpty() && tvGenres.isEmpty() && languages.isEmpty() && countries.isEmpty()

    /** Anime genres: the TMDB genres of the chosen format without the redundant Animation genre or non-fiction genres. */
    fun animeGenres(isTv: Boolean): List<DiscoverTmdbGenre> =
        if (isTv) {
            tvGenres.filterNot { it.id in AnimeExcludedTvGenreIds }
        } else {
            movieGenres.filterNot { it.id in AnimeExcludedMovieGenreIds }
        }
}
