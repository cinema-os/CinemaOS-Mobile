package com.nuvio.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.PaddingValues
import com.nuvio.app.features.streams.SourceLanguageFilterRow
import com.nuvio.app.features.streams.withLanguageFilter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.StreamsUiState
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_close
import nuvio.composeapp.generated.resources.compose_player_episode_code_full
import nuvio.composeapp.generated.resources.compose_player_panel_sources
import nuvio.composeapp.generated.resources.collections_tab_all
import nuvio.composeapp.generated.resources.compose_player_playing
import nuvio.composeapp.generated.resources.streams_refresh
import org.jetbrains.compose.resources.stringResource

@Composable
fun PlayerSourcesPanel(
    visible: Boolean,
    streamsUiState: StreamsUiState,
    contentTitle: String,
    currentSeason: Int?,
    currentEpisode: Int?,
    currentEpisodeTitle: String?,
    currentStream: PlayerCurrentStream?,
    onFilterSelected: (String?) -> Unit,
    onStreamSelected: (StreamItem) -> Unit,
    onReload: () -> Unit,
    onDownloadStream: (StreamItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val contentLabel = if (currentSeason != null && currentEpisode != null) {
        buildString {
            append(stringResource(Res.string.compose_player_episode_code_full, currentSeason, currentEpisode))
            currentEpisodeTitle?.takeIf { it.isNotBlank() }?.let {
                append(" • ")
                append(it)
            }
        }
    } else {
        contentTitle
    }

    // Drop a provider filter whose source vanished (as the filter row does), so the list never goes empty.
    LaunchedEffect(streamsUiState.groups, streamsUiState.selectedFilter) {
        val selected = streamsUiState.selectedFilter ?: return@LaunchedEffect
        if (streamsUiState.groups.none { it.addonId == selected && (it.streams.isNotEmpty() || it.isLoading) }) {
            onFilterSelected(null)
        }
    }

    PlayerOverlayScaffold(
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 44.dp, vertical = 28.dp),
        ) {
            PlayerPanelHeader(
                title = stringResource(Res.string.compose_player_panel_sources),
            ) {
                PlayerDialogButton(
                    label = stringResource(Res.string.streams_refresh),
                    onClick = onReload,
                )
                PlayerDialogButton(
                    label = stringResource(Res.string.action_close),
                    onClick = onDismiss,
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = contentLabel,
                color = tokens.colors.textSecondary,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(12.dp))

            var languageFilter by remember { mutableStateOf<String?>(null) }
            SourceLanguageFilterRow(
                uiState = streamsUiState,
                selected = languageFilter,
                onSelected = { languageFilter = it },
                contentPadding = PaddingValues(vertical = 4.dp),
            )
            val languageFilteredState = streamsUiState.withLanguageFilter(languageFilter)

            // Two columns like the TV and phone apps: sources on the left, the links of the selected one on the right.
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val providerColumnWidth = if (maxWidth < 520.dp) 112.dp else 164.dp
                Row(modifier = Modifier.fillMaxSize()) {
                    PlayerSourceProviderColumn(
                        streamsUiState = streamsUiState,
                        onFilterSelected = onFilterSelected,
                        modifier = Modifier.width(providerColumnWidth).fillMaxHeight(),
                    )
                    Spacer(Modifier.width(8.dp))
                    PlayerStreamList(
                        streamsUiState = languageFilteredState,
                        onStreamSelected = onStreamSelected,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        currentStream = currentStream,
                        currentLabel = stringResource(Res.string.compose_player_playing),
                        onDownloadStream = onDownloadStream,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerSourceProviderColumn(
    streamsUiState: StreamsUiState,
    onFilterSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = streamsUiState.groups.filter { it.streams.isNotEmpty() || it.isLoading }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PlayerSourceProviderItem(
            label = stringResource(Res.string.collections_tab_all),
            trailing = streamsUiState.groups.sumOf { it.streams.size }.takeIf { it > 0 }?.toString(),
            isSelected = streamsUiState.selectedFilter == null,
            isLoading = false,
            hasError = false,
            onClick = { onFilterSelected(null) },
        )
        groups.forEach { group ->
            PlayerSourceProviderItem(
                label = group.addonName,
                trailing = group.streams.size.takeIf { it > 0 }?.toString(),
                isSelected = streamsUiState.selectedFilter == group.addonId,
                isLoading = group.isLoading,
                hasError = group.error != null && group.streams.isEmpty(),
                onClick = { onFilterSelected(group.addonId) },
            )
        }
    }
}

@Composable
private fun PlayerSourceProviderItem(
    label: String,
    trailing: String?,
    isSelected: Boolean,
    isLoading: Boolean,
    hasError: Boolean,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) tokens.colors.surfaceCard else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = if (isSelected) tokens.colors.textPrimary else tokens.colors.textSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val badge = when {
            isLoading -> "\u2026"
            hasError -> "!"
            else -> trailing
        }
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = badge,
                color = tokens.colors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

internal fun List<StreamItem>.stablePlayerKeys(): List<String> {
    val occurrences = mutableMapOf<String, Int>()
    return map { stream ->
        val base = listOf(
            stream.addonId,
            stream.infoHash ?: stream.clientResolve?.infoHash ?: stream.url ?: stream.externalUrl ?: stream.streamLabel,
            stream.fileIdx ?: stream.clientResolve?.fileIdx ?: -1,
        ).joinToString("::")
        val count = occurrences[base] ?: 0
        occurrences[base] = count + 1
        "$base::$count"
    }
}

data class PlayerCurrentStream(
    val url: String? = null,
    val name: String? = null,
    val description: String? = null,
    val addonId: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val filename: String? = null,
)

internal fun List<StreamItem>.currentPlayerStreamIndex(current: PlayerCurrentStream?): Int {
    if (current == null) return -1
    val isSameAddon = { stream: StreamItem ->
        current.addonId.isNullOrBlank() || stream.addonId == current.addonId
    }

    if (!current.infoHash.isNullOrBlank()) {
        val hashMatch = indexOfFirst { stream ->
            stream.p2pInfoHash.equals(current.infoHash, ignoreCase = true) &&
                (current.fileIdx == null || stream.p2pFileIdx == current.fileIdx) &&
                isSameAddon(stream)
        }
        if (hashMatch >= 0) return hashMatch
    }

    if (!current.url.isNullOrBlank()) {
        val urlMatch = indexOfFirst { stream -> stream.playableDirectUrl == current.url }
        if (urlMatch >= 0) return urlMatch
    }

    // Addons may issue a new URL per request, so match what identifies the release instead.
    // Filenames and templates can repeat across streams, so only trust a single result.
    if (!current.filename.isNullOrBlank()) {
        val filenameMatches = indices.filter { index ->
            val stream = this[index]
            (stream.behaviorHints.filename ?: stream.clientResolve?.filename) == current.filename &&
                isSameAddon(stream)
        }
        filenameMatches.singleOrNull()?.let { return it }
    }

    if (!current.name.isNullOrBlank()) {
        val nameMatches = indices.filter { index ->
            val stream = this[index]
            stream.streamLabel.equals(current.name, ignoreCase = true) &&
                isSameAddon(stream) &&
                (current.description == null || stream.streamSubtitle == current.description)
        }
        nameMatches.singleOrNull()?.let { return it }
    }

    return -1
}
