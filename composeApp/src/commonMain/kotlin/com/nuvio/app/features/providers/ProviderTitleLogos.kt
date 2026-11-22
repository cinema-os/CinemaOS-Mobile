package com.nuvio.app.features.providers

import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.tmdb.TmdbImage
import com.nuvio.app.features.tmdb.TmdbImagesResponse
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.tmdb.buildTmdbUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

/**
 * TMDB discover has no title logos, so fetch them (bounded parallelism, cached, English preferred)
 * for the provider cards, which overlay the logo like Home's landscape cards and fall back to text.
 */
internal object ProviderTitleLogos {
    private const val PARALLELISM = 8
    private const val TIMEOUT_MS = 4_000L

    private val json = Json { ignoreUnknownKeys = true }
    private val gate = Semaphore(PARALLELISM)
    private val cacheMutex = Mutex()
    private val cache = HashMap<String, String>()

    suspend fun fill(items: List<MetaPreview>): List<MetaPreview> = withContext(Dispatchers.Default) {
        val apiKey = TmdbSettingsRepository.effectiveApiKey()
        if (apiKey.isBlank()) return@withContext items
        coroutineScope {
            items.map { item ->
                async {
                    val tmdbId = item.id.removePrefix("tmdb:").toIntOrNull() ?: return@async item
                    val mediaType = if (item.type == "series") "tv" else "movie"
                    val key = "$mediaType:$tmdbId"
                    val logo = cacheMutex.withLock { cache[key] }
                        ?: gate.withPermit {
                            withTimeoutOrNull(TIMEOUT_MS) { fetchLogo(mediaType, tmdbId, apiKey) }
                        }?.also { cacheMutex.withLock { cache[key] = it } }
                    if (logo.isNullOrBlank()) item else item.copy(logo = logo)
                }
            }.awaitAll()
        }
    }

    /** Logo url, "" when the title has none, or null when the request failed (not cached). */
    private suspend fun fetchLogo(mediaType: String, tmdbId: Int, apiKey: String): String? = runCatching {
        val url = buildTmdbUrl(endpoint = "$mediaType/$tmdbId/images", apiKey = apiKey)
        val logos: List<TmdbImage> = json.decodeFromString<TmdbImagesResponse>(httpGetText(url))
            .logos.filter { !it.filePath.isNullOrBlank() }
        val best = logos.firstOrNull { it.iso6391 == "en" } ?: logos.firstOrNull()
        best?.filePath?.let { "$TMDB_IMAGE_BASE/w300$it" }.orEmpty()
    }.getOrNull()
}
