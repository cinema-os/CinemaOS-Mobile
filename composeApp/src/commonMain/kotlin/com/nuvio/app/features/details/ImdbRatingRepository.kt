package com.nuvio.app.features.details

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.tmdb.TmdbService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Real IMDb ratings, sourced from Cinemeta (free, keyless): `meta.imdbRating`.
 * TMDB's vote_average is a different score and must never be labelled as IMDb.
 *
 * Results (including misses) are cached in memory for the process lifetime. [peek] is a cheap,
 * non-suspending cache read for list/card contexts; [fetch] does the lazy network lookup.
 */
object ImdbRatingRepository {
    private val log = Logger.withTag("ImdbRatingRepo")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    /** imdbId -> rating string, or "" when Cinemeta has no rating for it. */
    private val cache = mutableMapOf<String, String>()

    /** Returns the cached IMDb rating without any I/O. Null means "unknown yet" or "no rating". */
    fun peek(imdbId: String?): String? {
        val key = normalizeImdbId(imdbId) ?: return null
        return cache[key]?.takeIf { it.isNotBlank() }
    }

    /**
     * Resolves the IMDb rating for [id] (`tt…` or `tmdb:N`). [knownImdbId] is preferred when the
     * meta already carries one. Returns null when no real IMDb rating exists.
     */
    suspend fun fetch(type: String, id: String, knownImdbId: String? = null): String? {
        val imdbId = normalizeImdbId(knownImdbId)
            ?: normalizeImdbId(id)
            ?: resolveImdbIdFromTmdb(type = type, id = id)
            ?: return null

        mutex.withLock { cache[imdbId] }?.let { return it.takeIf(String::isNotBlank) }

        val rating = try {
            withTimeoutOrNull(FETCH_TIMEOUT_MS) { requestRating(type = type, imdbId = imdbId) }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            log.w { "Cinemeta rating lookup failed for $imdbId: ${error.message}" }
            return null
        } ?: return null

        mutex.withLock { cache[imdbId] = rating }
        return rating.takeIf(String::isNotBlank)
    }

    fun clearCache() {
        cache.clear()
    }

    private suspend fun resolveImdbIdFromTmdb(type: String, id: String): String? {
        val tmdbId = id
            .takeIf { it.startsWith("tmdb:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.substringBefore(':')
            ?.toIntOrNull()
            ?: return null
        return try {
            withTimeoutOrNull(FETCH_TIMEOUT_MS) {
                TmdbService.tmdbToImdb(tmdbId = tmdbId, mediaType = type)
            }?.let(::normalizeImdbId)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            null
        }
    }

    /** Returns the rating string, or "" when Cinemeta answered but has none. */
    private suspend fun requestRating(type: String, imdbId: String): String {
        val cinemetaType = if (type.trim().lowercase() in SERIES_TYPES) "series" else "movie"
        val payload = httpGetText("$CINEMETA_BASE_URL/meta/$cinemetaType/$imdbId.json")
        val meta = (json.parseToJsonElement(payload) as? JsonObject)
            ?.get("meta") as? JsonObject
            ?: return ""
        val raw = meta["imdbRating"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        return raw.takeIf { it.toDoubleOrNull()?.let { value -> value > 0.0 } == true }.orEmpty()
    }

    private fun normalizeImdbId(value: String?): String? =
        value
            ?.trim()
            ?.substringBefore(':')
            ?.takeIf { it.startsWith("tt", ignoreCase = true) && it.length > 2 }
            ?.lowercase()

    private val SERIES_TYPES = setOf("series", "tv", "show", "tvshow")
    private const val CINEMETA_BASE_URL = "https://v3-cinemeta.strem.io"
    private const val FETCH_TIMEOUT_MS = 8_000L
}
