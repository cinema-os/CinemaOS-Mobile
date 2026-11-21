package com.nuvio.app.features.details

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.poster.CustomPosterScreen
import com.nuvio.app.core.poster.CustomPosterUrlRepository
import com.nuvio.app.core.poster.withCustomPosterUrls
import com.nuvio.app.core.ui.NuvioBackButton
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.core.ui.withDuplicateSafeLazyKeys
import com.nuvio.app.features.catalog.CatalogPosterTile
import com.nuvio.app.features.catalog.catalogGridColumnsForWidth
import com.nuvio.app.features.details.components.ExpandableDescription
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.HomeEmptyStateCard
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.tmdb.TmdbCollectionDetail
import com.nuvio.app.features.tmdb.TmdbMetadataService
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watching.application.WatchingState
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private sealed interface MovieCollectionUiState {
    data object Loading : MovieCollectionUiState
    data object Error : MovieCollectionUiState
    data class Success(val detail: TmdbCollectionDetail) : MovieCollectionUiState
}

/**
 * Full page for a TMDB movie collection (franchise): name, overview, backdrop and a grid of every
 * movie in release order. Opened from the collection row header on a movie's details page.
 */
@Composable
fun MovieCollectionScreen(
    collectionId: Int,
    collectionName: String,
    onBack: () -> Unit,
    onOpenMeta: (MetaPreview) -> Unit,
    modifier: Modifier = Modifier,
) {
    var reloadToken by remember { mutableIntStateOf(0) }
    var uiState by remember(collectionId) { mutableStateOf<MovieCollectionUiState>(MovieCollectionUiState.Loading) }
    val posterCardStyle = rememberPosterCardStyleUiState()
    val watchedUiState by remember {
        WatchedRepository.ensureLoaded()
        WatchedRepository.uiState
    }.collectAsStateWithLifecycle()
    val fullyWatchedSeriesKeys by WatchedRepository.fullyWatchedSeriesKeys.collectAsStateWithLifecycle()

    LaunchedEffect(collectionId, reloadToken) {
        uiState = MovieCollectionUiState.Loading
        val detail = TmdbMetadataService.fetchCollectionDetail(
            collectionId = collectionId,
            fallbackName = collectionName,
        )
        uiState = if (detail != null) {
            val pattern = CustomPosterUrlRepository.let { repo ->
                repo.ensureLoaded()
                repo.patternForScreen(CustomPosterScreen.DETAILS)
            }
            MovieCollectionUiState.Success(
                detail.copy(items = detail.items.withCustomPosterUrls(pattern)),
            )
        } else {
            MovieCollectionUiState.Error
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Crossfade(targetState = uiState, label = "MovieCollectionCrossfade") { state ->
            when (state) {
                MovieCollectionUiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    NuvioLoadingIndicator(modifier = Modifier.size(28.dp))
                }

                MovieCollectionUiState.Error -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    HomeEmptyStateCard(
                        title = stringResource(Res.string.details_browse_load_failed, collectionName),
                        message = "",
                        actionLabel = stringResource(Res.string.action_retry),
                        onActionClick = { reloadToken += 1 },
                    )
                }

                is MovieCollectionUiState.Success -> MovieCollectionContent(
                    detail = state.detail,
                    cornerRadiusDp = posterCardStyle.cornerRadiusDp,
                    hideLabels = posterCardStyle.hideLabelsEnabled,
                    watchedKeys = watchedUiState.watchedKeys,
                    fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                    onOpenMeta = onOpenMeta,
                )
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
private fun MovieCollectionContent(
    detail: TmdbCollectionDetail,
    cornerRadiusDp: Int,
    hideLabels: Boolean,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    onOpenMeta: (MetaPreview) -> Unit,
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val backgroundUrl = detail.backdrop ?: detail.poster

    Box(modifier = Modifier.fillMaxSize()) {
        if (backgroundUrl != null) {
            AsyncImage(
                model = backgroundUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.22f,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                        0.35f to MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                        1f to MaterialTheme.colorScheme.background,
                    ),
                ),
        )

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val columns = remember(maxWidth) { catalogGridColumnsForWidth(maxWidth) }
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
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
                item(span = { GridItemSpan(maxLineSpan) }) {
                    MovieCollectionHeader(detail = detail)
                }
                items(
                    items = detail.items.withDuplicateSafeLazyKeys { item -> item.stableKey() },
                    key = { item -> item.lazyKey },
                ) { keyedItem ->
                    val item = keyedItem.value
                    CatalogPosterTile(
                        item = item,
                        cornerRadiusDp = cornerRadiusDp,
                        hideLabels = hideLabels,
                        isWatched = WatchingState.isPosterWatched(
                            watchedKeys = watchedKeys,
                            item = item,
                            fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                        ),
                        onClick = { onOpenMeta(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MovieCollectionHeader(detail: TmdbCollectionDetail) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = detail.name,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                lineHeight = 34.sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = pluralStringResource(
                Res.plurals.entity_browse_title_count,
                detail.items.size,
                detail.items.size,
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        detail.overview?.let { overview ->
            ExpandableDescription(
                text = overview,
                collapsedMaxLines = 4,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
