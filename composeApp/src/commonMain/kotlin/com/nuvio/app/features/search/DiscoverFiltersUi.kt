package com.nuvio.app.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ConnectedTv
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioBottomSheetActionRow
import com.nuvio.app.core.ui.NuvioBottomSheetDivider
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
private const val ANIME_YEAR_SPAN = 40

/** Option lists longer than this are shown as a collapsible list in the full filter sheet instead of as pills. */
private const val MAX_PILL_OPTIONS = 48

private val FilterBarHorizontalPadding = 16.dp

private enum class DiscoverSection { Browse, Release, Quality, Origin, Availability, Audience }

/**
 * One filter, described independent of how it is rendered (compact chip + picker sheet, or section of the full
 * Filters sheet). An option with an empty key means "any" / clear.
 */
private class DiscoverField(
    val title: String,
    val icon: ImageVector,
    val section: DiscoverSection,
    val options: List<NuvioDropdownOption>,
    val selectedKeys: Set<String>,
    val multi: Boolean,
    /** Shown after the title ("Genre: Action"); null when nothing is picked. */
    val valueLabel: String?,
    /** Highlight with the accent colour; false for filters sitting on their default value. */
    val isActive: Boolean,
    val enabled: Boolean = true,
    val onPick: (String) -> Unit,
) {
    fun isSelected(key: String): Boolean =
        if (key.isEmpty()) selectedKeys.isEmpty() else key in selectedKeys
}

/**
 * TMDB discover filter bar: a "Filters" button (full sheet, grouped into sections) followed by a horizontally
 * scrolling row of compact filter chips, then removable active-filter chips with a clear-all action.
 *
 * Movies / Series offer genre, sort, year/decade, rating, runtime, language, country, streaming provider, network
 * (series) and age rating (movies). Anime swaps in anime genres (no Animation), Format (TV / Movie), Season, Status
 * and a reduced sort list, and hides age rating and revenue.
 */
@Composable
internal fun DiscoverTmdbFilters(
    state: DiscoverUiState,
    onFiltersChange: ((DiscoverFilters) -> DiscoverFilters) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val filters = state.filters
    val fields = discoverFields(state, onFiltersChange)
    var showAllFilters by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = FilterBarHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DiscoverFiltersButton(
                activeCount = filters.activeCount,
                onClick = { showAllFilters = true },
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                fields.forEach { field -> DiscoverFilterChip(field) }
                Spacer(modifier = Modifier.width(8.dp))
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

    if (showAllFilters) {
        DiscoverAllFiltersSheet(
            fields = fields,
            activeCount = filters.activeCount,
            onClearAll = onClearAll,
            onClosed = { showAllFilters = false },
        )
    }
}

@Composable
private fun discoverFields(
    state: DiscoverUiState,
    onFiltersChange: ((DiscoverFilters) -> DiscoverFilters) -> Unit,
): List<DiscoverField> {
    val filters = state.filters
    val options = state.filterOptions
    val isTv = state.isTmdbTv
    val isAnime = state.isTmdbAnime
    val currentYear = remember { CurrentDateProvider.todayIsoDate().take(4).toIntOrNull() ?: 2025 }
    val anyKey = ""

    val genres = when {
        isAnime -> options.animeGenres(isTv)
        isTv -> options.tvGenres
        else -> options.movieGenres
    }
    val sorts = if (isAnime) {
        AnimeSortOptions
    } else {
        DiscoverSort.entries.filter { it != DiscoverSort.Trending && !(isTv && it == DiscoverSort.Revenue) }
    }
    val sortOptions = sorts.map { NuvioDropdownOption(key = it.name, label = it.label()) }
    val yearOptions = buildList {
        add(NuvioDropdownOption(anyKey, stringResource(Res.string.discover_year_any)))
        if (!isAnime) {
            var decade = currentYear / 10 * 10
            while (decade >= FIRST_DECADE) {
                add(NuvioDropdownOption(discoverDecadeKey(decade), "${decade}s"))
                decade -= 10
            }
        }
        val span = if (isAnime) ANIME_YEAR_SPAN else RECENT_YEAR_SPAN
        for (year in currentYear downTo currentYear - span) {
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

    val genreTitle = stringResource(Res.string.discover_filter_genre)
    val sortTitle = stringResource(Res.string.discover_filter_sort)
    val yearTitle = stringResource(Res.string.discover_filter_year)
    val ratingTitle = stringResource(Res.string.discover_filter_rating)
    val runtimeTitle = stringResource(Res.string.discover_filter_runtime)
    val languageTitle = stringResource(Res.string.discover_filter_language)
    val countryTitle = stringResource(Res.string.discover_filter_country)
    val providerTitle = stringResource(Res.string.discover_filter_provider)
    val networkTitle = stringResource(Res.string.discover_filter_network)
    val certificationTitle = stringResource(Res.string.discover_filter_certification)

    val genreField = DiscoverField(
        title = genreTitle,
        icon = Icons.Rounded.Category,
        section = DiscoverSection.Browse,
        options = genres.map { NuvioDropdownOption(it.id.toString(), it.name) },
        selectedKeys = filters.genreIds.map { it.toString() }.toSet(),
        multi = true,
        valueLabel = when (filters.genreIds.size) {
            0 -> null
            1 -> genres.firstOrNull { it.id in filters.genreIds }?.name
            else -> stringResource(Res.string.discover_genres_count, filters.genreIds.size)
        },
        isActive = filters.genreIds.isNotEmpty(),
        enabled = genres.isNotEmpty(),
        onPick = { key ->
            val id = key.toIntOrNull()
            onFiltersChange { current ->
                when {
                    id == null -> current.copy(genreIds = emptySet())
                    id in current.genreIds -> current.copy(genreIds = current.genreIds - id)
                    else -> current.copy(genreIds = current.genreIds + id)
                }
            }
        },
    )
    val sortField = DiscoverField(
        title = sortTitle,
        icon = Icons.AutoMirrored.Rounded.Sort,
        section = DiscoverSection.Browse,
        options = sortOptions,
        selectedKeys = setOf(filters.sort.name),
        multi = false,
        valueLabel = filters.sort.label(),
        isActive = filters.sort != DiscoverSort.Popular,
        onPick = { key ->
            val sort = DiscoverSort.entries.firstOrNull { it.name == key } ?: DiscoverSort.Popular
            onFiltersChange { it.copy(sort = sort) }
        },
    )
    val yearField = DiscoverField(
        title = yearTitle,
        icon = Icons.Rounded.CalendarMonth,
        section = DiscoverSection.Release,
        options = yearOptions,
        selectedKeys = setOfNotNull(filters.yearKey),
        multi = false,
        valueLabel = filters.yearKey?.yearLabel(),
        isActive = filters.yearKey != null,
        onPick = { key -> onFiltersChange { it.copy(yearKey = key.ifBlank { null }) } },
    )
    val ratingField = DiscoverField(
        title = ratingTitle,
        icon = Icons.Rounded.Star,
        section = DiscoverSection.Quality,
        options = ratingOptions,
        selectedKeys = setOfNotNull(filters.minRating?.toString()),
        multi = false,
        valueLabel = filters.minRating?.let { stringResource(Res.string.discover_rating_min, it.toString()) },
        isActive = filters.minRating != null,
        onPick = { key -> onFiltersChange { it.copy(minRating = key.toIntOrNull()) } },
    )
    val runtimeField = DiscoverField(
        title = runtimeTitle,
        icon = Icons.Rounded.Schedule,
        section = DiscoverSection.Quality,
        options = runtimeOptions,
        selectedKeys = setOfNotNull(filters.runtime?.name),
        multi = false,
        valueLabel = filters.runtime?.label(),
        isActive = filters.runtime != null,
        onPick = { key ->
            onFiltersChange { it.copy(runtime = DiscoverRuntime.entries.firstOrNull { r -> r.name == key }) }
        },
    )
    val languageField = DiscoverField(
        title = languageTitle,
        icon = Icons.Rounded.Language,
        section = DiscoverSection.Origin,
        options = languageOptions,
        selectedKeys = setOfNotNull(filters.language),
        multi = false,
        valueLabel = filters.language?.let { code -> options.languages.labelFor(code) },
        isActive = filters.language != null,
        enabled = options.languages.isNotEmpty() || filters.language != null,
        onPick = { key -> onFiltersChange { it.copy(language = key.ifBlank { null }) } },
    )
    val countryField = DiscoverField(
        title = countryTitle,
        icon = Icons.Rounded.Public,
        section = DiscoverSection.Origin,
        options = countryOptions,
        selectedKeys = setOfNotNull(filters.country),
        multi = false,
        valueLabel = filters.country?.let { code -> options.countries.labelFor(code) },
        isActive = filters.country != null,
        enabled = options.countries.isNotEmpty() || filters.country != null,
        onPick = { key -> onFiltersChange { it.copy(country = key.ifBlank { null }) } },
    )
    val providerField = DiscoverField(
        title = providerTitle,
        icon = Icons.Rounded.OndemandVideo,
        section = DiscoverSection.Availability,
        options = providerOptions,
        selectedKeys = setOfNotNull(filters.providerId),
        multi = false,
        valueLabel = filters.providerId?.let { id -> DiscoverProviderOptions.labelFor(id) },
        isActive = filters.providerId != null,
        onPick = { key -> onFiltersChange { it.copy(providerId = key.ifBlank { null }) } },
    )
    val networkField = DiscoverField(
        title = networkTitle,
        icon = Icons.Rounded.ConnectedTv,
        section = DiscoverSection.Availability,
        options = networkOptions,
        selectedKeys = setOfNotNull(filters.networkId),
        multi = false,
        valueLabel = filters.networkId?.let { id -> DiscoverNetworkOptions.labelFor(id) },
        isActive = filters.networkId != null,
        onPick = { key -> onFiltersChange { it.copy(networkId = key.ifBlank { null }) } },
    )
    val certificationField = DiscoverField(
        title = certificationTitle,
        icon = Icons.Rounded.Shield,
        section = DiscoverSection.Audience,
        options = certificationOptions,
        selectedKeys = setOfNotNull(filters.certification),
        multi = false,
        valueLabel = filters.certification,
        isActive = filters.certification != null,
        onPick = { key -> onFiltersChange { it.copy(certification = key.ifBlank { null }) } },
    )

    if (!isAnime) {
        return buildList {
            add(genreField)
            add(sortField)
            add(yearField)
            add(ratingField)
            add(runtimeField)
            add(languageField)
            add(countryField)
            add(providerField)
            if (isTv) add(networkField) else add(certificationField)
        }
    }

    // Anime: format first (it decides TV vs movie), then season / year / status, and no age rating or revenue sort.
    val formatField = DiscoverField(
        title = stringResource(Res.string.discover_filter_format),
        icon = Icons.Rounded.Tv,
        section = DiscoverSection.Browse,
        options = AnimeFormat.entries.map { NuvioDropdownOption(it.name, it.label()) },
        selectedKeys = setOf(filters.animeFormat.name),
        multi = false,
        valueLabel = filters.animeFormat.label(),
        isActive = filters.animeFormat != AnimeFormat.Tv,
        onPick = { key ->
            val format = AnimeFormat.entries.firstOrNull { it.name == key } ?: AnimeFormat.Tv
            onFiltersChange { it.copy(animeFormat = format) }
        },
    )
    val seasonField = DiscoverField(
        title = stringResource(Res.string.discover_filter_season),
        icon = Icons.Rounded.Event,
        section = DiscoverSection.Release,
        options = listOf(NuvioDropdownOption(anyKey, stringResource(Res.string.discover_season_any))) +
            AnimeSeason.entries.map { NuvioDropdownOption(it.name, it.label()) },
        selectedKeys = setOfNotNull(filters.animeSeason?.name),
        multi = false,
        valueLabel = filters.animeSeason?.label(),
        isActive = filters.animeSeason != null,
        onPick = { key ->
            val season = AnimeSeason.entries.firstOrNull { it.name == key }
            onFiltersChange { current ->
                current.copy(
                    animeSeason = season,
                    // A season always belongs to a year; default to the current one so the Year chip shows it.
                    yearKey = if (season != null && current.yearKey == null) discoverYearKey(currentYear) else current.yearKey,
                )
            }
        },
    )
    val statusField = DiscoverField(
        title = stringResource(Res.string.discover_filter_status),
        icon = Icons.Rounded.HourglassTop,
        section = DiscoverSection.Release,
        options = listOf(NuvioDropdownOption(anyKey, stringResource(Res.string.discover_status_any))) +
            AnimeStatus.entries.map { NuvioDropdownOption(it.name, it.label()) },
        selectedKeys = setOfNotNull(filters.animeStatus?.name),
        multi = false,
        valueLabel = filters.animeStatus?.label(),
        isActive = filters.animeStatus != null,
        onPick = { key ->
            onFiltersChange { it.copy(animeStatus = AnimeStatus.entries.firstOrNull { s -> s.name == key }) }
        },
    )

    return buildList {
        add(formatField)
        add(genreField)
        add(sortField)
        add(seasonField)
        add(yearField)
        if (isTv) add(statusField)
        add(ratingField)
        add(runtimeField)
        add(languageField)
        add(countryField)
        add(providerField)
        if (isTv) add(networkField)
    }
}

/** Shared pill look for chips and buttons in the filter bar. */
@Composable
private fun DiscoverPill(
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    solid: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val shape = tokens.shapes.chip
    val background = when {
        solid -> tokens.colors.accent
        active -> tokens.colors.accent.copy(alpha = tokens.opacity.selected)
        else -> tokens.colors.surface
    }
    val borderColor = when {
        solid -> tokens.colors.accent
        active -> tokens.colors.borderSelected
        else -> tokens.colors.borderDefault
    }
    Row(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(tokens.borders.thin, borderColor, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s8),
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun DiscoverFiltersButton(
    activeCount: Int,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val hasActive = activeCount > 0
    val contentColor = if (hasActive) tokens.colors.onAccent else tokens.colors.textPrimary
    DiscoverPill(
        active = hasActive,
        solid = hasActive,
        onClick = onClick,
    ) {
        Icon(
            imageVector = Icons.Rounded.Tune,
            contentDescription = null,
            modifier = Modifier.size(NuvioTokens.Icon.sm + NuvioTokens.Space.s2),
            tint = contentColor,
        )
        Text(
            text = stringResource(Res.string.discover_filters_button),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = contentColor,
            maxLines = 1,
        )
        if (hasActive) {
            Box(
                modifier = Modifier
                    .size(NuvioTokens.Space.s18)
                    .clip(tokens.shapes.avatar)
                    .background(tokens.colors.onAccent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = activeCount.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = tokens.colors.accent,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Compact chip: icon, "Title: Value" and a chevron; opens a picker sheet for just this filter. */
@Composable
private fun DiscoverFilterChip(field: DiscoverField) {
    val tokens = MaterialTheme.nuvio
    var isSheetVisible by remember { mutableStateOf(false) }
    val label = field.valueLabel?.let { stringResource(Res.string.discover_chip_value, field.title, it) } ?: field.title
    val primary = when {
        !field.enabled -> tokens.colors.textDisabled
        field.isActive -> tokens.colors.textPrimary
        else -> tokens.colors.textSecondary
    }
    val accentOrMuted = when {
        !field.enabled -> tokens.colors.borderDefault
        field.isActive -> tokens.colors.accent
        else -> tokens.colors.textMuted
    }

    DiscoverPill(
        active = field.isActive,
        enabled = field.enabled,
        onClick = { isSheetVisible = true },
    ) {
        Icon(
            imageVector = field.icon,
            contentDescription = null,
            modifier = Modifier.size(NuvioTokens.Icon.sm + NuvioTokens.Space.s2),
            tint = accentOrMuted,
        )
        Text(
            text = label,
            modifier = Modifier.widthIn(max = 200.dp),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (field.isActive) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(NuvioTokens.Icon.sm + NuvioTokens.Space.s2),
            tint = accentOrMuted,
        )
    }

    if (isSheetVisible) {
        DiscoverFieldSheet(field = field, onClosed = { isSheetVisible = false })
    }
}

/** Picker for one filter. Single-select closes on pick; multi-select (genres) stays open with Done / Clear. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoverFieldSheet(
    field: DiscoverField,
    onClosed: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val dismiss: () -> Unit = {
        coroutineScope.launch {
            dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onClosed)
        }
    }

    NuvioModalBottomSheet(
        onDismissRequest = onClosed,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = nuvioSafeBottomPadding(tokens.spacing.screenHorizontal)),
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = tokens.spacing.screenHorizontal,
                    vertical = NuvioTokens.Space.s14,
                ),
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = field.icon,
                    contentDescription = null,
                    modifier = Modifier.size(tokens.icons.md),
                    tint = tokens.colors.accent,
                )
                Text(
                    text = field.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.colors.textPrimary,
                )
            }
            NuvioBottomSheetDivider()
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = tokens.breakpoints.largePhone),
            ) {
                items(field.options, key = { it.key }) { option ->
                    val selected = field.isSelected(option.key)
                    NuvioBottomSheetActionRow(
                        title = option.label,
                        onClick = {
                            field.onPick(option.key)
                            if (!field.multi) dismiss()
                        },
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
            if (field.multi) {
                NuvioBottomSheetDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = tokens.spacing.screenHorizontal, vertical = NuvioTokens.Space.s12),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DiscoverPill(active = true, solid = true, onClick = dismiss) {
                        Text(
                            text = stringResource(Res.string.discover_filters_done),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = tokens.colors.onAccent,
                            maxLines = 1,
                        )
                    }
                    if (field.selectedKeys.isNotEmpty()) {
                        DiscoverPill(active = false, onClick = { field.onPick("") }) {
                            Text(
                                text = stringResource(Res.string.discover_filters_clear_all),
                                style = MaterialTheme.typography.labelLarge,
                                color = tokens.colors.textSecondary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Full Filters sheet: every filter, grouped into titled sections. Changes apply immediately. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoverAllFiltersSheet(
    fields: List<DiscoverField>,
    activeCount: Int,
    onClearAll: () -> Unit,
    onClosed: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val dismiss: () -> Unit = {
        coroutineScope.launch {
            dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onClosed)
        }
    }

    NuvioModalBottomSheet(
        onDismissRequest = onClosed,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = nuvioSafeBottomPadding(tokens.spacing.screenHorizontal)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.screenHorizontal, vertical = NuvioTokens.Space.s14),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.discover_filters_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.colors.textPrimary,
                )
                if (activeCount > 0) {
                    Text(
                        text = stringResource(Res.string.discover_filters_clear_all),
                        modifier = Modifier
                            .clip(tokens.shapes.chip)
                            .clickable(onClick = onClearAll)
                            .padding(horizontal = NuvioTokens.Space.s8, vertical = NuvioTokens.Space.s4),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = tokens.colors.accent,
                    )
                }
            }
            NuvioBottomSheetDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = tokens.spacing.screenHorizontal, vertical = NuvioTokens.Space.s14),
                verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s20),
            ) {
                DiscoverSection.entries.forEach { section ->
                    val sectionFields = fields.filter { it.section == section }
                    if (sectionFields.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s14)) {
                            Text(
                                text = section.title(),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = NuvioTokens.LetterSpacing.label,
                                ),
                                color = tokens.colors.accent,
                                maxLines = 1,
                            )
                            sectionFields.forEach { field -> DiscoverFieldBlock(field) }
                        }
                    }
                }
            }
            NuvioBottomSheetDivider()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.screenHorizontal, vertical = NuvioTokens.Space.s12),
            ) {
                DiscoverPill(
                    active = true,
                    solid = true,
                    onClick = dismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(Res.string.discover_filters_show_results),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = tokens.colors.onAccent,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscoverFieldBlock(field: DiscoverField) {
    val tokens = MaterialTheme.nuvio
    Column(verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = field.icon,
                contentDescription = null,
                modifier = Modifier.size(tokens.icons.sm),
                tint = tokens.colors.textMuted,
            )
            Text(
                text = field.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = tokens.colors.textPrimary,
                maxLines = 1,
            )
        }
        if (field.options.size <= MAX_PILL_OPTIONS) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
                verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
            ) {
                field.options.forEach { option ->
                    val selected = field.isSelected(option.key)
                    DiscoverPill(
                        active = selected,
                        enabled = field.enabled,
                        onClick = { field.onPick(option.key) },
                    ) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            ),
                            color = when {
                                !field.enabled -> tokens.colors.textDisabled
                                selected -> tokens.colors.textPrimary
                                else -> tokens.colors.textSecondary
                            },
                            maxLines = 1,
                        )
                    }
                }
            }
        } else {
            DiscoverExpandableOptions(field)
        }
    }
}

/** Long option lists (languages, countries) collapse to one row that expands into a scrollable list. */
@Composable
private fun DiscoverExpandableOptions(field: DiscoverField) {
    val tokens = MaterialTheme.nuvio
    var expanded by remember { mutableStateOf(false) }
    val current = field.options.firstOrNull { field.isSelected(it.key) }?.label ?: field.options.firstOrNull()?.label.orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6)) {
        DiscoverPill(
            active = field.isActive,
            enabled = field.enabled,
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = current,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (field.isActive) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = if (field.isActive) tokens.colors.textPrimary else tokens.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(NuvioTokens.Icon.sm + NuvioTokens.Space.s2),
                tint = if (field.isActive) tokens.colors.accent else tokens.colors.textMuted,
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .clip(tokens.shapes.compactCard)
                    .background(tokens.colors.surfaceCard)
                    .verticalScroll(rememberScrollState()),
            ) {
                field.options.forEach { option ->
                    val selected = field.isSelected(option.key)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                field.onPick(option.key)
                                expanded = false
                            }
                            .padding(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s10),
                        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = option.label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            color = if (selected) tokens.colors.textPrimary else tokens.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = tokens.colors.accent,
                                modifier = Modifier.size(tokens.icons.sm),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverActiveFilterChips(
    state: DiscoverUiState,
    onFiltersChange: ((DiscoverFilters) -> DiscoverFilters) -> Unit,
    onClearAll: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val filters = state.filters
    val options = state.filterOptions
    val isTv = state.isTmdbTv
    val genres = when {
        state.isTmdbAnime -> options.animeGenres(isTv)
        isTv -> options.tvGenres
        else -> options.movieGenres
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(FilterBarHorizontalPadding - 8.dp))
        genres.filter { it.id in filters.genreIds }.forEach { genre ->
            ActiveFilterChip(genre.name) {
                onFiltersChange { it.copy(genreIds = it.genreIds - genre.id) }
            }
        }
        if (filters.animeFormat != AnimeFormat.Tv) {
            ActiveFilterChip(filters.animeFormat.label()) { onFiltersChange { it.copy(animeFormat = AnimeFormat.Tv) } }
        }
        if (filters.sort != DiscoverSort.Popular) {
            ActiveFilterChip(filters.sort.label()) { onFiltersChange { it.copy(sort = DiscoverSort.Popular) } }
        }
        filters.animeSeason?.let { season ->
            ActiveFilterChip(season.label()) { onFiltersChange { it.copy(animeSeason = null) } }
        }
        filters.yearKey?.let { key ->
            ActiveFilterChip(key.yearLabel()) { onFiltersChange { it.copy(yearKey = null) } }
        }
        filters.animeStatus?.let { status ->
            ActiveFilterChip(status.label()) { onFiltersChange { it.copy(animeStatus = null) } }
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
        Text(
            text = stringResource(Res.string.discover_filters_clear_all),
            modifier = Modifier
                .clip(tokens.shapes.chip)
                .clickable(onClick = onClearAll)
                .padding(horizontal = NuvioTokens.Space.s10, vertical = NuvioTokens.Space.s6),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = tokens.colors.accent,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(FilterBarHorizontalPadding - 8.dp))
    }
}

/** Removable chip for an applied filter value. */
@Composable
private fun ActiveFilterChip(
    label: String,
    onRemove: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val shape = tokens.shapes.chip
    Row(
        modifier = Modifier
            .clip(shape)
            .background(tokens.colors.accent.copy(alpha = tokens.opacity.selected))
            .border(tokens.borders.thin, tokens.colors.borderSelected, shape)
            .clickable(onClick = onRemove)
            .padding(start = NuvioTokens.Space.s12, end = NuvioTokens.Space.s8, top = NuvioTokens.Space.s6, bottom = NuvioTokens.Space.s6),
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = tokens.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = null,
            modifier = Modifier.size(NuvioTokens.Icon.sm),
            tint = tokens.colors.accent,
        )
    }
}

private fun List<DiscoverNamedOption>.labelFor(key: String): String =
    firstOrNull { it.key == key }?.label ?: key.uppercase()

private fun String.yearLabel(): String =
    if (startsWith("d")) "${drop(1)}s" else drop(1)

@Composable
private fun DiscoverSection.title(): String =
    when (this) {
        DiscoverSection.Browse -> stringResource(Res.string.discover_section_browse)
        DiscoverSection.Release -> stringResource(Res.string.discover_section_release)
        DiscoverSection.Quality -> stringResource(Res.string.discover_section_quality)
        DiscoverSection.Origin -> stringResource(Res.string.discover_section_origin)
        DiscoverSection.Availability -> stringResource(Res.string.discover_section_availability)
        DiscoverSection.Audience -> stringResource(Res.string.discover_section_audience)
    }

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
        DiscoverSort.Trending -> stringResource(Res.string.discover_sort_trending)
    }

@Composable
private fun DiscoverRuntime.label(): String =
    when (this) {
        DiscoverRuntime.Under90 -> stringResource(Res.string.discover_runtime_under_90)
        DiscoverRuntime.From90To120 -> stringResource(Res.string.discover_runtime_90_120)
        DiscoverRuntime.From120To150 -> stringResource(Res.string.discover_runtime_120_150)
        DiscoverRuntime.Over150 -> stringResource(Res.string.discover_runtime_over_150)
    }

@Composable
private fun AnimeFormat.label(): String =
    when (this) {
        AnimeFormat.Tv -> stringResource(Res.string.discover_format_tv)
        AnimeFormat.Movie -> stringResource(Res.string.discover_format_movie)
    }

@Composable
private fun AnimeSeason.label(): String =
    when (this) {
        AnimeSeason.Winter -> stringResource(Res.string.discover_season_winter)
        AnimeSeason.Spring -> stringResource(Res.string.discover_season_spring)
        AnimeSeason.Summer -> stringResource(Res.string.discover_season_summer)
        AnimeSeason.Fall -> stringResource(Res.string.discover_season_fall)
    }

@Composable
private fun AnimeStatus.label(): String =
    when (this) {
        AnimeStatus.Airing -> stringResource(Res.string.discover_status_airing)
        AnimeStatus.Ended -> stringResource(Res.string.discover_status_ended)
    }
