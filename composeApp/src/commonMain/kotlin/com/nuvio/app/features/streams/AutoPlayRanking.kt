package com.nuvio.app.features.streams

import com.nuvio.app.features.settings.CinemaSettingsRepository

/**
 * Auto-pick ranking shared with the CinemaOS TV app: provider priority first
 * (Movy > HdHub > Wing > MovieBox; anime: Anikoto > AnimeWorld > KickAssAnime > AnimeSalt > MegaPlay),
 * then the best quality this device can really play, with oversized (high bitrate) links
 * pushed back when a lighter, acceptable alternative exists.
 */
internal object AutoPlayRanking {
    private const val MAX_AUTOPLAY_BITRATE_MBPS = 12.0

    private val SIZE_REGEX = Regex("""(\d+(?:[.,]\d+)?)\s*(GB|GiB|MB|MiB)\b""", RegexOption.IGNORE_CASE)
    private val HEVC = Regex("hevc|x265|h\\.?265", RegexOption.IGNORE_CASE)
    private val AV1 = Regex("\\bav1\\b", RegexOption.IGNORE_CASE)
    private val DV = Regex("dolby\\s*vision|\\bdovi\\b|\\bdv\\b", RegexOption.IGNORE_CASE)
    private val HDR = Regex("hdr", RegexOption.IGNORE_CASE)
    private val TEN_BIT = Regex("10[\\s-]?bit|main\\s*10", RegexOption.IGNORE_CASE)
    private val UHD = Regex("4k|2160|uhd", RegexOption.IGNORE_CASE)
    private val P = Regex("(\\d{3,4})\\s*p", RegexOption.IGNORE_CASE)
    private val HD = Regex("\\bhd\\b", RegexOption.IGNORE_CASE)
    private val REMUX = Regex("\\bremux\\b", RegexOption.IGNORE_CASE)
    private val WEB_OR_HLS = Regex("web[-. ]?(dl|rip)|\\.m3u8|\\bhls\\b", RegexOption.IGNORE_CASE)

    /** Provider priority; 0 for anything that is not a CinemaOS source. */
    fun providerRank(addonName: String): Int = when {
        addonName.contains("Movy", true) -> 4
        addonName.contains("HdHub", true) -> 3
        addonName.contains("Wing", true) -> 2
        addonName.contains("MovieBox", true) -> 1
        addonName.contains("Anikoto", true) -> 5
        addonName.contains("AnimeWorld", true) -> 4
        addonName.contains("KickAss", true) -> 3
        addonName.contains("AnimeSalt", true) -> 2
        addonName.contains("MegaPlay", true) -> 1
        else -> 0
    }

    private fun textOf(stream: StreamItem): String =
        listOfNotNull(stream.name, stream.title, stream.description).joinToString(" ")

    fun sizeBytes(stream: StreamItem): Long? {
        stream.behaviorHints.videoSize?.takeIf { it > 0 }?.let { return it }
        val match = SIZE_REGEX.find(textOf(stream)) ?: return null
        val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val multiplier = if (match.groupValues[2].startsWith("G", true)) 1_073_741_824.0 else 1_048_576.0
        return (value * multiplier).toLong()
    }

    private fun estimatedBitrateMbps(stream: StreamItem, isSeries: Boolean): Double? {
        val size = sizeBytes(stream) ?: return null
        val minutes = if (isSeries) 45 else 120
        return size * 8.0 / (minutes * 60.0) / 1_000_000.0
    }

    fun isHeavy(stream: StreamItem, isSeries: Boolean): Boolean {
        val bitrate = estimatedBitrateMbps(stream, isSeries)
        if (bitrate != null) return bitrate > MAX_AUTOPLAY_BITRATE_MBPS
        return REMUX.containsMatchIn("${stream.name.orEmpty()} ${stream.description.orEmpty()}")
    }

    /** Nominal height declared by the stream (0 when unknown). */
    fun declaredHeight(stream: StreamItem): Int {
        val text = textOf(stream)
        if (UHD.containsMatchIn(text)) return 2160
        P.find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return if (HD.containsMatchIn(text)) 720 else 0
    }

    /**
     * Quality this device can really play: the declared height clamped to the display and to the decoder the
     * stream's codec needs. 0 when its codec cannot be decoded at all.
     */
    fun playableHeight(stream: StreamItem): Int {
        val caps = DeviceVideoCapabilities.caps()
        val text = textOf(stream)
        val declared = declaredHeight(stream)
        if (declared <= 0) return 1
        val isAv1 = AV1.containsMatchIn(text)
        val isHevc = HEVC.containsMatchIn(text)
        val isDv = DV.containsMatchIn(text)
        val isHdr = HDR.containsMatchIn(text)
        val tenBit = TEN_BIT.containsMatchIn(text)
        val needsHevc = isHevc || isDv || isHdr || (!isAv1 && declared >= 2160)

        var cap = caps.maxDisplayHeight
        when {
            isAv1 -> {
                if (!caps.av1) return 0
                if (declared >= 2160 && !caps.av1_4k) cap = minOf(cap, 1080)
            }
            needsHevc -> {
                if (!caps.hevc) return 0
                if ((isHdr || tenBit) && !caps.hevc10bit) return 0
                if (declared >= 2160 && !caps.hevc4k) cap = minOf(cap, 1080)
            }
            else -> if (declared >= 2160 && !caps.h264_4k) cap = minOf(cap, 1080)
        }
        return minOf(declared, cap)
    }

    private fun isWebDlOrHls(stream: StreamItem): Boolean =
        WEB_OR_HLS.containsMatchIn("${textOf(stream)} ${stream.playableDirectUrl.orEmpty()}")

    /** Drops links too heavy to stream when an acceptable lighter alternative exists. */
    private fun deprioritiseHeavy(streams: List<StreamItem>, isSeries: Boolean): List<StreamItem> {
        val light = streams.filter { !isHeavy(it, isSeries) && playableHeight(it) >= 720 }
        return if (light.isEmpty()) streams else streams.filter { !isHeavy(it, isSeries) }
    }

    private class Ranked(val stream: StreamItem, val index: Int, val lang: SourceLanguageInfo)

    /**
     * Orders [streams] best first: playable streams in the preferred audio language first, then provider
     * priority, then quality. A MovieBox dub in a non-preferred language never leads while a MovieBox
     * "Original Audio" link of at least 480p exists. Streams of non-CinemaOS sources are left in their original
     * order (this only re-ranks when at least one CinemaOS source is present).
     */
    fun rank(
        streams: List<StreamItem>,
        isSeries: Boolean,
        preferredLanguage: String = CinemaSettingsRepository.preferredAudioLanguage.value,
    ): List<StreamItem> {
        if (streams.none { providerRank(it.addonName) > 0 }) return streams
        val light = deprioritiseHeavy(streams, isSeries)
        val heavy = streams.filterNot { it in light }
        val caps = DeviceVideoCapabilities.caps()
        val ranked = light.mapIndexed { i, s -> Ranked(s, i, SourceLanguage.of(s)) }
        val hasMovieBoxOriginal = ranked.any {
            it.stream.addonName.contains("MovieBox", true) &&
                it.lang.kind == StreamAudioKind.ORIGINAL &&
                playableHeight(it.stream) >= 480
        }
        fun isForeignMovieBoxDub(r: Ranked) =
            hasMovieBoxOriginal &&
                r.stream.addonName.contains("MovieBox", true) &&
                r.lang.kind == StreamAudioKind.DUB &&
                preferredLanguage !in r.lang.codes
        val (demoted, kept) = ranked.partition { isForeignMovieBoxDub(it) }
        val ordered = kept.sortedWith(
            compareByDescending<Ranked> { if (playableHeight(it.stream) > 0) 1 else 0 }
                .thenByDescending { SourceLanguage.tier(it.lang, preferredLanguage) }
                .thenByDescending { providerRank(it.stream.addonName) }
                .thenByDescending { playableHeight(it.stream) }
                .thenByDescending { if (isWebDlOrHls(it.stream)) 1 else 0 }
                // A stream that fits the display natively beats a downscaled bigger one.
                .thenByDescending { if (declaredHeight(it.stream) <= caps.maxDisplayHeight) 1 else 0 }
                .thenBy { it.index },
        ).map { it.stream }
        return ordered + demoted.map { it.stream } + heavy
    }
}
