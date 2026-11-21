package com.nuvio.app.features.search

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.network.NetworkCondition
import com.nuvio.app.core.ui.NuvioDropdownChip
import com.nuvio.app.core.ui.NuvioDropdownOption
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.NuvioNetworkOfflineCard
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.PosterGridSkeletonRow
import com.nuvio.app.features.home.components.HomeEmptyStateCard
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.discoverContent(
    state: DiscoverUiState,
    isSourceLoading: Boolean,
    columns: Int,
    networkCondition: NetworkCondition,
    onTypeSelected: (String) -> Unit,
    onCatalogSelected: (String) -> Unit,
    onGenreSelected: (String?) -> Unit,
    onFiltersChange: (((DiscoverFilters) -> DiscoverFilters) -> Unit)? = null,
    onClearFilters: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    watchedKeys: Set<String> = emptySet(),
    fullyWatchedSeriesKeys: Set<String> = emptySet(),
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
) {
    item(key = "discover_header") {
        DiscoverPageHeader(
            state = state,
            onTypeSelected = onTypeSelected,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
    // Addon catalogs need their own catalog / genre pickers; the built-in TMDB source is fully covered by the filter bar.
    if (!state.isTmdbSelected || state.catalogOptions.size > 1) {
        item(key = "discover_catalog_row") {
            DiscoverFilterRow(
                state = state,
                modifier = Modifier.padding(horizontal = 16.dp),
                onCatalogSelected = onCatalogSelected,
                onGenreSelected = onGenreSelected,
            )
        }
    }
    if (state.isTmdbSelected && onFiltersChange != null) {
        item(key = "discover_filters") {
            DiscoverTmdbFilters(
                state = state,
                onFiltersChange = onFiltersChange,
                onClearAll = { onClearFilters?.invoke() },
            )
        }
    }
    state.selectedCatalog?.takeIf { !it.isTmdb }?.let { selectedCatalog ->
        item {
            Text(
                text = stringResource(
                    Res.string.discover_catalog_context,
                    selectedCatalog.addonName,
                    selectedCatalog.type.displayTypeLabel(),
                ),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    when {
        (state.isLoading || isSourceLoading) && state.items.isEmpty() -> {
            items(3) {
                PosterGridSkeletonRow(
                    columns = columns,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        state.items.isEmpty() -> {
            item {
                DiscoverEmptyStateCard(
                    reason = state.emptyStateReason,
                    errorMessage = state.errorMessage,
                    networkCondition = networkCondition,
                    onRetry = onRetry,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        else -> {
            items(count = (state.items.size + columns - 1) / columns) { rowIndex ->
                val firstIndex = rowIndex * columns
                DiscoverPosterRow(
                    items = state.items.subList(firstIndex, minOf(firstIndex + columns, state.items.size)),
                    columns = columns,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    watchedKeys = watchedKeys,
                    fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                    onPosterClick = onPosterClick,
                    onPosterLongClick = onPosterLongClick,
                )
            }
            if (state.isLoading) {
                item {
                    CatalogLoadingFooter(
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

/**
 * Page header: large "Discover" title, a one-line subtitle and the media type switch (Movies / Series / Anime) as a
 * segmented pill directly underneath.
 */
@Composable
private fun DiscoverPageHeader(
    state: DiscoverUiState,
    onTypeSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s14),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = NuvioTokens.Space.s4, height = NuvioTokens.Space.s28)
                        .clip(tokens.shapes.chip)
                        .background(tokens.colors.accent),
                )
                Text(
                    text = stringResource(Res.string.compose_search_discover_title),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = NuvioTokens.Type.displaySm,
                        lineHeight = NuvioTokens.LineHeight.displaySm,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = NuvioTokens.LetterSpacing.headline,
                    ),
                    color = tokens.colors.textPrimary,
                    maxLines = 1,
                )
            }
            Text(
                text = stringResource(Res.string.discover_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.typeOptions.isNotEmpty()) {
            DiscoverTypeSwitch(
                types = state.typeOptions,
                selectedType = state.selectedType,
                onTypeSelected = onTypeSelected,
            )
        }
    }
}

/** Segmented pill control for the media type; scrolls horizontally when addons add many extra types. */
@Composable
private fun DiscoverTypeSwitch(
    types: List<String>,
    selectedType: String?,
    onTypeSelected: (String) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val shape = tokens.shapes.chip
    val fits = types.size <= 4

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tokens.colors.surface)
            .border(tokens.borders.thin, tokens.colors.borderDefault, shape)
            .then(if (fits) Modifier else Modifier.horizontalScroll(rememberScrollState()))
            .padding(NuvioTokens.Space.s4),
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        types.forEach { type ->
            val selected = type == selectedType
            val background by animateColorAsState(
                targetValue = if (selected) tokens.colors.accent else Color.Transparent,
                label = "discoverTypeBackground",
            )
            val contentColor by animateColorAsState(
                targetValue = if (selected) tokens.colors.onAccent else tokens.colors.textSecondary,
                label = "discoverTypeContent",
            )
            Row(
                modifier = (if (fits) Modifier.weight(1f) else Modifier)
                    .clip(shape)
                    .background(background)
                    .clickable { onTypeSelected(type) }
                    .padding(horizontal = NuvioTokens.Space.s14, vertical = NuvioTokens.Space.s10),
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                type.typeIcon()?.let { icon ->
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(tokens.icons.sm + NuvioTokens.Space.s2),
                        tint = contentColor,
                    )
                }
                Text(
                    text = type.displayTypeLabel(),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    ),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun String.typeIcon(): ImageVector? =
    when (lowercase()) {
        "movie" -> Icons.Rounded.Movie
        "series", "tv" -> Icons.Rounded.Tv
        "anime" -> Icons.Rounded.AutoAwesome
        else -> null
    }

/** Catalog / genre pickers for addon-provided catalogs (the media type lives in the page header). */
@Composable
private fun DiscoverFilterRow(
    state: DiscoverUiState,
    onCatalogSelected: (String) -> Unit,
    onGenreSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NuvioDropdownChip(
            title = stringResource(Res.string.discover_select_catalog),
            label = state.selectedCatalog?.let { it.displayCatalogName() } ?: stringResource(Res.string.discover_catalog),
            selectedKey = state.selectedCatalogKey,
            options = state.catalogOptions.map { option -> NuvioDropdownOption(key = option.key, label = option.displayCatalogName()) },
            enabled = state.catalogOptions.isNotEmpty(),
            onSelected = { onCatalogSelected(it.key) },
        )

        val selectedCatalog = state.selectedCatalog
        // TMDB catalogs bring their own multi-select genre filter, shown in the filter bar below.
        if (selectedCatalog?.isTmdb != true) {
            val genreOptions = buildList {
                if (selectedCatalog?.genreRequired != true) {
                    add(NuvioDropdownOption(key = "", label = stringResource(Res.string.discover_all_genres)))
                }
                addAll(state.genreOptions.map { genre -> NuvioDropdownOption(key = genre, label = genre) })
            }
            NuvioDropdownChip(
                title = stringResource(Res.string.discover_select_genre),
                label = state.selectedGenre ?: stringResource(Res.string.discover_all_genres),
                selectedKey = state.selectedGenre ?: "",
                options = genreOptions,
                enabled = genreOptions.size > 1 || selectedCatalog?.genreRequired == true,
                onSelected = { option ->
                    onGenreSelected(option.key.ifBlank { null })
                },
            )
        }
    }
}

@Composable
private fun DiscoverCatalogOption.displayCatalogName(): String =
    if (isTmdb) stringResource(Res.string.discover_tmdb_catalog) else catalogName

@Composable
private fun CatalogLoadingFooter(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        NuvioLoadingIndicator(
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun DiscoverEmptyStateCard(
    reason: DiscoverEmptyStateReason?,
    errorMessage: String?,
    networkCondition: NetworkCondition,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (
        reason == DiscoverEmptyStateReason.RequestFailed &&
        (networkCondition == NetworkCondition.NoInternet || networkCondition == NetworkCondition.ServersUnreachable)
    ) {
        NuvioNetworkOfflineCard(
            condition = networkCondition,
            modifier = modifier,
            onRetry = onRetry,
        )
        return
    }

    val title: String
    val message: String

    when (reason) {
        DiscoverEmptyStateReason.NoActiveAddons -> {
            title = stringResource(Res.string.home_empty_no_sources_title)
            message = stringResource(Res.string.search_empty_no_sources_message)
        }

        DiscoverEmptyStateReason.NoDiscoverCatalogs -> {
            title = stringResource(Res.string.discover_empty_no_catalogs_title)
            message = stringResource(Res.string.discover_empty_no_catalogs_message)
        }

        DiscoverEmptyStateReason.RequestFailed -> {
            title = stringResource(Res.string.discover_empty_load_failed_title)
            message = errorMessage ?: stringResource(Res.string.discover_empty_load_failed_message)
        }

        DiscoverEmptyStateReason.NoResults, null -> {
            title = stringResource(Res.string.discover_empty_no_results_title)
            message = stringResource(Res.string.discover_empty_no_results_message)
        }
    }

    HomeEmptyStateCard(
        modifier = modifier,
        title = title,
        message = message,
        actionLabel = if (reason == DiscoverEmptyStateReason.RequestFailed) {
            stringResource(Res.string.action_retry)
        } else {
            null
        },
        onActionClick = if (reason == DiscoverEmptyStateReason.RequestFailed) onRetry else null,
    )
}

@Composable
private fun String.displayTypeLabel(): String =
    when (lowercase()) {
        "movie" -> stringResource(Res.string.media_movies)
        "series" -> stringResource(Res.string.media_series)
        "anime" -> stringResource(Res.string.media_anime)
        "channel" -> stringResource(Res.string.media_channels)
        "tv" -> stringResource(Res.string.media_tv)
        else -> replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
