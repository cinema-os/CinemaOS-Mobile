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
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.provider_logo_appletv
import nuvio.composeapp.generated.resources.provider_logo_crunchyroll
import nuvio.composeapp.generated.resources.provider_logo_disney
import nuvio.composeapp.generated.resources.provider_logo_hbo
import nuvio.composeapp.generated.resources.provider_logo_hulu
import nuvio.composeapp.generated.resources.provider_logo_netflix
import nuvio.composeapp.generated.resources.provider_logo_paramount
import nuvio.composeapp.generated.resources.provider_logo_prime
import nuvio.composeapp.generated.resources.provider_logo_sonyliv
import nuvio.composeapp.generated.resources.provider_logo_zee5
import org.jetbrains.compose.resources.DrawableResource
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

/** Tile background, mirroring the approved CSS: solid, linear (CSS angle) or elliptical radial. */
sealed interface ProviderBackground {
    data class Solid(val color: Color) : ProviderBackground

    /** linear-gradient([angleDeg]deg, stops...) with stops as (position 0..1, colour). */
    class Linear(val angleDeg: Double, val stops: List<Pair<Float, Color>>) : ProviderBackground

    /**
     * radial-gradient([rxFraction]*width [ryFraction]*height at ([cx]*width, [cy]*height), stops...).
     */
    class Radial(
        val rxFraction: Float,
        val ryFraction: Float,
        val cx: Float,
        val cy: Float,
        val stops: List<Pair<Float, Color>>,
    ) : ProviderBackground
}

/**
 * Tile look for one service: [color] is the accent used for the provider page glow, [background] the
 * tile fill, [logo] the bundled logo (composeResources/drawable/provider_logo_*.png, no network) and
 * [tintWhite] draws it white (ColorFilter.tint SrcIn) when the original logo is dark or single-colour.
 */
data class ProviderBrand(
    val color: Color,
    val background: ProviderBackground,
    val logo: DrawableResource?,
    val tintWhite: Boolean,
)

private fun solid(hex: Long) = ProviderBackground.Solid(Color(hex))

private fun linear(angleDeg: Double, vararg stops: Pair<Float, Long>) =
    ProviderBackground.Linear(angleDeg, stops.map { it.first to Color(it.second) })

/** Tile background + bundled logo per TMDB provider id (same look as the TV app). */
private val ProviderBrands: Map<Int, ProviderBrand> by lazy {
    mapOf(
        8 to ProviderBrand(Color(0xFFB81D24), solid(0xFF000000), Res.drawable.provider_logo_netflix, tintWhite = false),
        9 to ProviderBrand(
            Color(0xFF0A7FD1),
            linear(135.0, 0f to 0xFF1FA6F2, 0.6f to 0xFF0A7FD1, 1f to 0xFF0868B0),
            Res.drawable.provider_logo_prime,
            tintWhite = true,
        ),
        337 to ProviderBrand(
            Color(0xFF1F5FD6),
            ProviderBackground.Radial(
                1.2f, 1.0f, 0.5f, 1.1f,
                listOf(0f to Color(0xFF1F5FD6), 0.55f to Color(0xFF0B1E5B), 1f to Color(0xFF040B26)),
            ),
            Res.drawable.provider_logo_disney,
            tintWhite = false,
        ),
        1899 to ProviderBrand(Color(0xFF4A1A78), solid(0xFF000000), Res.drawable.provider_logo_hbo, tintWhite = true),
        350 to ProviderBrand(Color(0xFF1C1C1E), solid(0xFF000000), Res.drawable.provider_logo_appletv, tintWhite = true),
        15 to ProviderBrand(Color(0xFF1CE783), solid(0xFF0B0C0F), Res.drawable.provider_logo_hulu, tintWhite = false),
        531 to ProviderBrand(
            Color(0xFF0045C4),
            linear(135.0, 0f to 0xFF0B6BFF, 0.55f to 0xFF0045C4, 1f to 0xFF00195A),
            Res.drawable.provider_logo_paramount,
            tintWhite = true,
        ),
        283 to ProviderBrand(
            Color(0xFFF47521),
            linear(135.0, 0f to 0xFFFF8A2B, 0.55f to 0xFFF47521, 1f to 0xFFC9520E),
            Res.drawable.provider_logo_crunchyroll,
            tintWhite = true,
        ),
        232 to ProviderBrand(
            Color(0xFF2A0B45),
            linear(135.0, 0f to 0xFF2A0B45, 1f to 0xFF14052A),
            Res.drawable.provider_logo_zee5,
            tintWhite = false,
        ),
        237 to ProviderBrand(Color(0xFF1A123C), solid(0xFF000000), Res.drawable.provider_logo_sonyliv, tintWhite = false),
    )
}

private val FallbackBrand = ProviderBrand(Color(26, 26, 46), solid(0xFF1A1A2E), null, tintWhite = false)

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
