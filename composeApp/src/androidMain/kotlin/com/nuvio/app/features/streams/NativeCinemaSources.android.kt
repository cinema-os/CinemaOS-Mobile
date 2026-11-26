package com.nuvio.app.features.streams

import com.nuvio.app.features.plugins.PluginRuntimeResult
import com.nuvio.app.features.streams.cinema.CinemaHttp
import com.nuvio.app.features.streams.cinema.CinemaScrapeRequest
import com.nuvio.app.features.streams.cinema.CinemaScraper
import com.nuvio.app.features.streams.cinema.FourKHdHubScraper
import com.nuvio.app.features.streams.cinema.HdHub4uScraper
import com.nuvio.app.features.streams.cinema.HdHubScraper
import com.nuvio.app.features.streams.cinema.HindmoviezScraper
import com.nuvio.app.features.streams.cinema.MovieBoxScraper
import com.nuvio.app.features.streams.cinema.Movies4uScraper
import com.nuvio.app.features.streams.cinema.MovyScraper
import com.nuvio.app.features.streams.cinema.VegaMoviesScraper
import com.nuvio.app.features.streams.cinema.WingScraper

actual object NativeCinemaSources {
    private val http by lazy { CinemaHttp() }

    /** Priority order: Movy > HdHub > Wing > MovieBox > Hindmoviez > 4KHDHub > VegaMovies > HDhub4u > Movies4u. */
    private val scrapers: List<CinemaScraper> by lazy {
        listOf(
            MovyScraper(http), HdHubScraper(http), WingScraper(http), MovieBoxScraper(http),
            HindmoviezScraper(http), FourKHdHubScraper(http), VegaMoviesScraper(http),
            HdHub4uScraper(http), Movies4uScraper(http),
        )
    }

    actual fun providers(): List<NativeCinemaProvider> =
        scrapers.map { NativeCinemaProvider(id = it.id, name = it.name, budgetMs = it.budgetMs) }

    actual suspend fun fetch(
        providerId: String,
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
    ): List<PluginRuntimeResult> {
        val scraper = scrapers.firstOrNull { it.id == providerId } ?: return emptyList()
        val isMovie = mediaType.equals("movie", ignoreCase = true)
        return scraper.getStreams(
            CinemaScrapeRequest(
                tmdbId = tmdbId,
                mediaType = if (isMovie) "movie" else "tv",
                season = season ?: 1,
                episode = episode ?: 1,
            ),
        )
    }

    actual suspend fun isAnimeTmdb(tmdbId: String): Boolean = http.isAnimeTmdb(tmdbId)
}
