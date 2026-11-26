package com.nuvio.app.features.player

import com.nuvio.app.features.streams.SourceLanguage
import com.nuvio.app.features.streams.StreamAudioKind
import com.nuvio.app.features.streams.StreamItem

/** One audio choice of an anime episode, e.g. "Japanese (Sub)" or "English (Dub)", backed by the best matching source. */
data class AnimeAudioVariant(
    val label: String,
    val stream: StreamItem,
    val isCurrent: Boolean,
)

private val ANIME_PROVIDERS = listOf("Anikoto", "AnimeWorld", "KickAss", "AnimeSalt", "MegaPlay")

internal fun isAnimeProviderName(name: String?): Boolean =
    name != null && ANIME_PROVIDERS.any { name.contains(it, ignoreCase = true) }

internal fun animeAudioLabel(stream: StreamItem): String? {
    val info = SourceLanguage.of(stream)
    val code = info.primary ?: return null
    val suffix = when (info.kind) {
        StreamAudioKind.DUB -> "Dub"
        StreamAudioKind.SUB -> "Sub"
        StreamAudioKind.ORIGINAL -> if (code == "ja") "Sub" else "Original"
        else -> if (code == "ja") "Sub" else "Dub"
    }
    return "${SourceLanguage.labelOf(code)} ($suffix)"
}

/**
 * Audio choices for the episode being played, built from the loaded anime sources. Empty unless the playing source
 * is an anime provider and at least two distinct audio choices exist. Picking one switches to that source (the
 * player keeps the current position).
 */
internal fun buildAnimeAudioVariants(
    allStreams: List<StreamItem>,
    currentStream: PlayerCurrentStream,
): List<AnimeAudioVariant> {
    val playable = allStreams.filter { isAnimeProviderName(it.addonName) && it.playableDirectUrl != null }
    if (playable.isEmpty()) return emptyList()
    val currentIndex = playable.currentPlayerStreamIndex(currentStream)
    val current = playable.getOrNull(currentIndex) ?: return emptyList()
    val currentLabel = animeAudioLabel(current)
    val byLabel = LinkedHashMap<String, MutableList<StreamItem>>()
    playable.forEach { s -> animeAudioLabel(s)?.let { byLabel.getOrPut(it) { mutableListOf() }.add(s) } }
    if (byLabel.size < 2) return emptyList()
    val currentHeight = current.parsedInfo().qualityHeight
    return byLabel.map { (label, list) ->
        val best = if (label == currentLabel) {
            current
        } else {
            list.sortedWith(
                compareByDescending<StreamItem> { it.addonId == current.addonId }
                    .thenBy { kotlin.math.abs(it.parsedInfo().qualityHeight - currentHeight) },
            ).first()
        }
        AnimeAudioVariant(label = label, stream = best, isCurrent = label == currentLabel)
    }.sortedWith(
        compareByDescending<AnimeAudioVariant> { it.label.startsWith("Japanese") }
            .thenByDescending { it.label.startsWith("English") }
            .thenBy { it.label },
    )
}
