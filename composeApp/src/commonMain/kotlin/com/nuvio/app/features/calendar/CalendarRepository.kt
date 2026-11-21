package com.nuvio.app.features.calendar

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.library.LibraryItem
import com.nuvio.app.features.tmdb.TmdbService
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.tmdb.buildTmdbUrl
import com.nuvio.app.features.tmdb.normalizeTmdbLanguage
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One poster on a calendar day: a movie release, or one or more episodes of a show. */
data class CalendarEntry(
    val key: String,
    val dateIso: String,
    val preview: MetaPreview,
    val title: String,
    val poster: String?,
    val isMovie: Boolean,
    val seasonNumber: Int? = null,
    val episodeNumbers: List<Int> = emptyList(),
    val episodeTitle: String? = null,
)

/** A show or movie the user follows: saved in the library or in Continue Watching. */
internal data class CalendarSource(
    val id: String,
    val isMovie: Boolean,
    val name: String,
    val poster: String?,
    val tmdbId: Int?,
    val imdbId: String?,
) {
    val key: String get() = "${if (isMovie) "movie" else "series"}:$id"
}

private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p"
private const val FETCH_CONCURRENCY = 6
private const val MAX_CONTINUE_WATCHING_SERIES = 60
private val SERIES_TYPES = setOf("series", "show", "tv", "tvshow", "anime")

internal fun libraryCalendarSources(items: List<LibraryItem>): List<CalendarSource> =
    items.mapNotNull { item ->
        val type = item.type.trim().lowercase()
        val isMovie = type == "movie" || type == "film"
        if (!isMovie && type !in SERIES_TYPES) return@mapNotNull null
        CalendarSource(
            id = item.id,
            isMovie = isMovie,
            name = item.name,
            poster = item.poster,
            tmdbId = item.tmdbId ?: item.id.removePrefix("tmdb:").takeIf { item.id.startsWith("tmdb:") }?.toIntOrNull(),
            imdbId = item.imdbId,
        )
    }

internal fun continueWatchingCalendarSources(entries: List<WatchProgressEntry>): List<CalendarSource> =
    entries
        .asSequence()
        .filter { it.parentMetaType.trim().lowercase() in SERIES_TYPES }
        .sortedByDescending { it.lastUpdatedEpochMs }
        .distinctBy { it.parentMetaId }
        .take(MAX_CONTINUE_WATCHING_SERIES)
        .map { entry ->
            CalendarSource(
                id = entry.parentMetaId,
                isMovie = false,
                name = entry.title,
                poster = entry.poster,
                tmdbId = entry.parentMetaId.removePrefix("tmdb:")
                    .takeIf { entry.parentMetaId.startsWith("tmdb:") }
                    ?.toIntOrNull(),
                imdbId = entry.parentMetaId.takeIf { it.startsWith("tt") },
            )
        }
        .toList()

/**
 * Builds month schedules for the user's shows and movies from TMDB. Show/season/movie lookups and whole
 * months are cached in memory for the session; lookups run concurrently with a small limit. Requests go
 * through the shared [httpGetText] client (DNS-over-HTTPS on Android) using the effective TMDB key.
 */
object CalendarRepository {
    private val log = Logger.withTag("CalendarRepository")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private val monthCache = mutableMapOf<String, List<CalendarEntry>>()
    private val tmdbIdCache = mutableMapOf<String, Int?>()
    private val tvCache = mutableMapOf<Int, TmdbCalendarTv?>()
    private val seasonCache = mutableMapOf<String, List<TmdbCalendarEpisode>>()
    private val movieCache = mutableMapOf<Int, TmdbCalendarMovie?>()

    suspend fun clear() {
        mutex.withLock {
            monthCache.clear()
            tvCache.clear()
            seasonCache.clear()
            movieCache.clear()
        }
    }

    internal fun cachedMonth(month: CalendarMonth, sources: List<CalendarSource>): List<CalendarEntry>? =
        monthCache[monthCacheKey(month, sources)]

    internal suspend fun loadMonth(month: CalendarMonth, sources: List<CalendarSource>): List<CalendarEntry> {
        val cacheKey = monthCacheKey(month, sources)
        mutex.withLock { monthCache[cacheKey]?.let { return it } }
        val apiKey = TmdbSettingsRepository.effectiveApiKey()
        val language = normalizeTmdbLanguage(TmdbSettingsRepository.snapshot().language)
        val semaphore = Semaphore(FETCH_CONCURRENCY)
        val entries = withContext(Dispatchers.Default) {
            coroutineScope {
                sources.distinctBy { it.key }.map { source ->
                    async {
                        semaphore.withPermit {
                            runCatching {
                                if (source.isMovie) {
                                    movieEntries(source, month, apiKey, language)
                                } else {
                                    showEntries(source, month, apiKey, language)
                                }
                            }.onFailure { log.w(it) { "Calendar lookup failed for ${source.key}" } }
                                .getOrDefault(emptyList())
                        }
                    }
                }.awaitAll().flatten()
            }
        }.sortedWith(compareBy<CalendarEntry>({ it.dateIso }, { it.title.lowercase() }))
        mutex.withLock { monthCache[cacheKey] = entries }
        return entries
    }

    private fun monthCacheKey(month: CalendarMonth, sources: List<CalendarSource>): String =
        month.key + "|" + sources.map { it.key }.distinct().sorted().joinToString(",").hashCode()

    private suspend fun resolveTmdbId(source: CalendarSource): Int? {
        source.tmdbId?.let { return it }
        mutex.withLock { if (source.key in tmdbIdCache) return tmdbIdCache[source.key] }
        val resolved = TmdbService.ensureTmdbId(
            videoId = source.id,
            mediaType = if (source.isMovie) "movie" else "tv",
            fallbackImdbId = source.imdbId,
        )?.toIntOrNull()
        mutex.withLock { tmdbIdCache[source.key] = resolved }
        return resolved
    }

    private suspend fun movieEntries(
        source: CalendarSource,
        month: CalendarMonth,
        apiKey: String,
        language: String,
    ): List<CalendarEntry> {
        val tmdbId = resolveTmdbId(source) ?: return emptyList()
        val movie = cached(movieCache, tmdbId) {
            fetch<TmdbCalendarMovie>("movie/$tmdbId", apiKey, mapOf("language" to language))
        } ?: return emptyList()
        val date = normalizeIsoDate(movie.releaseDate) ?: return emptyList()
        if (!month.contains(date)) return emptyList()
        val poster = source.poster ?: movie.posterPath.tmdbImage("w342")
        val title = source.name.ifBlank { movie.title.orEmpty() }
        return listOf(
            CalendarEntry(
                key = "${source.key}:$date",
                dateIso = date,
                preview = source.toPreview(title, poster),
                title = title,
                poster = poster,
                isMovie = true,
            ),
        )
    }

    private suspend fun showEntries(
        source: CalendarSource,
        month: CalendarMonth,
        apiKey: String,
        language: String,
    ): List<CalendarEntry> {
        val tmdbId = resolveTmdbId(source) ?: return emptyList()
        val tv = cached(tvCache, tmdbId) {
            fetch<TmdbCalendarTv>("tv/$tmdbId", apiKey, mapOf("language" to language))
        } ?: return emptyList()

        val lastAired = normalizeIsoDate(tv.lastEpisodeToAir?.airDate)
        val finished = tv.status.equals("Ended", ignoreCase = true) || tv.status.equals("Canceled", ignoreCase = true)
        if (finished && lastAired != null && lastAired < month.startIso) return emptyList()

        val seasonNumbers = seasonsForMonth(tv, month)
        if (seasonNumbers.isEmpty()) return emptyList()

        val poster = source.poster ?: tv.posterPath.tmdbImage("w342")
        val title = source.name.ifBlank { tv.name.orEmpty() }
        val preview = source.toPreview(title, poster)

        return seasonNumbers.flatMap { seasonNumber ->
            val episodes = cached(seasonCache, "$tmdbId:$seasonNumber") {
                fetch<TmdbCalendarSeason>("tv/$tmdbId/season/$seasonNumber", apiKey, mapOf("language" to language))
                    ?.episodes
            }.orEmpty()
            episodes
                .mapNotNull { episode -> normalizeIsoDate(episode.airDate)?.takeIf(month::contains)?.let { it to episode } }
                .groupBy({ it.first }, { it.second })
                .map { (date, dayEpisodes) ->
                    val sorted = dayEpisodes.sortedBy { it.episodeNumber }
                    CalendarEntry(
                        key = "${source.key}:$date:$seasonNumber",
                        dateIso = date,
                        preview = preview,
                        title = title,
                        poster = poster,
                        isMovie = false,
                        seasonNumber = seasonNumber,
                        episodeNumbers = sorted.map { it.episodeNumber },
                        episodeTitle = sorted.singleOrNull()?.name?.takeIf { it.isNotBlank() },
                    )
                }
        }
    }

    /** Seasons that can have episodes airing inside [month]: the one running then, plus any starting in it. */
    private fun seasonsForMonth(tv: TmdbCalendarTv, month: CalendarMonth): List<Int> {
        val seasons = tv.seasons.filter { it.seasonNumber > 0 }.sortedBy { it.seasonNumber }
        val result = linkedSetOf<Int>()
        seasons
            .filter { normalizeIsoDate(it.airDate)?.let { start -> start <= month.endIso } == true }
            .maxByOrNull { it.seasonNumber }
            ?.let { result += it.seasonNumber }
        seasons
            .filter { normalizeIsoDate(it.airDate)?.let(month::contains) == true }
            .forEach { result += it.seasonNumber }
        tv.nextEpisodeToAir?.let { next ->
            val date = normalizeIsoDate(next.airDate)
            val season = next.seasonNumber
            if (season != null && season > 0 && date != null && date <= month.endIso) result += season
        }
        return result.toList().takeLast(3)
    }

    private suspend fun <K, V> cached(cache: MutableMap<K, V?>, key: K, load: suspend () -> V?): V? {
        mutex.withLock { if (key in cache) return cache[key] }
        val value = load()
        mutex.withLock { cache[key] = value }
        return value
    }

    private suspend inline fun <reified T> fetch(
        endpoint: String,
        apiKey: String,
        query: Map<String, String>,
    ): T? {
        val url = buildTmdbUrl(endpoint = endpoint, apiKey = apiKey, query = query)
        return runCatching { json.decodeFromString<T>(httpGetText(url)) }
            .onFailure { error -> log.w(error) { "TMDB request failed for $endpoint" } }
            .getOrNull()
    }

    private fun CalendarSource.toPreview(title: String, poster: String?): MetaPreview =
        MetaPreview(
            id = id,
            type = if (isMovie) "movie" else "series",
            name = title,
            poster = poster,
            posterShape = PosterShape.Poster,
        )

    private fun String?.tmdbImage(size: String): String? =
        this?.takeIf { it.isNotBlank() }?.let { "$TMDB_IMAGE_BASE/$size$it" }
}

@Serializable
internal data class TmdbCalendarTv(
    val id: Int,
    val name: String? = null,
    val status: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    val seasons: List<TmdbCalendarSeasonSummary> = emptyList(),
    @SerialName("last_episode_to_air") val lastEpisodeToAir: TmdbCalendarEpisode? = null,
    @SerialName("next_episode_to_air") val nextEpisodeToAir: TmdbCalendarEpisode? = null,
)

@Serializable
internal data class TmdbCalendarSeasonSummary(
    @SerialName("season_number") val seasonNumber: Int = 0,
    @SerialName("air_date") val airDate: String? = null,
)

@Serializable
internal data class TmdbCalendarSeason(
    val episodes: List<TmdbCalendarEpisode> = emptyList(),
)

@Serializable
internal data class TmdbCalendarEpisode(
    @SerialName("episode_number") val episodeNumber: Int = 0,
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("air_date") val airDate: String? = null,
    val name: String? = null,
)

@Serializable
internal data class TmdbCalendarMovie(
    val id: Int,
    val title: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
)
