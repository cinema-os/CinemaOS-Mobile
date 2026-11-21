package com.nuvio.app.features.search

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.catalog.CatalogPage
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.tmdb.buildTmdbUrl
import com.nuvio.app.features.tmdb.normalizeTmdbLanguage
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

internal const val TMDB_DISCOVER_PAGE_SIZE = 20
private const val TMDB_MAX_PAGES = 500
private const val DEFAULT_WATCH_REGION = "US"
private const val ANIME_KEYWORD_ID = "210024"
private const val ANIME_GENRE_ID = 16

/** TMDB `/discover` backed catalog used by the Discover screen's filter UI (mirrors the CinemaOS website). */
internal object TmdbDiscoverService {
    private val log = Logger.withTag("TmdbDiscoverService")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadFilterOptions(): DiscoverFilterOptions = withContext(Dispatchers.Default) {
        val apiKey = TmdbSettingsRepository.effectiveApiKey()
        val language = normalizeTmdbLanguage(TmdbSettingsRepository.snapshot().language)
        val movieGenres = fetch<TmdbGenreList>("genre/movie/list", apiKey, mapOf("language" to language))
        val tvGenres = fetch<TmdbGenreList>("genre/tv/list", apiKey, mapOf("language" to language))
        val languages = fetch<List<TmdbLanguageEntry>>("configuration/languages", apiKey)
        val countries = fetch<List<TmdbCountryEntry>>("configuration/countries", apiKey, mapOf("language" to language))
        DiscoverFilterOptions(
            movieGenres = movieGenres?.genres.orEmpty().map { DiscoverTmdbGenre(it.id, it.name) },
            tvGenres = tvGenres?.genres.orEmpty().map { DiscoverTmdbGenre(it.id, it.name) },
            languages = languages.orEmpty()
                .filter { !it.code.isNullOrBlank() && !it.englishName.isNullOrBlank() }
                .map { DiscoverNamedOption(it.code.orEmpty(), it.englishName.orEmpty()) }
                .sortedBy { it.label },
            countries = countries.orEmpty()
                .filter { !it.code.isNullOrBlank() && !it.englishName.isNullOrBlank() }
                .map { DiscoverNamedOption(it.code.orEmpty(), it.englishName.orEmpty()) }
                .sortedBy { it.label },
        )
    }

    /** [skip] is the number of items already loaded; TMDB pages hold [TMDB_DISCOVER_PAGE_SIZE] items. */
    suspend fun discover(isTv: Boolean, filters: DiscoverFilters, skip: Int, isAnime: Boolean = false): CatalogPage =
        withContext(Dispatchers.Default) {
            val apiKey = TmdbSettingsRepository.effectiveApiKey()
            val language = normalizeTmdbLanguage(TmdbSettingsRepository.snapshot().language)
            val page = (skip / TMDB_DISCOVER_PAGE_SIZE) + 1
            val query = buildDiscoverQuery(isTv, filters, language, page, isAnime)
            val endpoint = if (isTv) "discover/tv" else "discover/movie"
            val body = fetch<TmdbDiscoverPage>(endpoint, apiKey, query, throwOnFailure = true)
            val items = body?.results.orEmpty().mapNotNull { it.toPreview(isTv) }
            val totalPages = minOf(body?.totalPages ?: page, TMDB_MAX_PAGES)
            CatalogPage(
                items = items,
                rawItemCount = items.size,
                nextSkip = if (page < totalPages && items.isNotEmpty()) page * TMDB_DISCOVER_PAGE_SIZE else null,
            )
        }

    internal fun buildDiscoverQuery(
        isTv: Boolean,
        filters: DiscoverFilters,
        language: String,
        page: Int,
        isAnime: Boolean = false,
    ): Map<String, String> {
        val dateField = if (isTv) "first_air_date" else "primary_release_date"
        val today = CurrentDateProvider.todayIsoDate()
        val sortBy = when (filters.sort) {
            DiscoverSort.Popular, DiscoverSort.NowPlaying, DiscoverSort.Trending -> "popularity.desc"
            DiscoverSort.Newest -> "$dateField.desc"
            DiscoverSort.Oldest, DiscoverSort.Upcoming -> "$dateField.asc"
            DiscoverSort.TopRated -> "vote_average.desc"
            DiscoverSort.MostVoted -> "vote_count.desc"
            DiscoverSort.Revenue -> if (isTv) "popularity.desc" else "revenue.desc"
        }
        return buildMap {
            put("language", language)
            put("page", page.toString())
            put("sort_by", sortBy)
            put("include_adult", "false")
            // Anime = Japanese animation keyword + Animation genre; picked genres are ANDed on top of these.
            val genreIds = if (isAnime) filters.genreIds + ANIME_GENRE_ID else filters.genreIds
            if (genreIds.isNotEmpty()) put("with_genres", genreIds.sorted().joinToString(","))
            if (isAnime) put("with_keywords", ANIME_KEYWORD_ID)

            val year = filters.yearKey
            // Anime season: a broadcast season (e.g. Spring 2024) pins the first air / release date to that quarter.
            val season = if (isAnime) filters.animeSeason else null
            if (season != null) {
                val seasonYear = year?.takeIf { it.startsWith("y") }?.drop(1)?.toIntOrNull() ?: currentYear(today)
                put("$dateField.gte", isoDate(seasonYear, season.startMonth, 1))
                put("$dateField.lte", isoDate(seasonYear, season.endMonth, season.endDay))
            } else {
                when {
                    year == null -> Unit
                    year.startsWith("y") -> {
                        val value = year.drop(1)
                        put(if (isTv) "first_air_date_year" else "primary_release_year", value)
                    }
                    year.startsWith("d") -> {
                        val start = year.drop(1).toIntOrNull()
                        if (start != null) {
                            put("$dateField.gte", "$start-01-01")
                            put("$dateField.lte", "${start + 9}-12-31")
                        }
                    }
                }
            }
            // Sort-implied date windows; a year or season filter already pins the date range.
            if (year == null && season == null) {
                when (filters.sort) {
                    DiscoverSort.Upcoming -> put("$dateField.gte", today)
                    DiscoverSort.NowPlaying -> {
                        put("$dateField.gte", shiftMonths(today, -2))
                        put("$dateField.lte", today)
                    }
                    DiscoverSort.Newest -> put("$dateField.lte", today)
                    DiscoverSort.Trending -> if (isTv) {
                        // Shows that aired an episode recently (includes long runners), most popular first.
                        put("air_date.gte", shiftMonths(today, -1))
                        put("air_date.lte", today)
                    } else {
                        put("$dateField.gte", shiftMonths(today, -6))
                        put("$dateField.lte", today)
                    }
                    else -> Unit
                }
            }

            val originalLanguage = filters.language ?: if (isAnime) "ja" else null
            originalLanguage?.let { put("with_original_language", it) }
            filters.country?.let { put("with_origin_country", it) }
            filters.minRating?.let {
                put("vote_average.gte", it.toString())
                put("vote_count.gte", "50")
            }
            if (filters.sort == DiscoverSort.TopRated && !containsKey("vote_count.gte")) {
                put("vote_count.gte", "200")
            }
            filters.runtime?.let { runtime ->
                runtime.minMinutes?.let { put("with_runtime.gte", it.toString()) }
                runtime.maxMinutes?.let { put("with_runtime.lte", it.toString()) }
            }
            if (isTv) {
                filters.networkId?.let { put("with_networks", it) }
                if (isAnime) filters.animeStatus?.let { put("with_status", it.tmdbStatus) }
            } else {
                filters.certification?.let {
                    put("certification_country", "US")
                    put("certification", it)
                }
            }
            filters.providerId?.let {
                put("with_watch_providers", it)
                put("with_watch_monetization_types", "flatrate")
                put("watch_region", filters.country ?: DEFAULT_WATCH_REGION)
            }
        }
    }

    private suspend inline fun <reified T> fetch(
        endpoint: String,
        apiKey: String,
        query: Map<String, String> = emptyMap(),
        throwOnFailure: Boolean = false,
    ): T? {
        val url = buildTmdbUrl(endpoint = endpoint, apiKey = apiKey, query = query)
        return runCatching {
            json.decodeFromString<T>(httpGetText(url))
        }.onFailure { error ->
            log.w(error) { "TMDB discover request failed for $endpoint" }
        }.let { result ->
            if (throwOnFailure) result.getOrThrow() else result.getOrNull()
        }
    }

    private fun TmdbDiscoverItem.toPreview(isTv: Boolean): MetaPreview? {
        val title = (if (isTv) name else title)?.takeIf { it.isNotBlank() }
            ?: title?.takeIf { it.isNotBlank() }
            ?: name?.takeIf { it.isNotBlank() }
            ?: return null
        val date = if (isTv) firstAirDate else releaseDate
        return MetaPreview(
            id = "tmdb:$id",
            type = if (isTv) "series" else "movie",
            name = title,
            poster = posterPath.tmdbImage("w500") ?: backdropPath.tmdbImage("w780"),
            banner = backdropPath.tmdbImage("w1280"),
            posterShape = PosterShape.Poster,
            description = overview?.takeIf { it.isNotBlank() },
            releaseInfo = date?.take(4)?.takeIf { it.isNotBlank() },
            rawReleaseDate = date?.takeIf { it.isNotBlank() },
            popularity = popularity,
            voteCount = voteCount,
            tmdbRating = voteAverage?.takeIf { it > 0.0 }?.let { ((it * 10).roundToInt() / 10.0).toString() },
        )
    }

    private fun String?.tmdbImage(size: String): String? =
        this?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/$size$it" }

    private fun currentYear(todayIso: String): Int = todayIso.take(4).toIntOrNull() ?: 2025

    private fun isoDate(year: Int, month: Int, day: Int): String =
        "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

    /** Shifts an ISO date by whole months; the day is clamped to 28 to stay valid in every month. */
    private fun shiftMonths(isoDate: String, months: Int): String {
        val parts = isoDate.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: return isoDate
        val month = parts.getOrNull(1)?.toIntOrNull() ?: return isoDate
        val day = (parts.getOrNull(2)?.toIntOrNull() ?: 1).coerceAtMost(28)
        val total = year * 12 + (month - 1) + months
        val newYear = total / 12
        val newMonth = total % 12 + 1
        return "${newYear.toString().padStart(4, '0')}-${newMonth.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
    }
}

@Serializable
private data class TmdbGenreList(
    val genres: List<TmdbGenreEntry> = emptyList(),
)

@Serializable
private data class TmdbGenreEntry(
    val id: Int,
    val name: String,
)

@Serializable
private data class TmdbLanguageEntry(
    @SerialName("iso_639_1") val code: String? = null,
    @SerialName("english_name") val englishName: String? = null,
)

@Serializable
private data class TmdbCountryEntry(
    @SerialName("iso_3166_1") val code: String? = null,
    @SerialName("english_name") val englishName: String? = null,
)

@Serializable
private data class TmdbDiscoverPage(
    val page: Int? = null,
    @SerialName("total_pages") val totalPages: Int? = null,
    val results: List<TmdbDiscoverItem> = emptyList(),
)

@Serializable
private data class TmdbDiscoverItem(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    val popularity: Double? = null,
)
