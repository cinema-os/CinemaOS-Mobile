package com.nuvio.app.features.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.nuvio
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.brand_dolby_mark
import nuvio.composeapp.generated.resources.brand_dolby_vision
import nuvio.composeapp.generated.resources.brand_hdr10plus
import nuvio.composeapp.generated.resources.cinemaos_player_source_download
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import com.nuvio.app.features.streams.StreamAudioKind
import com.nuvio.app.features.streams.StreamItem

private enum class ChipTone { Quality, Accent, Neutral }

private val BadgeHeight = 22.dp
private val BadgeShape = RoundedCornerShape(5.dp)

/** Shared badge frame: fixed height, rounded rectangle, theme-tinted fill and outline. */
@Composable
private fun MediaBadge(
    tone: ChipTone,
    content: @Composable RowScope.(Color) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val accent = MaterialTheme.colorScheme.primary
    val (bg, border, contentColor) = when (tone) {
        ChipTone.Quality -> Triple(accent, accent, MaterialTheme.colorScheme.onPrimary)
        ChipTone.Accent -> Triple(accent.copy(alpha = 0.14f), accent.copy(alpha = 0.60f), tokens.colors.textPrimary)
        ChipTone.Neutral -> Triple(Color.Transparent, tokens.colors.textSecondary.copy(alpha = 0.55f), tokens.colors.textPrimary)
    }
    Row(
        modifier = Modifier
            .height(BadgeHeight)
            .clip(BadgeShape)
            .background(bg)
            .border(BorderStroke(1.2.dp, border), BadgeShape)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        content(contentColor)
    }
}

@Composable
private fun BadgeText(
    text: String,
    color: Color,
    fontSize: TextUnit = 10.5.sp,
    weight: FontWeight = FontWeight.Bold,
    tracking: TextUnit = 0.4.sp,
) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        lineHeight = fontSize,
        fontWeight = weight,
        letterSpacing = tracking,
        maxLines = 1,
        softWrap = false,
    )
}

/** Outlined (or filled) label badge such as 4K, HEVC, REMUX or WEB-DL. */
@Composable
private fun LabelBadge(text: String, tone: ChipTone = ChipTone.Neutral) {
    MediaBadge(tone) { color -> BadgeText(text, color) }
}

/** Dolby-style badge: the double-D mark followed by a spaced wordmark (VISION, ATMOS, TRUEHD, DIGITAL+). */
@Composable
private fun DolbyBadge(word: String) {
    MediaBadge(ChipTone.Accent) { color ->
        if (word == "VISION") {
            Icon(painterResource(Res.drawable.brand_dolby_vision), contentDescription = "Dolby Vision", tint = color,
                modifier = Modifier.width(88.dp).height(14.dp))
        } else {
            Icon(
                painter = painterResource(Res.drawable.brand_dolby_mark),
                contentDescription = null,
                tint = color,
                modifier = Modifier.width(22.dp).height(15.dp),
            )
            BadgeText(word, color, fontSize = 9.5.sp, weight = FontWeight.Black, tracking = 1.sp)
        }
    }
}

/** HDR10+ as a bold filled badge with an oversized plus. */
@Composable
private fun Hdr10PlusBadge() {
    MediaBadge(ChipTone.Quality) { color ->
        Icon(painterResource(Res.drawable.brand_hdr10plus), contentDescription = "HDR10+", tint = color,
            modifier = Modifier.width(48.dp).height(17.dp))
    }
}

/** DTS family wordmark: heavy DTS with a small suffix (HD, :X). */
@Composable
private fun DtsBadge(format: String) {
    val suffix = when (format) {
        "DTS-HD" -> "HD"
        "DTS:X" -> ":X"
        else -> null
    }
    MediaBadge(ChipTone.Neutral) { color ->
        BadgeText("DTS", color, fontSize = 11.5.sp, weight = FontWeight.Black, tracking = 0.8.sp)
        if (suffix != null) {
            BadgeText(suffix, color.copy(alpha = 0.85f), fontSize = 9.5.sp, weight = FontWeight.Bold, tracking = 0.4.sp)
        }
    }
}

@Composable
private fun ChannelsBadge(layout: String) {
    MediaBadge(ChipTone.Neutral) { color ->
        Icon(
            imageVector = StreamInfoIcons.Speaker,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp),
        )
        BadgeText(layout, color)
    }
}

/** One badge for each thing parsed from a stream, laid out in a wrapping row. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StreamInfoChipRow(info: ParsedStreamInfo, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        info.qualityLabel?.let { LabelBadge(it, ChipTone.Quality) }
        info.sizeLabel?.let { LabelBadge(it) }
        if (info.imax) LabelBadge("IMAX", ChipTone.Accent)
        if (info.dolbyVision) DolbyBadge("VISION")
        if (info.hdr10Plus) Hdr10PlusBadge()
        if (info.hdr10) LabelBadge("HDR10", ChipTone.Accent)
        if (info.hdr) LabelBadge("HDR", ChipTone.Accent)
        if (info.atmos) DolbyBadge("ATMOS")
        info.audioFormats.forEach { audio ->
            when (audio) {
                "TrueHD" -> DolbyBadge("TRUEHD")
                "DD+" -> DolbyBadge("DIGITAL+")
                "DD" -> DolbyBadge("DIGITAL")
                "DTS", "DTS-HD", "DTS:X" -> DtsBadge(audio)
                else -> LabelBadge(audio)
            }
        }
        info.channels?.let { ChannelsBadge(it) }
        info.codec?.let { LabelBadge(it) }
        if (info.tenBit) LabelBadge("10-BIT")
        info.sourceTypes.forEach { LabelBadge(it) }
        if (info.languages.isNotEmpty()) {
            info.languages.take(4).forEach { lang ->
                LabelBadge("${lang.flag} ${lang.name}")
            }
            if (info.languages.size > 4) LabelBadge("+${info.languages.size - 4}")
        } else if (info.multiAudio) {
            MediaBadge(ChipTone.Neutral) { color ->
                Icon(
                    imageVector = StreamInfoIcons.Language,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(12.dp),
                )
                BadgeText("MULTI AUDIO", color)
            }
        }
        when (info.audioKind) {
            StreamAudioKind.DUB -> LabelBadge("DUB", ChipTone.Accent)
            StreamAudioKind.SUB -> LabelBadge("SUB", ChipTone.Accent)
            StreamAudioKind.ORIGINAL -> LabelBadge("ORIGINAL AUDIO")
            else -> Unit
        }
    }
}

private val TrailingInfoWidth = 64.dp
private val DownloadButtonSize = 40.dp

/**
 * One link in the player's sources list. Tapping the row plays it; the trailing area shows the
 * resolution and size in a fixed-width column (so they line up row to row) and, when given, a
 * separate download button that does not trigger playback.
 */
@Composable
internal fun PlayerSourceCard(
    stream: StreamItem,
    enabled: Boolean,
    isCurrent: Boolean,
    currentLabel: String?,
    partyLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDownload: (() -> Unit)? = null,
) {
    val tokens = MaterialTheme.nuvio
    val info = remember(stream) { stream.parsedInfo() }
    val shape = RoundedCornerShape(14.dp)
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(shape)
            .background(if (isCurrent) accent.copy(alpha = 0.12f) else tokens.colors.surfaceCard)
            .border(
                BorderStroke(1.dp, if (isCurrent) accent.copy(alpha = 0.55f) else tokens.colors.borderSubtle),
                shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stream.streamLabel,
                    modifier = Modifier.weight(1f, fill = false),
                    color = if (enabled) tokens.colors.textPrimary else tokens.colors.textMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isCurrent && !currentLabel.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = currentLabel,
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                if (partyLabel != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = partyLabel,
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val subtitleLines = stream.streamSubtitle.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
            subtitleLines.firstOrNull()?.let { subtitle ->
                Text(
                    text = subtitle,
                    color = tokens.colors.textPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (subtitleLines.size > 1) {
                Text(
                    text = subtitleLines.drop(1).joinToString(" "),
                    color = tokens.colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Quality and size live in the trailing column, so the badges here are format facts only.
            val formatInfo = remember(info) { info.copy(qualityLabel = null, sizeLabel = null) }
            StreamInfoChipRow(info = formatInfo, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.width(10.dp))
        SourceTrailingInfo(
            qualityLabel = info.qualityLabel,
            sizeLabel = info.sizeLabel,
            highlighted = isCurrent,
        )
        if (onDownload != null) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(tokens.colors.borderSubtle),
            )
            Spacer(Modifier.width(6.dp))
            if (enabled) {
                SourceDownloadButton(onClick = onDownload)
            } else {
                Spacer(Modifier.size(DownloadButtonSize))
            }
        }
    }
}

/** Resolution pill over the file size, centred in a fixed-width column. */
@Composable
private fun SourceTrailingInfo(
    qualityLabel: String?,
    sizeLabel: String?,
    highlighted: Boolean,
) {
    val tokens = MaterialTheme.nuvio
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier.width(TrailingInfoWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        if (qualityLabel != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (highlighted) accent else accent.copy(alpha = 0.16f))
                    .border(BorderStroke(1.dp, accent.copy(alpha = 0.6f)), RoundedCornerShape(7.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = qualityLabel,
                    color = if (highlighted) MaterialTheme.colorScheme.onPrimary else tokens.colors.textPrimary,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.3.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        if (sizeLabel != null) {
            Text(
                text = sizeLabel,
                color = tokens.colors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Round secondary action: its own touch target, so it never starts playback. */
@Composable
private fun SourceDownloadButton(onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier = Modifier
            .size(DownloadButtonSize)
            .clip(CircleShape)
            .background(tokens.colors.textPrimary.copy(alpha = 0.08f))
            .border(BorderStroke(1.dp, tokens.colors.borderSubtle), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Download,
            contentDescription = stringResource(Res.string.cinemaos_player_source_download),
            tint = tokens.colors.textPrimary,
            modifier = Modifier.size(20.dp),
        )
    }
}
