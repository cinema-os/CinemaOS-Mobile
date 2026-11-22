package com.nuvio.app.features.sports

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.LocalScreenActive
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioShelfSection
import com.nuvio.app.core.ui.NuvioViewAllPillSize
import com.nuvio.app.core.ui.backdropControlColor
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.addons.AddonCatalog
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.catalog.fetchCatalogPage
import com.nuvio.app.features.catalog.supportsPagination
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.HomePosterCard
import com.nuvio.app.features.home.components.homeSectionHorizontalPaddingForWidth
import com.nuvio.app.features.home.stableKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_nav_sports
import nuvio.composeapp.generated.resources.sports_empty_message
import nuvio.composeapp.generated.resources.sports_error_message
import nuvio.composeapp.generated.resources.sports_filter_all
import nuvio.composeapp.generated.resources.sports_filter_american_football
import nuvio.composeapp.generated.resources.sports_filter_baseball
import nuvio.composeapp.generated.resources.sports_filter_basketball
import nuvio.composeapp.generated.resources.sports_filter_college
import nuvio.composeapp.generated.resources.sports_filter_cricket
import nuvio.composeapp.generated.resources.sports_filter_darts
import nuvio.composeapp.generated.resources.sports_filter_football
import nuvio.composeapp.generated.resources.sports_filter_golf
import nuvio.composeapp.generated.resources.sports_filter_live_tv
import nuvio.composeapp.generated.resources.sports_filter_hockey
import nuvio.composeapp.generated.resources.sports_filter_mma
import nuvio.composeapp.generated.resources.sports_filter_motorsport
import nuvio.composeapp.generated.resources.sports_filter_other
import nuvio.composeapp.generated.resources.sports_filter_rugby
import nuvio.composeapp.generated.resources.sports_filter_tennis
import nuvio.composeapp.generated.resources.sports_live_badge
import nuvio.composeapp.generated.resources.sports_live_tv_empty
import nuvio.composeapp.generated.resources.sports_live_tv_search_placeholder
import nuvio.composeapp.generated.resources.sports_retry
import nuvio.composeapp.generated.resources.sports_recent_clear
import nuvio.composeapp.generated.resources.sports_recent_remove
import nuvio.composeapp.generated.resources.sports_recent_title
import nuvio.composeapp.generated.resources.sports_row_live
import nuvio.composeapp.generated.resources.sports_row_networks
import nuvio.composeapp.generated.resources.sports_row_replays
import nuvio.composeapp.generated.resources.sports_row_upcoming
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val SPORTS_ROW_PREVIEW_ITEMS = 20
private const val SPORTS_LIVE_REFRESH_MS = 60_000L
private const val SPORTS_CATALOG_TYPE = "tv"
private const val LIVE_TV_PAGE_ITEMS = 100
private const val LIVE_TV_SEARCH_DEBOUNCE_MS = 350L

/**
 * One sport filter chip. [sportKey] is the suffix of the addon's per-sport catalog
 * (`nuvio_sports_<sportKey>`), [genre] the Live/Upcoming genre option (null = that sport has no
 * genre option, so the Live/Upcoming rows are skipped for it) and [hasReplays] whether the addon
 * has a `nuvio_sports_replays_<sportKey>_recent` catalog.
 */
private enum class SportFilter(
    val sportKey: String?,
    val genre: String?,
    val hasReplays: Boolean,
    val label: StringResource,
) {
    /** Only the 24/7 channels ([SportsAddon.CATALOG_NETWORKS]) as a grid, with search. */
    LiveTv(null, null, false, Res.string.sports_filter_live_tv),
    All(null, null, false, Res.string.sports_filter_all),
    Football("football", "Football", true, Res.string.sports_filter_football),
    Cricket("cricket", "Cricket", false, Res.string.sports_filter_cricket),
    Basketball("basketball", "Basketball", true, Res.string.sports_filter_basketball),
    Motorsport("motorsport", "Motorsport", true, Res.string.sports_filter_motorsport),
    Tennis("tennis", "Tennis", true, Res.string.sports_filter_tennis),
    AmericanFootball("american_football", "American Football", true, Res.string.sports_filter_american_football),
    Hockey("hockey", "Hockey", true, Res.string.sports_filter_hockey),
    Baseball("baseball", "Baseball", true, Res.string.sports_filter_baseball),
    Mma("mma", "MMA", false, Res.string.sports_filter_mma),
    Rugby("rugby", "Rugby", true, Res.string.sports_filter_rugby),
    Golf("golf", "Golf", false, Res.string.sports_filter_golf),
    Darts("darts", "Darts", false, Res.string.sports_filter_darts),
    College("college", null, false, Res.string.sports_filter_college),
    Other("other", "Other", false, Res.string.sports_filter_other),
    ;

    val catalogId: String?
        get() = sportKey?.let { SportsAddon.SPORT_CATALOG_PREFIX + it }

    val replayCatalogId: String?
        get() = sportKey?.takeIf { hasReplays }?.let { SportsAddon.REPLAY_CATALOG_PREFIX + it + "_recent" }
}

private data class SportsRowSpec(
    val key: String,
    val title: String,
    val catalogId: String,
    val genre: String? = null,
    val isLive: Boolean = false,
)

private data class SportsRow(
    val spec: SportsRowSpec,
    val items: List<MetaPreview>,
    val hasMore: Boolean,
    val supportsPagination: Boolean,
)

private class SportsRowResult(val row: SportsRow?, val failed: Boolean)

/**
 * Sports tab: rows of the built-in Live Sports addon (see [SportsAddon]). Opening an item goes
 * through the normal details route (type `tv` / `series`), so the existing meta, streams and player
 * flow is used; stream `behaviorHints.proxyHeaders.request` already reach the player as headers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportsScreen(
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onCatalogClick: ((HomeCatalogSection) -> Unit)? = null,
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
) {
    val addonsState by AddonRepository.uiState.collectAsStateWithLifecycle()
    val sportsAddon = remember(addonsState.addons) { addonsState.addons.firstOrNull { it.isSportsAddon() } }
    val manifestUrl = sportsAddon?.manifestUrl ?: SportsAddon.MANIFEST_URL
    val availableCatalogs = remember(sportsAddon?.manifest) {
        sportsAddon?.manifest?.catalogs?.associateBy { it.id }
    }

    LaunchedEffect(Unit) { SportsRecentRepository.ensureLoaded() }
    val recentEntries by SportsRecentRepository.entries.collectAsStateWithLifecycle()

    var selectedFilterName by rememberSaveable { mutableStateOf(SportFilter.LiveTv.name) }
    val selectedFilter = remember(selectedFilterName) {
        SportFilter.entries.firstOrNull { it.name == selectedFilterName } ?: SportFilter.All
    }

    val filterLabels = SportFilter.entries.associateWith { stringResource(it.label) }
    val liveTitle = stringResource(Res.string.sports_row_live)
    val upcomingTitle = stringResource(Res.string.sports_row_upcoming)
    val networksTitle = stringResource(Res.string.sports_row_networks)
    val replaysTitle = stringResource(Res.string.sports_row_replays)

    val specs = remember(selectedFilter, availableCatalogs, filterLabels, liveTitle, upcomingTitle, networksTitle, replaysTitle) {
        buildRowSpecs(
            filter = selectedFilter,
            filterLabels = filterLabels,
            liveTitle = liveTitle,
            upcomingTitle = upcomingTitle,
            networksTitle = networksTitle,
            replaysTitle = replaysTitle,
        ).filter { spec -> availableCatalogs == null || spec.catalogId in availableCatalogs }
    }

    var rows by remember { mutableStateOf<List<SportsRow>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    // Live TV chip: the 24/7 channels catalog as a paged grid, searchable when the catalog allows it.
    val isLiveTv = selectedFilter == SportFilter.LiveTv
    val networksCatalog = availableCatalogs?.get(SportsAddon.CATALOG_NETWORKS)
    val liveTvSupportsSearch = networksCatalog?.supportsSearchExtra() ?: true
    val liveTvSupportsPagination = networksCatalog?.supportsPagination() ?: true
    var liveTvQuery by rememberSaveable { mutableStateOf("") }
    var liveTvItems by remember { mutableStateOf<List<MetaPreview>>(emptyList()) }
    var liveTvNextSkip by remember { mutableStateOf<Int?>(null) }
    var liveTvLoading by remember { mutableStateOf(false) }
    var liveTvLoadingMore by remember { mutableStateOf(false) }
    var liveTvFailed by remember { mutableStateOf(false) }
    val liveTvSearch = liveTvQuery.trim().takeIf { liveTvSupportsSearch && it.isNotEmpty() }

    suspend fun loadLiveTvFirstPage(forceRefresh: Boolean) {
        try {
            val page = fetchCatalogPage(
                manifestUrl = manifestUrl,
                type = SPORTS_CATALOG_TYPE,
                catalogId = SportsAddon.CATALOG_NETWORKS,
                search = liveTvSearch,
                maxItems = LIVE_TV_PAGE_ITEMS,
                forceRefresh = forceRefresh,
            )
            liveTvItems = page.items.distinctBy { it.stableKey() }
            liveTvNextSkip = page.nextSkip?.takeIf { liveTvSupportsPagination && page.items.isNotEmpty() }
            liveTvFailed = false
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            liveTvItems = emptyList()
            liveTvNextSkip = null
            liveTvFailed = true
        }
    }

    suspend fun loadMoreLiveTv() {
        val skip = liveTvNextSkip ?: return
        if (liveTvLoadingMore) return
        liveTvLoadingMore = true
        try {
            val page = fetchCatalogPage(
                manifestUrl = manifestUrl,
                type = SPORTS_CATALOG_TYPE,
                catalogId = SportsAddon.CATALOG_NETWORKS,
                search = liveTvSearch,
                skip = skip,
                maxItems = LIVE_TV_PAGE_ITEMS,
            )
            val known = liveTvItems.mapTo(HashSet()) { it.stableKey() }
            val fresh = page.items.filter { known.add(it.stableKey()) }
            liveTvItems = liveTvItems + fresh
            // Stop when a page adds nothing new, so a catalog ignoring `skip` cannot loop.
            liveTvNextSkip = page.nextSkip?.takeIf { fresh.isNotEmpty() }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            liveTvNextSkip = null
        } finally {
            liveTvLoadingMore = false
        }
    }

    LaunchedEffect(isLiveTv, liveTvSearch, manifestUrl, reloadToken) {
        if (!isLiveTv) return@LaunchedEffect
        if (liveTvSearch != null) delay(LIVE_TV_SEARCH_DEBOUNCE_MS)
        liveTvLoading = true
        liveTvNextSkip = null
        loadLiveTvFirstPage(forceRefresh = reloadToken > 0)
        liveTvLoading = false
    }

    suspend fun fetchRow(spec: SportsRowSpec, forceRefresh: Boolean): SportsRowResult =
        try {
            val page = fetchCatalogPage(
                manifestUrl = manifestUrl,
                type = SPORTS_CATALOG_TYPE,
                catalogId = spec.catalogId,
                genre = spec.genre,
                maxItems = SPORTS_ROW_PREVIEW_ITEMS,
                forceRefresh = forceRefresh,
            )
            val row = page.items.takeIf { it.isNotEmpty() }?.let { items ->
                SportsRow(
                    spec = spec,
                    items = items,
                    hasMore = page.nextSkip != null,
                    supportsPagination = availableCatalogs?.get(spec.catalogId)?.supportsPagination() == true,
                )
            }
            SportsRowResult(row = row, failed = false)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            SportsRowResult(row = null, failed = true)
        }

    suspend fun loadAll(forceRefresh: Boolean) {
        val results = coroutineScope {
            specs.map { spec -> async { fetchRow(spec, forceRefresh) } }.awaitAll()
        }
        rows = results.mapNotNull { it.row }
        loadFailed = results.isNotEmpty() && results.all { it.failed }
    }

    LaunchedEffect(specs, manifestUrl, reloadToken) {
        if (isLiveTv) return@LaunchedEffect
        isLoading = true
        loadAll(forceRefresh = reloadToken > 0)
        isLoading = false
    }

    // Keep the Live Now row fresh while the Sports tab is on screen.
    val screenActive = LocalScreenActive.current
    LaunchedEffect(specs, manifestUrl, screenActive) {
        if (!screenActive) return@LaunchedEffect
        val liveSpec = specs.firstOrNull { it.isLive } ?: return@LaunchedEffect
        while (true) {
            delay(SPORTS_LIVE_REFRESH_MS)
            val result = fetchRow(liveSpec, forceRefresh = true)
            if (result.failed) continue
            val current = rows
            val liveRow = result.row
            val index = current.indexOfFirst { it.spec.key == liveSpec.key }
            rows = if (liveRow == null) {
                current.filterNot { it.spec.key == liveSpec.key }
            } else if (index >= 0) {
                current.toMutableList().also { it[index] = liveRow }
            } else {
                listOf(liveRow) + current
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                try {
                    if (isLiveTv) loadLiveTvFirstPage(forceRefresh = true) else loadAll(forceRefresh = true)
                } finally {
                    isRefreshing = false
                }
            }
        },
        modifier = modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val sectionPadding = homeSectionHorizontalPaddingForWidth(maxWidth.value)
            val liveTvColumns = liveTvGridColumns(maxWidth)
            NuvioScreen(
                modifier = Modifier.fillMaxSize(),
                horizontalPadding = 0.dp,
                listState = listState,
            ) {
                item(key = "sports_header") {
                    NuvioScreenHeader(
                        title = stringResource(Res.string.compose_nav_sports),
                        modifier = Modifier.padding(horizontal = sectionPadding),
                    )
                }
                item(key = "sports_filters") {
                    SportFilterChips(
                        filters = SportFilter.entries,
                        labels = filterLabels,
                        selected = selectedFilter,
                        contentPadding = PaddingValues(horizontal = sectionPadding),
                        onSelected = { selectedFilterName = it.name },
                    )
                }
                if (recentEntries.isNotEmpty() && (isLiveTv || selectedFilter == SportFilter.All)) {
                    item(key = "sports_recent") {
                        SportsRecentSection(
                            entries = recentEntries,
                            sectionPadding = sectionPadding,
                            onClick = onPosterClick,
                        )
                    }
                }
                if (isLiveTv) {
                    liveTvContent(
                        items = liveTvItems,
                        query = liveTvQuery,
                        onQueryChange = { liveTvQuery = it },
                        showSearch = liveTvSupportsSearch,
                        isLoading = liveTvLoading,
                        isLoadingMore = liveTvLoadingMore,
                        isError = liveTvFailed,
                        hasMore = liveTvNextSkip != null,
                        columns = liveTvColumns,
                        sectionPadding = sectionPadding,
                        onLoadMore = { scope.launch { loadMoreLiveTv() } },
                        onRetry = { reloadToken++ },
                        onPosterClick = onPosterClick,
                        onPosterLongClick = onPosterLongClick,
                    )
                } else if (rows.isEmpty()) {
                    item(key = "sports_state") {
                        SportsStateMessage(
                            isLoading = isLoading,
                            isError = loadFailed,
                            onRetry = { reloadToken++ },
                        )
                    }
                } else {
                    rows.forEach { row ->
                        item(key = "sports_row_${row.spec.key}") {
                            SportsRowSection(
                                row = row,
                                sectionPadding = sectionPadding,
                                onViewAllClick = onCatalogClick?.takeIf { row.hasMore || row.items.size >= SPORTS_ROW_PREVIEW_ITEMS }
                                    ?.let { click -> { click(row.toCatalogSection(manifestUrl)) } },
                                onPosterClick = onPosterClick,
                                onPosterLongClick = onPosterLongClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildRowSpecs(
    filter: SportFilter,
    filterLabels: Map<SportFilter, String>,
    liveTitle: String,
    upcomingTitle: String,
    networksTitle: String,
    replaysTitle: String,
): List<SportsRowSpec> = buildList {
    if (filter == SportFilter.LiveTv) return@buildList
    if (filter == SportFilter.All) {
        add(SportsRowSpec(key = "live", title = liveTitle, catalogId = SportsAddon.CATALOG_LIVE, isLive = true))
        add(SportsRowSpec(key = "upcoming", title = upcomingTitle, catalogId = SportsAddon.CATALOG_UPCOMING))
        SportFilter.entries.filter { it != SportFilter.All && it != SportFilter.LiveTv }.forEach { sport ->
            val catalogId = sport.catalogId ?: return@forEach
            add(SportsRowSpec(key = "sport_${sport.sportKey}", title = filterLabels[sport].orEmpty(), catalogId = catalogId))
        }
        add(SportsRowSpec(key = "networks", title = networksTitle, catalogId = SportsAddon.CATALOG_NETWORKS))
        add(SportsRowSpec(key = "replays", title = replaysTitle, catalogId = SportsAddon.CATALOG_REPLAYS))
    } else {
        val label = filterLabels[filter].orEmpty()
        filter.genre?.let { genre ->
            add(SportsRowSpec(key = "live", title = liveTitle, catalogId = SportsAddon.CATALOG_LIVE, genre = genre, isLive = true))
            add(SportsRowSpec(key = "upcoming", title = upcomingTitle, catalogId = SportsAddon.CATALOG_UPCOMING, genre = genre))
        }
        filter.catalogId?.let { catalogId ->
            add(SportsRowSpec(key = "sport_${filter.sportKey}", title = label, catalogId = catalogId))
        }
        add(SportsRowSpec(key = "networks", title = networksTitle, catalogId = SportsAddon.CATALOG_NETWORKS))
        filter.replayCatalogId?.let { catalogId ->
            add(SportsRowSpec(key = "replays_${filter.sportKey}", title = "$replaysTitle · $label", catalogId = catalogId))
        }
    }
}

private fun SportsRow.toCatalogSection(manifestUrl: String): HomeCatalogSection =
    HomeCatalogSection(
        key = "sports:${spec.catalogId}:${spec.genre.orEmpty()}",
        title = spec.title,
        subtitle = SportsAddon.DISPLAY_NAME,
        addonName = SportsAddon.DISPLAY_NAME,
        target = CatalogTarget.Addon(
            manifestUrl = manifestUrl,
            contentType = SPORTS_CATALOG_TYPE,
            catalogId = spec.catalogId,
            genre = spec.genre,
            supportsPagination = supportsPagination,
        ),
        items = items,
        hasMore = hasMore,
    )

private val LIVE_NAME_PREFIX = Regex("^\\s*🔴\\s*LIVE\\s*[:\\-–]?\\s*", RegexOption.IGNORE_CASE)

/** Live items: anything in the Live Now row, plus items the addon marks as live elsewhere. */
private fun MetaPreview.isLiveEvent(inLiveRow: Boolean): Boolean =
    inLiveRow ||
        releaseInfo.equals("LIVE", ignoreCase = true) ||
        LIVE_NAME_PREFIX.containsMatchIn(name)

@Composable
private fun SportsRowSection(
    row: SportsRow,
    sectionPadding: androidx.compose.ui.unit.Dp,
    onViewAllClick: (() -> Unit)?,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
) {
    val posterCardStyle = rememberPosterCardStyleUiState()
    NuvioShelfSection(
        title = row.spec.title,
        entries = row.items,
        modifier = Modifier.fillMaxWidth(),
        headerHorizontalPadding = sectionPadding,
        rowContentPadding = PaddingValues(horizontal = sectionPadding),
        onViewAllClick = onViewAllClick,
        viewAllPillSize = NuvioViewAllPillSize.Compact,
        key = { item -> item.stableKey() },
    ) { item ->
        val isLive = item.isLiveEvent(inLiveRow = row.spec.isLive)
        // The live badge replaces the addon's "🔴 LIVE:" title prefix.
        val displayItem = if (isLive) {
            item.copy(name = item.name.replace(LIVE_NAME_PREFIX, "").ifBlank { item.name })
        } else {
            item
        }
        Box {
            HomePosterCard(
                item = displayItem,
                showLandscapeOverlay = false,
                useLandscapeBackdropMode = posterCardStyle.catalogLandscapeModeEnabled,
                onClick = onPosterClick?.let { { it(item) } },
                onLongClick = onPosterLongClick?.let { { it(item) } },
            )
            if (isLive) {
                LiveBadge(modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
            }
        }
    }
}

@Composable
private fun SportsRecentSection(
    entries: List<SportsRecentEntry>,
    sectionPadding: androidx.compose.ui.unit.Dp,
    onClick: ((MetaPreview) -> Unit)?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = sectionPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.sports_recent_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
            TextButton(onClick = { SportsRecentRepository.clear() }) {
                Text(text = stringResource(Res.string.sports_recent_clear))
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = sectionPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(entries, key = { "${it.type}:${it.id}" }) { entry ->
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    HomePosterCard(
                        item = entry.toMetaPreview(),
                        showLandscapeOverlay = false,
                        onClick = onClick?.let { click -> { click(entry.toMetaPreview()) } },
                        onLongClick = { menuOpen = true },
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.sports_recent_remove)) },
                            onClick = {
                                menuOpen = false
                                SportsRecentRepository.remove(entry.id, entry.type)
                            },
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun LiveBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.error)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = stringResource(Res.string.sports_live_badge),
            color = MaterialTheme.colorScheme.onError,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SportFilterChips(
    filters: List<SportFilter>,
    labels: Map<SportFilter, String>,
    selected: SportFilter,
    contentPadding: PaddingValues,
    onSelected: (SportFilter) -> Unit,
) {
    // Compact (default): one scrolling row, selected chip first. Expanded: full wrapped list.
    var expanded by rememberSaveable { mutableStateOf(false) }
    val toggle: @Composable () -> Unit = {
        SportChip(
            label = if (expanded) "Show less" else "Show all",
            isSelected = false,
            onClick = { expanded = !expanded },
            icon = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
        )
    }
    if (expanded) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filters.forEach { filter ->
                SportChip(
                    label = labels[filter].orEmpty(),
                    isSelected = filter == selected,
                    onClick = { onSelected(filter) },
                )
            }
            toggle()
        }
    } else {
        val ordered = listOf(selected) + filters.filter { it != selected }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ordered.forEach { filter ->
                SportChip(
                    label = labels[filter].orEmpty(),
                    isSelected = filter == selected,
                    onClick = { onSelected(filter) },
                )
            }
            toggle()
        }
    }
}

@Composable
private fun SportChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            backdropControlColor(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        },
        animationSpec = tween(durationMillis = 180),
        label = "sport_chip_container",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = 180),
        label = "sport_chip_content",
    )
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private fun AddonCatalog.supportsSearchExtra(): Boolean =
    extra.any { property -> property.name.equals("search", ignoreCase = true) }

/** Channel logos are landscape, so the grid is denser than the poster catalogs. */
private fun liveTvGridColumns(width: androidx.compose.ui.unit.Dp): Int =
    when {
        width >= 1200.dp -> 5
        width >= 840.dp -> 4
        width >= 600.dp -> 3
        else -> 2
    }

private fun androidx.compose.foundation.lazy.LazyListScope.liveTvContent(
    items: List<MetaPreview>,
    query: String,
    onQueryChange: (String) -> Unit,
    showSearch: Boolean,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    isError: Boolean,
    hasMore: Boolean,
    columns: Int,
    sectionPadding: androidx.compose.ui.unit.Dp,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
) {
    if (showSearch) {
        item(key = "live_tv_search") {
            NuvioInputField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = stringResource(Res.string.sports_live_tv_search_placeholder),
                modifier = Modifier.padding(horizontal = sectionPadding, vertical = 4.dp),
            )
        }
    }
    if (items.isEmpty()) {
        item(key = "live_tv_state") {
            if (isLoading || isError) {
                SportsStateMessage(isLoading = isLoading, isError = isError, onRetry = onRetry)
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.sports_live_tv_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            }
        }
        return
    }
    val gridRows = items.chunked(columns.coerceAtLeast(1))
    gridRows.forEachIndexed { index, rowItems ->
        item(key = "live_tv_row_${rowItems.first().stableKey()}") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sectionPadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowItems.forEach { item ->
                    Box(modifier = Modifier.weight(1f)) {
                        HomePosterCard(
                            item = item,
                            showLandscapeOverlay = false,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onPosterClick?.let { { it(item) } },
                            onLongClick = onPosterLongClick?.let { { it(item) } },
                        )
                    }
                }
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            // Reaching the last rows loads the next page.
            if (hasMore && index >= gridRows.lastIndex - 1) {
                LaunchedEffect(items.size) { onLoadMore() }
            }
        }
    }
    if (isLoadingMore) {
        item(key = "live_tv_loading_more") {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                NuvioLoadingIndicator()
            }
        }
    }
}

@Composable
private fun SportsStateMessage(
    isLoading: Boolean,
    isError: Boolean,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoading -> NuvioLoadingIndicator()
            isError -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(Res.string.sports_error_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                TextButton(onClick = onRetry) {
                    Text(text = stringResource(Res.string.sports_retry))
                }
            }
            else -> Text(
                text = stringResource(Res.string.sports_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}
