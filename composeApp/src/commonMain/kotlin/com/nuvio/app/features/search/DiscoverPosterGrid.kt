package com.nuvio.app.features.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.ui.NuvioCardDepthSurface
import com.nuvio.app.core.ui.NuvioPosterWatchedOverlay
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioCardDepth
import com.nuvio.app.core.ui.posterCardClickable
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.watching.application.WatchingState

/**
 * One row of the Discover results grid. Same poster geometry as the shared poster grid, but the caption is a title
 * followed by a compact "star rating, year" line so results can be scanned quickly.
 */
@Composable
internal fun DiscoverPosterRow(
    items: List<MetaPreview>,
    columns: Int,
    modifier: Modifier = Modifier,
    watchedKeys: Set<String> = emptySet(),
    fullyWatchedSeriesKeys: Set<String> = emptySet(),
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
) {
    val posterCardStyle = rememberPosterCardStyleUiState()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        items.forEach { item ->
            DiscoverPosterTile(
                item = item,
                cornerRadiusDp = posterCardStyle.cornerRadiusDp,
                hideLabels = posterCardStyle.hideLabelsEnabled,
                modifier = Modifier.weight(1f),
                isWatched = WatchingState.isPosterWatched(
                    watchedKeys = watchedKeys,
                    item = item,
                    fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                ),
                onClick = onPosterClick?.let { { it(item) } },
                onLongClick = onPosterLongClick?.let { { it(item) } },
            )
        }
        repeat(columns - items.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiscoverPosterTile(
    item: MetaPreview,
    cornerRadiusDp: Int,
    hideLabels: Boolean,
    modifier: Modifier = Modifier,
    isWatched: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val tokens = MaterialTheme.nuvio
    val cardShape = RoundedCornerShape(cornerRadiusDp.dp)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(item.posterShape.discoverAspectRatio())
                .clip(cardShape)
                .background(tokens.colors.surfaceCard)
                .border(tokens.borders.thin, tokens.colors.borderSubtle, cardShape)
                .nuvioCardDepth(
                    shape = cardShape,
                    surface = NuvioCardDepthSurface.Posters,
                )
                .posterCardClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    zoomImageUrl = item.poster,
                    zoomCornerRadius = cornerRadiusDp.dp,
                ),
        ) {
            if (item.poster != null) {
                val platformContext = coil3.compose.LocalPlatformContext.current
                val hasFallback = !item.rawPosterUrl.isNullOrBlank() && item.rawPosterUrl != item.poster
                val imageModel = remember(item.poster, item.rawPosterUrl, platformContext) {
                    if (hasFallback) {
                        coil3.request.ImageRequest.Builder(platformContext)
                            .data(item.poster)
                            .memoryCacheKeyExtras(
                                mapOf(com.nuvio.app.core.poster.CustomPosterFallbackInterceptor.FALLBACK_URL_KEY to item.rawPosterUrl!!),
                            )
                            .build()
                    } else {
                        item.poster
                    }
                }
                AsyncImage(
                    model = imageModel,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            NuvioPosterWatchedOverlay(isWatched = isWatched)
        }
        if (!hideLabels) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = tokens.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            DiscoverPosterMeta(item = item)
        }
    }
}

/** "star 7.8 - 2024" line under the title; either part is omitted when unknown. */
@Composable
private fun DiscoverPosterMeta(item: MetaPreview) {
    val tokens = MaterialTheme.nuvio
    val rating = (item.imdbRating ?: item.tmdbRating)?.takeIf { it.isNotBlank() }
    val year = item.releaseInfo?.takeIf { it.isNotBlank() }?.let { formatReleaseDateForDisplay(it) }
    if (rating == null && year == null) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rating != null) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                modifier = Modifier.size(NuvioTokens.Icon.sm),
                tint = tokens.colors.warning,
            )
            Text(
                text = rating,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = tokens.colors.textSecondary,
                maxLines = 1,
            )
        }
        if (rating != null && year != null) {
            Text(
                text = "•",
                style = MaterialTheme.typography.labelMedium,
                color = tokens.colors.textMuted,
                maxLines = 1,
            )
        }
        if (year != null) {
            Text(
                text = year,
                style = MaterialTheme.typography.labelMedium,
                color = tokens.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun PosterShape.discoverAspectRatio(): Float =
    when (this) {
        PosterShape.Poster -> 0.68f
        PosterShape.Square -> 1f
        PosterShape.Landscape -> 1.78f
    }
