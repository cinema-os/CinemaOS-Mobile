package com.nuvio.app.features.player

import com.nuvio.app.features.streams.SourceLanguage
import com.nuvio.app.features.streams.StreamAudioKind
import com.nuvio.app.features.streams.StreamItem
import kotlin.math.round

/** One detected audio language, with a display name and a flag emoji. */
internal data class StreamLanguage(val name: String, val flag: String)

/** Structured facts read from a stream's name, title, description and filename. */
internal data class ParsedStreamInfo(
    val qualityHeight: Int = 0,
    val qualityLabel: String? = null,
    val sizeLabel: String? = null,
    val hdr: Boolean = false,
    val hdr10: Boolean = false,
    val hdr10Plus: Boolean = false,
    val dolbyVision: Boolean = false,
    val atmos: Boolean = false,
    val audioFormat: String? = null,
    val audioFormats: List<String> = listOfNotNull(audioFormat),
    val channels: String? = null,
    val codec: String? = null,
    val tenBit: Boolean = false,
    val imax: Boolean = false,
    val sourceType: String? = null,
    val sourceTypes: List<String> = listOfNotNull(sourceType),
    val languages: List<StreamLanguage> = emptyList(),
    val multiAudio: Boolean = false,
    val audioKind: StreamAudioKind = StreamAudioKind.UNKNOWN,
    val releaseKey: String = "",
)

private val RES_REGEX = Regex("""(?<![0-9])(2160|1440|1080|720|576|480|360)\s?[pi]\b""", RegexOption.IGNORE_CASE)
private val UHD_REGEX = Regex("""\b(4k|uhd)\b""", RegexOption.IGNORE_CASE)
private val SIZE_REGEX = Regex("""(\d+(?:[.,]\d+)?)\s?(GB|GiB|MB|MiB)\b""", RegexOption.IGNORE_CASE)
private val DV_REGEX = Regex("""dolby[\s._-]?vision|\bDV\b|\bDoVi\b|\bDOVI\b""", RegexOption.IGNORE_CASE)
private val HDR10P_REGEX = Regex("""\bhdr[\s._-]?10[\s._-]?(\+|plus|p\b)""", RegexOption.IGNORE_CASE)
private val HDR10_REGEX = Regex("""\bhdr[\s._-]?10(?![0-9])""", RegexOption.IGNORE_CASE)
private val HDR_REGEX = Regex("""\bhdr(?![a-z]|[\s._-]?10)""", RegexOption.IGNORE_CASE)
private val IMAX_REGEX = Regex("""\bimax\b""", RegexOption.IGNORE_CASE)
private val ATMOS_REGEX = Regex("""atmos""", RegexOption.IGNORE_CASE)
private val DTS_REGEX = Regex("""\bdts(?:[\s._:-]?(?:hd(?:[\s._-]?ma)?|x|es))?(?![a-z0-9])""", RegexOption.IGNORE_CASE)
private val DD_REGEX = Regex("""\b(true[\s._-]?hd|(?:dolby[\s._-]+)?digital(?:[\s._-]*plus|\+)?|e-?ac-?3|dd\+|ddp|dd|ac-?3|aac|flac|opus)(?![a-z])""", RegexOption.IGNORE_CASE)
private val CHANNEL_REGEX = Regex("""(?<![0-9])(7[.\s]1|5[.\s]1|2[.\s]0|2[.\s]1)(?![0-9])""")
private val CHANNEL_CH_REGEX = Regex("""(?<![0-9])(8|6)\s?ch\b""", RegexOption.IGNORE_CASE)
private val HEVC_REGEX = Regex("""\b(hevc|x265|h[.\s]?265)\b""", RegexOption.IGNORE_CASE)
private val AVC_REGEX = Regex("""\b(avc|x264|h[.\s]?264)\b""", RegexOption.IGNORE_CASE)
private val AV1_REGEX = Regex("""\bav1\b""", RegexOption.IGNORE_CASE)
private val TENBIT_REGEX = Regex("""\b10[\s._-]?bits?\b|\bhi10p?\b""", RegexOption.IGNORE_CASE)
private val REMUX_REGEX = Regex("""\bremux\b""", RegexOption.IGNORE_CASE)
private val BLURAY_REGEX = Regex("""blu[\s-]?ray|\bbdrip\b|\bbrrip\b""", RegexOption.IGNORE_CASE)
private val WEBDL_REGEX = Regex("""web[\s._-]?dl|\bweb\b""", RegexOption.IGNORE_CASE)
private val WEBRIP_REGEX = Regex("""web[\s._-]?rip""", RegexOption.IGNORE_CASE)
private val HDTV_REGEX = Regex("""\bhdtv\b""", RegexOption.IGNORE_CASE)
private val CAM_REGEX = Regex("""\b(cam|hdcam|telesync|\bts\b|hdts)\b""", RegexOption.IGNORE_CASE)
private val MULTI_REGEX = Regex("""multi[\s._-]?(audio|lang)|dual[\s._-]?audio|\bmulti\b""", RegexOption.IGNORE_CASE)
private val BRACKET_REGEX = Regex("""[\[(]([^\])]{2,40})[\])]""")
private val SPLIT_REGEX = Regex("""[\s,/+&|._-]+""")

private data class LangDef(val name: String, val flag: String, val words: List<String>, val codes: List<String>)

private val LANGS = listOf(
    LangDef("English", "🇬🇧", listOf("english"), listOf("eng", "en")),
    LangDef("Hindi", "🇮🇳", listOf("hindi"), listOf("hin", "hi")),
    LangDef("Tamil", "🇮🇳", listOf("tamil"), listOf("tam", "ta")),
    LangDef("Telugu", "🇮🇳", listOf("telugu"), listOf("tel", "te")),
    LangDef("Malayalam", "🇮🇳", listOf("malayalam"), listOf("mal", "ml")),
    LangDef("Kannada", "🇮🇳", listOf("kannada"), listOf("kan", "kn")),
    LangDef("Bengali", "🇧🇩", listOf("bengali", "bangla"), listOf("ben", "bn")),
    LangDef("Punjabi", "🇮🇳", listOf("punjabi"), listOf("pun", "pa")),
    LangDef("Marathi", "🇮🇳", listOf("marathi"), listOf("mar", "mr")),
    LangDef("Spanish", "🇪🇸", listOf("spanish", "espanol", "latino", "esla", "latam"), listOf("spa", "es")),
    LangDef("French", "🇫🇷", listOf("french", "francais"), listOf("fre", "fra", "fr")),
    LangDef("German", "🇩🇪", listOf("german", "deutsch"), listOf("ger", "deu", "de")),
    LangDef("Italian", "🇮🇹", listOf("italian", "italiano"), listOf("ita", "it")),
    LangDef("Portuguese", "🇧🇷", listOf("portuguese", "ptbr", "pt-br", "brazilian"), listOf("por", "pt")),
    LangDef("Russian", "🇷🇺", listOf("russian"), listOf("rus", "ru")),
    LangDef("Japanese", "🇯🇵", listOf("japanese"), listOf("jpn", "jap", "ja")),
    LangDef("Korean", "🇰🇷", listOf("korean"), listOf("kor", "ko")),
    LangDef("Chinese", "🇨🇳", listOf("chinese", "mandarin", "cantonese"), listOf("chi", "zho", "zh")),
    LangDef("Arabic", "🇸🇦", listOf("arabic"), listOf("ara", "ar")),
    LangDef("Turkish", "🇹🇷", listOf("turkish"), listOf("tur", "tr")),
    LangDef("Thai", "🇹🇭", listOf("thai"), listOf("tha", "th")),
    LangDef("Indonesian", "🇮🇩", listOf("indonesian"), listOf("ind", "id")),
    LangDef("Polish", "🇵🇱", listOf("polish"), listOf("pol", "pl")),
    LangDef("Dutch", "🇳🇱", listOf("dutch"), listOf("dut", "nld", "nl")),
)

private val WORD_REGEXES: List<Pair<LangDef, Regex>> = LANGS.map { def ->
    def to Regex("""\b(${def.words.joinToString("|")})\b""", RegexOption.IGNORE_CASE)
}

internal fun detectStreamLanguages(text: String): List<StreamLanguage> {
    val found = LinkedHashMap<String, Pair<Int, LangDef>>()
    for ((def, regex) in WORD_REGEXES) {
        val m = regex.find(text) ?: continue
        found[def.name] = m.range.first to def
    }
    // Short codes are only trusted inside brackets such as [Hin-Eng] or (hi, en).
    for (bracket in BRACKET_REGEX.findAll(text)) {
        val parts = bracket.groupValues[1].split(SPLIT_REGEX).filter { it.isNotBlank() }
        if (parts.size < 2 && parts.firstOrNull()?.length != 3) continue
        var offset = bracket.range.first
        for (part in parts) {
            val lower = part.lowercase()
            val def = LANGS.firstOrNull { lower in it.codes && (lower.length == 3 || parts.size >= 2) }
            if (def != null && def.name !in found) found[def.name] = offset to def
            offset++
        }
    }
    return found.values.sortedBy { it.first }.map { StreamLanguage(it.second.name, it.second.flag) }
}

private fun formatSize(bytes: Long): String {
    val gib = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    return if (gib >= 1.0) {
        "${round(gib * 10.0) / 10.0} GB"
    } else {
        "${round(bytes.toDouble() / (1024.0 * 1024.0)).toInt()} MB"
    }
}

internal fun parseStreamInfo(texts: List<String?>, videoSizeBytes: Long? = null): ParsedStreamInfo {
    val text = texts.filterNotNull().filter { it.isNotBlank() }.joinToString(" \n ")
    if (text.isBlank() && videoSizeBytes == null) return ParsedStreamInfo()

    val height = RES_REGEX.find(text)?.groupValues?.get(1)?.toIntOrNull()
        ?: if (UHD_REGEX.containsMatchIn(text)) 2160 else 0
    val qualityLabel = when {
        height == 0 -> null
        height >= 2160 -> "4K"
        else -> "${height}p"
    }
    val sizeLabel = videoSizeBytes?.takeIf { it > 0 }?.let(::formatSize)
        ?: SIZE_REGEX.find(text)?.let { m ->
            val unit = if (m.groupValues[2].startsWith("g", ignoreCase = true)) "GB" else "MB"
            "${m.groupValues[1].replace(',', '.')} $unit"
        }

    val hdr10Plus = HDR10P_REGEX.containsMatchIn(text)
    val dv = DV_REGEX.containsMatchIn(text)
    val hdr10 = !hdr10Plus && HDR10_REGEX.containsMatchIn(text)
    val hdr = !hdr10Plus && !hdr10 && HDR_REGEX.containsMatchIn(text)
    val atmos = ATMOS_REGEX.containsMatchIn(text)
    val dtsFormats = DTS_REGEX.findAll(text).map { match ->
        val raw = match.value
        val lower = raw.lowercase()
        when {
            lower.endsWith("x") && lower.length > 3 -> "DTS:X"
            "hd" in lower -> "DTS-HD"
            else -> "DTS"
        }
    }.toList()
    val dolbyFormats = DD_REGEX.findAll(text).map { match ->
        val raw = match.value
        val lower = raw.lowercase().replace(Regex("""[\s._-]+"""), "")
        when {
            lower.startsWith("truehd") -> "TrueHD"
            lower.startsWith("eac3") || lower.startsWith("dd+") || lower.startsWith("ddp") ||
                lower.endsWith("digital+") || lower.endsWith("digitalplus") -> "DD+"
            lower.startsWith("ac3") || lower.startsWith("dd") || lower.endsWith("digital") -> "DD"
            else -> raw.uppercase()
        }
    }.toList()
    val audioFormats = (dtsFormats + dolbyFormats).distinct()
    val channels = CHANNEL_REGEX.find(text)?.groupValues?.get(1)?.replace(Regex("""\s"""), ".")
        ?: CHANNEL_CH_REGEX.find(text)?.groupValues?.get(1)?.let { if (it == "8") "7.1" else "5.1" }
    val codec = when {
        AV1_REGEX.containsMatchIn(text) -> "AV1"
        HEVC_REGEX.containsMatchIn(text) -> "HEVC"
        AVC_REGEX.containsMatchIn(text) -> "H.264"
        else -> null
    }
    val sourceTypes = buildList {
        if (REMUX_REGEX.containsMatchIn(text)) add("REMUX")
        if (BLURAY_REGEX.containsMatchIn(text)) add("BluRay")
        if (WEBRIP_REGEX.containsMatchIn(text)) add("WEBRip")
        else if (WEBDL_REGEX.containsMatchIn(text)) add("WEB-DL")
        if (HDTV_REGEX.containsMatchIn(text)) add("HDTV")
        if (CAM_REGEX.containsMatchIn(text)) add("CAM")
    }
    val langs = detectStreamLanguages(text)
    val multi = MULTI_REGEX.containsMatchIn(text)

    val key = text.lowercase()
        .replace(RES_REGEX, " ")
        .replace(UHD_REGEX, " ")
        .replace(SIZE_REGEX, " ")
        .replace(Regex("""[^a-z0-9]+"""), " ")
        .trim()

    return ParsedStreamInfo(
        qualityHeight = height,
        qualityLabel = qualityLabel,
        sizeLabel = sizeLabel,
        hdr = hdr,
        hdr10 = hdr10,
        hdr10Plus = hdr10Plus,
        dolbyVision = dv,
        atmos = atmos,
        audioFormat = audioFormats.firstOrNull(),
        audioFormats = audioFormats,
        channels = channels,
        codec = codec,
        tenBit = TENBIT_REGEX.containsMatchIn(text),
        imax = IMAX_REGEX.containsMatchIn(text),
        sourceType = sourceTypes.firstOrNull(),
        sourceTypes = sourceTypes,
        languages = langs,
        multiAudio = multi,
        audioKind = SourceLanguage.parse(text).kind,
        releaseKey = key,
    )
}

internal fun StreamItem.parsedInfo(): ParsedStreamInfo = parseStreamInfo(
    texts = listOf(name, title, description, behaviorHints.filename ?: clientResolve?.filename),
    videoSizeBytes = behaviorHints.videoSize,
)

/** One row of the Quality menu: every loaded source at one resolution class. */
internal data class QualityGroup(
    /** Resolution class: 2160, 1440, 1080, 720, 480, or 0 for SD / unknown. */
    val bucket: Int,
    /** The stream shown for the row and played when it is tapped. */
    val best: StreamItem,
    val bestInfo: ParsedStreamInfo,
    val sourceCount: Int,
    val isCurrent: Boolean,
)

internal fun qualityBucketOf(height: Int): Int = when {
    height >= 2160 -> 2160
    height >= 1440 -> 1440
    height >= 1080 -> 1080
    height >= 720 -> 720
    height >= 480 -> 480
    else -> 0
}

/** Short tile label for a resolution class, the same style the source rows use. */
internal fun qualityBucketLabel(bucket: Int): String = when (bucket) {
    2160 -> "4K"
    0 -> "SD"
    else -> "${bucket}p"
}

private val FHD_REGEX = Regex("""\bfhd\b""", RegexOption.IGNORE_CASE)
private val QHD_REGEX = Regex("""\b(qhd|2k)\b""", RegexOption.IGNORE_CASE)
private val HD_REGEX = Regex("""\bhd\b""", RegexOption.IGNORE_CASE)

/**
 * [parsedInfo], plus a height guessed from word labels (FHD, HD...) that live sports and other
 * addons use instead of a number. Only applied when the number-based parser found nothing.
 */
private fun StreamItem.parsedQualityInfo(): ParsedStreamInfo {
    val info = parsedInfo()
    if (info.qualityHeight > 0) return info
    val text = listOf(name, title, description).filterNotNull().joinToString(" ")
    val guessed = when {
        FHD_REGEX.containsMatchIn(text) -> 1080
        QHD_REGEX.containsMatchIn(text) -> 1440
        HD_REGEX.containsMatchIn(text) -> 720
        else -> return info
    }
    return info.copy(qualityHeight = guessed, qualityLabel = "${guessed}p")
}

private fun ParsedStreamInfo.sizeBytes(): Long {
    val m = sizeLabel?.let { SIZE_REGEX.find(it) } ?: return 0L
    val value = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return 0L
    val unit = if (m.groupValues[2].startsWith("g", ignoreCase = true)) 1024.0 * 1024.0 * 1024.0 else 1024.0 * 1024.0
    return (value * unit).toLong()
}

private fun ParsedStreamInfo.badgeCount(): Int =
    listOf(imax, dolbyVision, hdr10Plus, hdr10, hdr, atmos, tenBit, multiAudio).count { it } +
        audioFormats.size + sourceTypes.size + (if (channels != null) 1 else 0) + (if (codec != null) 1 else 0)

/**
 * Groups every playable source in [streams] (all add-ons) by resolution class, best class first,
 * one entry per class. The row's stream is chosen by, in order: same add-on as the playing one,
 * ready to play (direct link or cached debrid), larger size, then richer badges; earlier in the
 * list wins a tie. The playing stream represents its own class so the row describes what is on
 * screen.
 */
internal fun qualityGroupsOf(
    streams: List<StreamItem>,
    current: PlayerCurrentStream,
): List<QualityGroup> {
    val currentIndex = streams.currentPlayerStreamIndex(current)
    val currentStream = streams.getOrNull(currentIndex)
    val currentInfo = currentStream?.parsedQualityInfo()
        ?: parseStreamInfo(listOf(current.name, current.description, current.filename))
    val currentBucket = qualityBucketOf(currentInfo.qualityHeight)
    val currentAddon = currentStream?.addonId ?: current.addonId?.takeIf { it.isNotBlank() }

    val candidates = streams
        .filter { it.playableDirectUrl != null || it.hasPlayableSource }
        .map { it to it.parsedQualityInfo() }
    val ranked = compareByDescending<Pair<StreamItem, ParsedStreamInfo>> { (stream, _) ->
        currentAddon != null && stream.addonId == currentAddon
    }.thenByDescending { (stream, _) ->
        stream.playableDirectUrl != null || stream.isCachedDebridTorrentStream
    }.thenByDescending { (stream, info) ->
        info.sizeBytes().takeIf { it > 0 } ?: stream.debridCacheStatus?.cachedSize ?: 0L
    }.thenByDescending { (stream, info) ->
        info.badgeCount() + stream.badges.size
    }

    return candidates
        .groupBy { qualityBucketOf(it.second.qualityHeight) }
        .map { (bucket, list) ->
            val isCurrent = bucket == currentBucket
            val playing = if (isCurrent && currentStream != null) list.firstOrNull { it.first === currentStream } else null
            val best = playing ?: list.sortedWith(ranked).first()
            QualityGroup(
                bucket = bucket,
                best = best.first,
                bestInfo = best.second,
                sourceCount = list.size,
                isCurrent = isCurrent,
            )
        }
        .sortedByDescending { it.bucket }
}

private val GENERIC_TRACK_LABEL = Regex("""^\s*(audio|track|stream)?\s*#?\s*\d*\s*$""", RegexOption.IGNORE_CASE)

/**
 * Adds the languages named by the selected link to the engine's audio tracks. A track with a
 * real language keeps it; a generic or unlabeled track takes the parsed language at the same
 * position (links list languages in track order).
 */
internal fun labelAudioTracksWithLanguages(
    tracks: List<AudioTrack>,
    languages: List<StreamLanguage>,
): List<AudioTrack> {
    if (tracks.isEmpty() || languages.isEmpty()) return tracks
    return tracks.mapIndexed { position, track ->
        val lang = languages.getOrNull(position) ?: return@mapIndexed track
        val hasLanguage = !track.language.isNullOrBlank() && !track.language.equals("und", ignoreCase = true)
        val label = track.label
        when {
            label.isBlank() || GENERIC_TRACK_LABEL.matches(label) -> track.copy(label = "${lang.flag} ${lang.name}")
            !hasLanguage && !label.contains(lang.name, ignoreCase = true) ->
                track.copy(label = "$label · ${lang.flag} ${lang.name}")
            else -> track
        }
    }
}
