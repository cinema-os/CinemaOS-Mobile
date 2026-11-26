package com.nuvio.app.features.streams

import com.nuvio.app.features.plugins.PluginRuntimeResult

/** A built-in (native) movie / series source, queried in addition to addons and JS plugins. */
data class NativeCinemaProvider(
    val id: String,
    /** Display name, e.g. "CinemaOS · Movy". */
    val name: String,
    val budgetMs: Long,
)

/**
 * Built-in native sources (Movy > HdHub > Wing > MovieBox > Hindmoviez > 4KHDHub > VegaMovies > HDhub4u > Movies4u, in priority order). Implemented on Android;
 * other platforms expose none.
 */
expect object NativeCinemaSources {
    fun providers(): List<NativeCinemaProvider>

    suspend fun fetch(
        providerId: String,
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
    ): List<PluginRuntimeResult>

    /** True when the TMDB id is an anime title (those belong to the anime plugins). */
    suspend fun isAnimeTmdb(tmdbId: String): Boolean
}
