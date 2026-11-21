package com.nuvio.app.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.core.ui.NuvioBottomSheetActionRow
import com.nuvio.app.core.ui.NuvioBottomSheetDivider
import com.nuvio.app.core.ui.NuvioDropdownChip
import com.nuvio.app.core.ui.NuvioDropdownOption
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

private const val FIRST_DECADE = 1950
private const val RECENT_YEAR_SPAN = 30

/**
 * TMDB discover filter bar: genre (multi), sort, year/decade, rating, runtime, language, country, streaming provider,
 * network (series) and age rating (movies), followed by removable active-filter chips with a clear-all action.
 */
@Composable
internal fun DiscoverTmdbFilters(
    state: DiscoverUiState,
    onFiltersChange: ((DiscoverFilters) -> DiscoverFilters) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val filters = state.filters
    val options = state.filterOptions
    val isTv = state.isTmdbTv
    val genres = if (isTv) options.tvGenres else options.movieGenres
    val currentYear = remember { CurrentDateProvider.todayIsoDate().take(4).toIntOrNull() ?: 2025 }

    val anyKey = ""
    val sortOptions = DiscoverSort.entries
        .filter { !isTv || it != DiscoverSort.Revenue }
        .map { NuvioDropdownOption(key = it.name, label = it.label()) }
    val yearOptions = buildList {
        add(NuvioDropdownOption(anyKey, stringResource(Res.string.discover_year_any)))
        var decade = currentYear / 10 * 10
        while (decade >= FIRST_DECADE) {
            add(NuvioDropdownOption(discoverDecadeKey(decade), "${decade}s"))
            decade -= 10
        }
        for (year in currentYear downTo currentYear - RECENT_YEAR_SPAN) {
            add(NuvioDropdownOption(discoverYearKey(year), year.toString()))
        }
    }
    val ratingOptions = buildList {
        add(NuvioDropdownOption(anyKey, stringResource(Res.string.discover_rating_any)))
        DiscoverRatingOptions.forEach { rating ->
            add(NuvioDropdownOption(rating.toString(), stringResource(Res.string.discover_rating_min, rating.toString())))
        }
    }
    val runtimeOptions = buildList {
        add(NuvioDropdownOption(anyKey, stringResource(Res.string.discover_runtime_any)))
        DiscoverRuntime.entries.forEach { add(NuvioDropdownOption(it.name, it.label())) }
    }
    val languageOptions = listOf(NuvioDropdownOption(anyKey, stringResource(Res.string.discover_language_any))) +
        options.languages.map { NuvioDropdownOption(it.key, it.label) }
    val countryOptions = listOf(NuvioDropdownOption(anyKey, stringResource(Res.string.discover_country_any))) +
        options.countries.map { NuvioDropdownOption(it.key, it.label) }
    val providerOptions = listOf(NuvioDropdownOption(anyKey, stringResource(Res.string.discover_provider_any))) +
        DiscoverProviderOptions.map { NuvioDropdownOption(it.key, it.label) }
    val networkOptions = listOf(NuvioDropdownOption(anyKey, stringResource(Res.string.discover_network_any))) +
        DiscoverNetworkOptions.map { NuvioDropdownOption(it.key, it.label) }
    val certificationOptions = listOf(NuvioDropdownOption(anyKey, stringResource(Res.string.discover_certification_any))) +
        DiscoverCertificationOptions.map { NuvioDropdownOption(it, it) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DiscoverGenreMultiChip(
                title = stringResource(Res.string.discover_filter_genre),
                label = when (filters.genreIds.size) {
                    0 -> stringResource(Res.string.discover_filter_genre)
                    1 -> genres.firstOrNull { it.id in filters.genreIds }?.name
                        ?: stringResource(Res.string.discover_filter_genre)
                    else -> stringResource(Res.string.discover_genres_count, filters.genreIds.size)
                },
                genres = genres,
                selectedIds = filters.genreIds,
                onToggle = { id ->
                    onFiltersChange { current ->
                        current.copy(
                            genreIds = if (id in current.genreIds) current.genreIds - id else current.genreIds + id,
                        )
                    }
                },
                onClear = { onFiltersChange { it.copy(genreIds = emptySet()) } },
            )
            NuvioDropdownChip(
                title = stringResource(Res.string.discover_filter_sort),
                label = filters.sort.label(),
                selectedKey = filters.sort.name,
                options = sortOptions,
                onSelected = { option ->
                    onFiltersChange { it.copy(sort = DiscoverSort.valueOf(option.key)) }
                },
            )
            NuvioDropdownChip(
                title = stringResource(Res.string.discover_filter_year),
                label = filters.yearKey?.yearLabel() ?: stringResource(Res.string.discover_filter_year),
                selectedKey = filters.yearKey ?: anyKey,
                options = yearOptions,
                onSelected = { option -> onFiltersChange { it.copy(yearKey = option.key.ifBlank { null }) } },
            )
            NuvioDropdownChip(
                title = stringResource(Res.string.discover_filter_rating),
                label = filters.minRating?.let { stringResource(Res.string.discover_rating_min, it.toString()) }
                    ?: stringResource(Res.string.discover_filter_rating),
                selectedKey = filters.minRating?.toString() ?: anyKey,
                options = ratingOptions,
                onSelected = { option -> onFiltersChange { it.copy(minRating = option.key.toIntOrNull()) } },
            )
            NuvioDropdownChip(
                title = stringResource(Res.string.discover_filter_runtime),
                label = filters.runtime?.label() ?: stringResource(Res.string.discover_filter_runtime),
                selectedKey = filters.runtime?.name ?: anyKey,
                options = runtimeOptions,
                onSelected = { option ->
                    onFiltersChange { it.copy(runtime = DiscoverRuntime.entries.firstOrNull { r -> r.name == option.key }) }
                },
            )
            NuvioDropdownChip(
                title = stringResource(Res.string.discover_filter_language),
                label = filters.language?.let { code -> options.languages.labelFor(code) }
                    ?: stringResource(Res.string.discover_filter_language),
                selectedKey = filters.language ?: anyKey,
                options = languageOptions,
                enabled = options.languages.isNotEmpty() || filters.language != null,
                onSelected = { option -> onFiltersChange { it.copy(language = option.key.ifBlank { null }) } },
            )
            NuvioDropdownChip(
                title = stringResource(Res.string.discover_filter_country),
                label = filters.country?.let { code -> options.countries.labelFor(code) }
                    ?: stringResource(Res.string.discover_filter_country),
                selectedKey = filters.country ?: anyKey,
                options = countryOptions,
                enabled = options.countries.isNotEmpty() || filters.country != null,
                onSelected = { option -> onFiltersChange { it.copy(country = option.key.ifBlank { null }) } },
            )
            NuvioDropdownChip(
                title = stringResource(Res.string.discover_filter_provider),
                label = filters.providerId?.let { id -> DiscoverProviderOptions.labelFor(id) }
                    ?: stringResource(Res.string.discover_filter_provider),
                selectedKey = filters.providerId ?: anyKey,
                options = providerOptions,
                onSelected = { option -> onFiltersChange { it.copy(providerId = option.key.ifBlank { null }) } },
            )
            if (isTv) {
                NuvioDropdownChip(
                    title = stringResource(Res.string.discover_filter_network),
                    label = filters.networkId?.let { id -> DiscoverNetworkOptions.labelFor(id) }
                        ?: stringResource(Res.string.discover_filter_network),
                    selectedKey = filters.networkId ?: anyKey,
                    options = networkOptions,
                    onSelected = { option -> onFiltersChange { it.copy(networkId = option.key.ifBlank { null }) } },
                )
            } else {
                NuvioDropdownChip(
                    title = stringResource(Res.string.discover_filter_certification),
                    label = filters.certification ?: stringResource(Res.string.discover_filter_certification),
                    selectedKey = filters.certification ?: anyKey,
                    options = certificationOptions,
                    onSelected = { option -> onFiltersChange { it.copy(certification = option.key.ifBlank { null }) } },
                )
            }
        }

        if (!filters.isDefault) {
            DiscoverActiveFilterChips(
                state = state,
                onFiltersChange = onFiltersChange,
                onClearAll = onClearAll,
            )
        }
    }
}

@Composable
private fun DiscoverActiveFilterChips(
    state: DiscoverUiState,
    onFiltersChange: ((DiscoverFilters) -> DiscoverFilters) -> Unit,
    onClearAll: () -> Unit,
) {
    val filters = state.filters
    val options = state.filterOptions
    val genres = if (state.isTmdbTv) options.tvGenres else options.movieGenres

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        genres.filter { it.id in filters.genreIds }.forEach { genre ->
            ActiveFilterChip(genre.name) {
                onFiltersChange { it.copy(genreIds = it.genreIds - genre.id) }
            }
        }
        if (filters.sort != DiscoverSort.Popular) {
            ActiveFilterChip(filters.sort.label()) { onFiltersChange { it.copy(sort = DiscoverSort.Popular) } }
        }
        filters.yearKey?.let { key ->
            ActiveFilterChip(key.yearLabel()) { onFiltersChange { it.copy(yearKey = null) } }
        }
        filters.minRating?.let { rating ->
            ActiveFilterChip(stringResource(Res.string.discover_rating_min, rating.toString())) {
                onFiltersChange { it.copy(minRating = null) }
            }
        }
        filters.runtime?.let { runtime ->
            ActiveFilterChip(runtime.label()) { onFiltersChange { it.copy(runtime = null) } }
        }
        filters.language?.let { code ->
            ActiveFilterChip(options.languages.labelFor(code)) { onFiltersChange { it.copy(language = null) } }
        }
        filters.country?.let { code ->
            ActiveFilterChip(options.countries.labelFor(code)) { onFiltersChange { it.copy(country = null) } }
        }
        filters.providerId?.let { id ->
            ActiveFilterChip(DiscoverProviderOptions.labelFor(id)) { onFiltersChange { it.copy(providerId = null) } }
        }
        filters.networkId?.let { id ->
            ActiveFilterChip(DiscoverNetworkOptions.labelFor(id)) { onFiltersChange { it.copy(networkId = null) } }
        }
        filters.certification?.let { code ->
            ActiveFilterChip(code) { onFiltersChange { it.copy(certification = null) } }
        }
        Chip(
            label = stringResource(Res.string.discover_filters_clear_all),
            selected = false,
            onClick = onClearAll,
        )
    }
}

@Composable
private fun ActiveFilterChip(
    label: String,
    onRemove: () -> Unit,
) {
    Chip(
        label = "$label  ✕",
        selected = true,
        onClick = onRemove,
    )
}

/** Multi-select genre picker; same look as [NuvioDropdownChip] but toggles several genres in one sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoverGenreMultiChip(
    title: String,
    label: String,
    genres: List<DiscoverTmdbGenre>,
    selectedIds: Set<Int>,
    onToggle: (Int) -> Unit,
    onClear: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val enabled = genres.isNotEmpty()
    var isSheetVisible by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    Row(
        modifier = Modifier
            .clip(tokens.shapes.compactCard)
            .background(tokens.colors.surface)
            .then(if (enabled) Modifier.clickable { isSheetVisible = true } else Modifier)
            .padding(horizontal = NuvioTokens.Space.s12, vertical = tokens.components.chipVerticalPadding),
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) tokens.colors.textPrimary else tokens.colors.textDisabled,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(NuvioTokens.Icon.sm + NuvioTokens.Space.s2),
            tint = if (enabled) tokens.colors.textMuted else tokens.colors.borderDefault,
        )
    }

    if (isSheetVisible) {
        val dismiss: () -> Unit = {
            coroutineScope.launch {
                dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = { isSheetVisible = false })
            }
        }
        NuvioModalBottomSheet(
            onDismissRequest = { isSheetVisible = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = nuvioSafeBottomPadding(tokens.spacing.screenHorizontal)),
            ) {
                Text(
                    text = title,
                    modifier = Modifier.padding(
                        horizontal = tokens.spacing.screenHorizontal,
                        vertical = NuvioTokens.Space.s14,
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.colors.textPrimary,
                )
                NuvioBottomSheetDivider()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = tokens.breakpoints.largePhone),
                ) {
                    items(genres, key = { it.id }) { genre ->
                        val selected = genre.id in selectedIds
                        NuvioBottomSheetActionRow(
                            title = genre.name,
                            onClick = { onToggle(genre.id) },
                            selected = selected,
                            trailingContent = {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = tokens.colors.accent,
                                        modifier = Modifier.size(tokens.icons.md),
                                    )
                                }
                            },
                        )
                    }
                }
                NuvioBottomSheetDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = tokens.spacing.screenHorizontal, vertical = NuvioTokens.Space.s12),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Chip(
                        label = stringResource(Res.string.discover_filters_done),
                        selected = true,
                        onClick = dismiss,
                    )
                    if (selectedIds.isNotEmpty()) {
                        Chip(
                            label = stringResource(Res.string.discover_filters_clear_all),
                            selected = false,
                            onClick = onClear,
                        )
                    }
                }
            }
        }
    }
}

private fun List<DiscoverNamedOption>.labelFor(key: String): String =
    firstOrNull { it.key == key }?.label ?: key.uppercase()

private fun String.yearLabel(): String =
    if (startsWith("d")) "${drop(1)}s" else drop(1)

@Composable
private fun DiscoverSort.label(): String =
    when (this) {
        DiscoverSort.Popular -> stringResource(Res.string.discover_sort_popular)
        DiscoverSort.Newest -> stringResource(Res.string.discover_sort_newest)
        DiscoverSort.Oldest -> stringResource(Res.string.discover_sort_oldest)
        DiscoverSort.TopRated -> stringResource(Res.string.discover_sort_top_rated)
        DiscoverSort.MostVoted -> stringResource(Res.string.discover_sort_most_voted)
        DiscoverSort.Revenue -> stringResource(Res.string.discover_sort_revenue)
        DiscoverSort.Upcoming -> stringResource(Res.string.discover_sort_upcoming)
        DiscoverSort.NowPlaying -> stringResource(Res.string.discover_sort_now_playing)
    }

@Composable
private fun DiscoverRuntime.label(): String =
    when (this) {
        DiscoverRuntime.Under90 -> stringResource(Res.string.discover_runtime_under_90)
        DiscoverRuntime.From90To120 -> stringResource(Res.string.discover_runtime_90_120)
        DiscoverRuntime.From120To150 -> stringResource(Res.string.discover_runtime_120_150)
        DiscoverRuntime.Over150 -> stringResource(Res.string.discover_runtime_over_150)
    }
