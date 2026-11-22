package com.nuvio.app.features.providers

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.intl.Locale
import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.tmdb.buildTmdbUrl
import com.nuvio.app.features.tmdb.normalizeTmdbLanguage
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/** A streaming service shown in the Home "Providers" row and on the Provider page. */
data class StreamingProvider(
    val id: Int,
    val name: String,
    val logoPath: String? = null,
) {
    val logoUrl: String?
        get() = logoPath?.takeIf { it.isNotBlank() }?.let { "$TMDB_IMAGE_BASE/w185$it" }
}

/**
 * Tile look for one service: [color] is the gradient's brand colour, [wordmarkUrl] a rectangular
 * wordmark (Wikimedia Commons PNG render of the official SVG/PNG, 500px), and [tintWhite] draws it
 * white when the original logo is dark or the brand's own colour.
 */
data class ProviderBrand(
    val color: Color,
    val wordmarkUrl: String,
    val tintWhite: Boolean,
)

private const val COMMONS_THUMB = "https://upload.wikimedia.org/wikipedia/commons/thumb/"

private fun brand(r: Int, g: Int, b: Int, path: String, tintWhite: Boolean) =
    ProviderBrand(Color(r, g, b), COMMONS_THUMB + path, tintWhite)

/** Brand colour + wordmark per TMDB provider id (same table as the TV app). */
private val ProviderBrands: Map<Int, ProviderBrand> = mapOf(
    8 to brand(184, 29, 36, "0/08/Netflix_2015_logo.svg/500px-Netflix_2015_logo.svg.png", tintWhite = true),
    9 to brand(15, 61, 92, "9/90/Prime_Video_logo_(2024).svg/500px-Prime_Video_logo_(2024).svg.png", tintWhite = true),
    337 to brand(26, 77, 184, "7/77/Disney_Plus_logo.svg/500px-Disney_Plus_logo.svg.png", tintWhite = false),
    1899 to brand(74, 26, 120, "7/7e/HBO_Max_May_2025_(Horizontal).svg/500px-HBO_Max_May_2025_(Horizontal).svg.png", tintWhite = true),
    350 to brand(28, 28, 30, "2/28/Apple_TV_Plus_Logo.svg/500px-Apple_TV_Plus_Logo.svg.png", tintWhite = true),
    15 to brand(12, 61, 44, "f/f9/Hulu_logo_(2018).svg/500px-Hulu_logo_(2018).svg.png", tintWhite = false),
    531 to brand(10, 58, 122, "4/4e/Paramount+_logo.svg/500px-Paramount+_logo.svg.png", tintWhite = true),
    283 to brand(122, 52, 16, "d/d9/Crunchyroll_2024.svg/500px-Crunchyroll_2024.svg.png", tintWhite = true),
    232 to brand(70, 20, 88, "6/6e/ZEE5_2025.svg/500px-ZEE5_2025.svg.png", tintWhite = false),
    // SonyLIV: Commons only has the square app icon, no wordmark.
    237 to brand(26, 18, 60, "f/f7/SonyLIV_2020.png/500px-SonyLIV_2020.png", tintWhite = false),
)

private val FallbackBrand = ProviderBrand(Color(26, 26, 46), "", tintWhite = false)

fun providerBrand(providerId: Int): ProviderBrand = ProviderBrands[providerId] ?: FallbackBrand

/**
 * The same services offered by the Discover "Streaming on" filter
 * (features/search/DiscoverFilters.kt, DiscoverProviderOptions). Copied rather than referenced so the
 * Discover screen can evolve independently; keep the two lists in sync.
 */
val CinemaOsStreamingProviders: List<StreamingProvider> = listOf(
    StreamingProvider(8, "Netflix"),
    StreamingProvider(9, "Prime Video"),
    StreamingProvider(350, "Apple TV+"),
    StreamingProvider(337, "Disney+"),
    StreamingProvider(1899, "Max"),
    StreamingProvider(15, "Hulu"),
    StreamingProvider(531, "Paramount+"),
    StreamingProvider(283, "Crunchyroll"),
    StreamingProvider(232, "Zee5"),
    StreamingProvider(237, "SonyLiv"),
)

enum class ProviderSort { Popular, TopRated, Newest }

data class ProviderPage(
    val items: List<MetaPreview>,
    val hasMore: Boolean,
)

internal const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p"
private const val DEFAULT_WATCH_REGION = "US"

/** Device region (ISO 3166-1 alpha-2), falling back to US. */
internal fun deviceWatchRegion(): String =
    runCatching { Locale.current.region }
        .getOrNull()
        ?.uppercase()
        ?.takeIf { it.length == 2 && it.all(Char::isLetter) }
        ?: DEFAULT_WATCH_REGION

/**
 * TMDB backed data for the Providers row and Provider page. Every request goes through the shared
 * [httpGetText] client (DNS-over-HTTPS on Android) with the app's effective TMDB key.
 */
object ProvidersRepository {
    private val log = Logger.withTag("ProvidersRepository")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var logoCache: Map<Int, String>? = null

    /** The provider list with TMDB logos filled in (cached for the session). */
    suspend fun providersWithLogos(): List<StreamingProvider> {
        val logos = loadLogos()
        return CinemaOsStreamingProviders.map { provider ->
            provider.copy(logoPath = logos[provider.id] ?: provider.logoPath)
        }
    }

    fun cachedProvidersWithLogos(): List<StreamingProvider> {
        val logos = logoCache ?: return CinemaOsStreamingProviders
        return CinemaOsStreamingProviders.map { it.copy(logoPath = logos[it.id] ?: it.logoPath) }
    }

    private suspend fun loadLogos(): Map<Int, String> {
        mutex.withLock { logoCache?.let { return it } }
        val region = deviceWatchRegion()
        val apiKey = TmdbSettingsRepository.effectiveApiKey()
        val merged = withContext(Dispatchers.Default) {
            coroutineScope {
                val movie = async {
                    fetch<TmdbWatchProviderList>("watch/providers/movie", apiKey, mapOf("watch_region" to region))
                }
                val tv = async {
                    fetch<TmdbWatchProviderList>("watch/providers/tv", apiKey, mapOf("watch_region" to region))
                }
                val result = linkedMapOf<Int, String>()
                (movie.await()?.results.orEmpty() + tv.await()?.results.orEmpty()).forEach { entry ->
                    val path = entry.logoPath?.takeIf { it.isNotBlank() } ?: return@forEach
                    if (entry.providerId !in result) result[entry.providerId] = path
                }
                result
            }
        }
        val missing = CinemaOsStreamingProviders.filter { it.id !in merged }
        if (missing.isNotEmpty() && region != DEFAULT_WATCH_REGION) {
            // Services not offered in the device region still get a logo from the US catalog.
            val us = fetch<TmdbWatchProviderList>("watch/providers/tv", apiKey, mapOf("watch_region" to DEFAULT_WATCH_REGION))
            us?.results.orEmpty().forEach { entry ->
                val path = entry.logoPath?.takeIf { it.isNotBlank() } ?: return@forEach
                if (entry.providerId !in merged) merged[entry.providerId] = path
            }
        }
        if (merged.isNotEmpty()) {
            mutex.withLock { logoCache = merged }
        }
        return merged
    }

    /** [page] is 1-based. */
    suspend fun discover(
        providerId: Int,
        isTv: Boolean,
        sort: ProviderSort,
        page: Int,
        region: String = deviceWatchRegion(),
    ): ProviderPage = withContext(Dispatchers.Default) {
        val apiKey = TmdbSettingsRepository.effectiveApiKey()
        val language = normalizeTmdbLanguage(TmdbSettingsRepository.snapshot().language)
        val today = CurrentDateProvider.todayIsoDate()
        val query = buildMap {
            put("language", language)
            put("page", page.toString())
            put("with_watch_providers", providerId.toString())
            put("watch_region", region)
            put("with_watch_monetization_types", "flatrate")
            put("include_adult", "false")
            when (sort) {
                ProviderSort.Popular -> put("sort_by", "popularity.desc")
                ProviderSort.TopRated -> {
                    put("sort_by", "vote_average.desc")
                    put("vote_count.gte", if (isTv) "100" else "200")
                }
                ProviderSort.Newest -> {
                    put("sort_by", if (isTv) "first_air_date.desc" else "primary_release_date.desc")
                    put(if (isTv) "first_air_date.lte" else "primary_release_date.lte", today)
                }
            }
        }
        val endpoint = if (isTv) "discover/tv" else "discover/movie"
        val body = fetch<TmdbProviderDiscoverPage>(endpoint, apiKey, query, throwOnFailure = true)
        val items = body?.results.orEmpty().mapNotNull { it.toPreview(isTv) }
        val totalPages = (body?.totalPages ?: page).coerceAtMost(500)
        ProviderPage(items = items, hasMore = page < totalPages && items.isNotEmpty())
    }

    private suspend inline fun <reified T> fetch(
        endpoint: String,
        apiKey: String,
        query: Map<String, String> = emptyMap(),
        throwOnFailure: Boolean = false,
    ): T? {
        val url = buildTmdbUrl(endpoint = endpoint, apiKey = apiKey, query = query)
        return runCatching { json.decodeFromString<T>(httpGetText(url)) }
            .onFailure { error -> log.w(error) { "TMDB request failed for $endpoint" } }
            .let { result -> if (throwOnFailure) result.getOrThrow() else result.getOrNull() }
    }

    private fun TmdbProviderDiscoverItem.toPreview(isTv: Boolean): MetaPreview? {
        val label = (if (isTv) name else title)?.takeIf { it.isNotBlank() }
            ?: title?.takeIf { it.isNotBlank() }
            ?: name?.takeIf { it.isNotBlank() }
            ?: return null
        val date = (if (isTv) firstAirDate else releaseDate)?.takeIf { it.isNotBlank() }
        return MetaPreview(
            id = "tmdb:$id",
            type = if (isTv) "series" else "movie",
            name = label,
            poster = posterPath.tmdbImage("w500") ?: backdropPath.tmdbImage("w780"),
            banner = backdropPath.tmdbImage("w1280"),
            posterShape = PosterShape.Poster,
            description = overview?.takeIf { it.isNotBlank() },
            releaseInfo = date?.take(4),
            rawReleaseDate = date,
            popularity = popularity,
            voteCount = voteCount,
            tmdbRating = voteAverage?.takeIf { it > 0.0 }?.let { ((it * 10).roundToInt() / 10.0).toString() },
        )
    }

    private fun String?.tmdbImage(size: String): String? =
        this?.takeIf { it.isNotBlank() }?.let { "$TMDB_IMAGE_BASE/$size$it" }
}

@Serializable
private data class TmdbWatchProviderList(
    val results: List<TmdbWatchProviderEntry> = emptyList(),
)

@Serializable
private data class TmdbWatchProviderEntry(
    @SerialName("provider_id") val providerId: Int,
    @SerialName("provider_name") val providerName: String? = null,
    @SerialName("logo_path") val logoPath: String? = null,
)

@Serializable
private data class TmdbProviderDiscoverPage(
    val page: Int? = null,
    @SerialName("total_pages") val totalPages: Int? = null,
    val results: List<TmdbProviderDiscoverItem> = emptyList(),
)

@Serializable
private data class TmdbProviderDiscoverItem(
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
