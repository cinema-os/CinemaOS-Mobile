package com.nuvio.app.features.providers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.core.ui.NuvioBackButton
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.core.ui.withDuplicateSafeLazyKeys
import com.nuvio.app.features.catalog.CatalogPosterTile
import com.nuvio.app.features.catalog.catalogGridColumnsForWidth
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.HomeEmptyStateCard
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watching.application.WatchingState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_retry
import nuvio.composeapp.generated.resources.providers_empty_message
import nuvio.composeapp.generated.resources.providers_empty_title
import nuvio.composeapp.generated.resources.providers_load_failed
import nuvio.composeapp.generated.resources.providers_region
import nuvio.composeapp.generated.resources.providers_sort_newest
import nuvio.composeapp.generated.resources.providers_sort_popular
import nuvio.composeapp.generated.resources.providers_sort_top_rated
import nuvio.composeapp.generated.resources.providers_streaming_on
import nuvio.composeapp.generated.resources.providers_tab_movies
import nuvio.composeapp.generated.resources.providers_tab_series
import org.jetbrains.compose.resources.stringResource

private data class ProviderGridState(
    val items: List<MetaPreview> = emptyList(),
    val nextPage: Int = 1,
    val hasMore: Boolean = true,
    val isLoading: Boolean = false,
    val failed: Boolean = false,
)

/**
 * Provider page: logo + name header, Movies/Series tabs, sort and an infinite grid backed by
 * TMDB `/discover/{movie|tv}?with_watch_providers=..&watch_region=..`.
 */
@Composable
fun ProviderScreen(
    providerId: Int,
    providerName: String,
    logoPath: String?,
    onBack: () -> Unit,
    onOpenMeta: (MetaPreview) -> Unit,
    modifier: Modifier = Modifier,
) {
    val region = remember { deviceWatchRegion() }
    var isTv by rememberSaveable(providerId) { mutableStateOf(false) }
    var sortName by rememberSaveable(providerId) { mutableStateOf(ProviderSort.Popular.name) }
    val sort = ProviderSort.entries.firstOrNull { it.name == sortName } ?: ProviderSort.Popular
    var reloadToken by remember { mutableIntStateOf(0) }
    var state by remember(providerId) { mutableStateOf(ProviderGridState()) }
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val posterCardStyle = rememberPosterCardStyleUiState()
    val watchedUiState by remember {
        WatchedRepository.ensureLoaded()
        WatchedRepository.uiState
    }.collectAsStateWithLifecycle()
    val fullyWatchedSeriesKeys by WatchedRepository.fullyWatchedSeriesKeys.collectAsStateWithLifecycle()
    val provider = remember(providerId, logoPath) {
        val known = ProvidersRepository.cachedProvidersWithLogos().firstOrNull { it.id == providerId }
        StreamingProvider(id = providerId, name = providerName, logoPath = logoPath ?: known?.logoPath)
    }

    fun loadMore() {
        val current = state
        if (current.isLoading || !current.hasMore) return
        val key = Triple(isTv, sortName, reloadToken)
        state = current.copy(isLoading = true, failed = false)
        scope.launch {
            val result = runCatching {
                ProvidersRepository.discover(
                    providerId = providerId,
                    isTv = key.first,
                    sort = ProviderSort.entries.firstOrNull { it.name == key.second } ?: ProviderSort.Popular,
                    page = current.nextPage,
                    region = region,
                )
            }
            // Drop the result if the tab or sort changed while it was loading.
            if (key != Triple(isTv, sortName, reloadToken)) return@launch
            state = result.fold(
                onSuccess = { page ->
                    state.copy(
                        items = (state.items + page.items).distinctBy { it.stableKey() },
                        nextPage = current.nextPage + 1,
                        hasMore = page.hasMore,
                        isLoading = false,
                    )
                },
                onFailure = { state.copy(isLoading = false, failed = true) },
            )
        }
    }

    LaunchedEffect(providerId, isTv, sortName, reloadToken) {
        state = ProviderGridState()
        gridState.scrollToItem(0)
        loadMore()
    }

    LaunchedEffect(gridState, state.items.size, state.hasMore) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 8
        }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd && state.items.isNotEmpty()) loadMore() }
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val brandColor = providerBrand(providerId).color

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    Brush.verticalGradient(
                        0f to brandColor.copy(alpha = 0.35f),
                        1f to brandColor.copy(alpha = 0f),
                    ),
                ),
        )
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val columns = remember(maxWidth) { catalogGridColumnsForWidth(maxWidth) }
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = statusTop + 56.dp,
                    end = 16.dp,
                    bottom = nuvioSafeBottomPadding(28.dp),
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "provider_header") {
                    ProviderHeader(
                        provider = provider,
                        region = region,
                        isTv = isTv,
                        sort = sort,
                        onTabChange = { tv -> if (tv != isTv) isTv = tv },
                        onSortChange = { next -> sortName = next.name },
                    )
                }
                items(
                    items = state.items.withDuplicateSafeLazyKeys { item -> item.stableKey() },
                    key = { item -> item.lazyKey },
                ) { keyedItem ->
                    val item = keyedItem.value
                    CatalogPosterTile(
                        item = item,
                        cornerRadiusDp = posterCardStyle.cornerRadiusDp,
                        hideLabels = posterCardStyle.hideLabelsEnabled,
                        isWatched = WatchingState.isPosterWatched(
                            watchedKeys = watchedUiState.watchedKeys,
                            item = item,
                            fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                        ),
                        onClick = { onOpenMeta(item) },
                    )
                }
                when {
                    state.isLoading -> item(span = { GridItemSpan(maxLineSpan) }, key = "provider_loading") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            NuvioLoadingIndicator(modifier = Modifier.size(28.dp))
                        }
                    }

                    state.failed -> item(span = { GridItemSpan(maxLineSpan) }, key = "provider_failed") {
                        HomeEmptyStateCard(
                            title = stringResource(Res.string.providers_load_failed, provider.name),
                            message = "",
                            actionLabel = stringResource(Res.string.action_retry),
                            onActionClick = {
                                if (state.items.isEmpty()) reloadToken += 1 else loadMore()
                            },
                        )
                    }

                    state.items.isEmpty() && !state.hasMore -> item(
                        span = { GridItemSpan(maxLineSpan) },
                        key = "provider_empty",
                    ) {
                        HomeEmptyStateCard(
                            title = stringResource(Res.string.providers_empty_title),
                            message = stringResource(Res.string.providers_empty_message),
                        )
                    }
                }
            }
        }

        NuvioBackButton(
            onClick = onBack,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 12.dp, top = 4.dp)
                .align(Alignment.TopStart),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            iconSize = 24.dp,
        )
    }
}

@Composable
private fun ProviderHeader(
    provider: StreamingProvider,
    region: String,
    isTv: Boolean,
    sort: ProviderSort,
    onTabChange: (Boolean) -> Unit,
    onSortChange: (ProviderSort) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Same brand tile as the Home row (gradient + wordmark, no grey container), 16:9.
            ProviderLogo(provider = provider, modifier = Modifier.size(width = 128.dp, height = 72.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.providers_streaming_on).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(Res.string.providers_region, region),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ProviderTabs(isTv = isTv, brandColor = providerBrand(provider.id).color, onTabChange = onTabChange)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProviderSort.entries.forEach { option ->
                Chip(
                    label = stringResource(
                        when (option) {
                            ProviderSort.Popular -> Res.string.providers_sort_popular
                            ProviderSort.TopRated -> Res.string.providers_sort_top_rated
                            ProviderSort.Newest -> Res.string.providers_sort_newest
                        },
                    ),
                    selected = option == sort,
                    onClick = { onSortChange(option) },
                )
            }
        }
    }
}

@Composable
private fun ProviderTabs(
    isTv: Boolean,
    brandColor: Color,
    onTabChange: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(brandColor.copy(alpha = 0.28f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(false to Res.string.providers_tab_movies, true to Res.string.providers_tab_series).forEach { (tv, label) ->
            val selected = tv == isTv
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(
                        if (selected) lerp(brandColor, Color.White, 0.22f) else Color.Transparent,
                    )
                    .clickable { onTabChange(tv) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) Color.White else Color.White.copy(alpha = 0.75f),
                )
            }
        }
    }
}
