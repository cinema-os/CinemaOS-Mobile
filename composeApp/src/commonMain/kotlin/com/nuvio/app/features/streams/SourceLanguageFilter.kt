package com.nuvio.app.features.streams

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.settings.CinemaSettingsRepository

/** Audio language codes present in the loaded streams, the preferred language first. */
internal fun StreamsUiState.sourceLanguageCodes(): List<String> {
    val seen = LinkedHashSet<String>()
    for (group in groups) for (stream in group.streams) seen += SourceLanguage.of(stream).codes
    val preferred = CinemaSettingsRepository.preferredAudioLanguage.value
    return seen.sortedWith(compareBy({ if (it == preferred) 0 else 1 }, { SourceLanguage.labelOf(it) }))
}

/** Keeps only streams carrying [code]; null keeps everything. Streams with no detected language are kept out when filtering. */
internal fun StreamsUiState.withLanguageFilter(code: String?): StreamsUiState {
    if (code == null) return this
    return copy(
        groups = groups.map { group ->
            group.copy(streams = group.streams.filter { code in SourceLanguage.of(it).codes })
        },
    )
}

/** Chips "All languages" + one per audio language found in the loaded sources. Hidden with fewer than two languages. */
@Composable
internal fun SourceLanguageFilterRow(
    uiState: StreamsUiState,
    selected: String?,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
) {
    val codes = remember(uiState.groups) { uiState.sourceLanguageCodes() }
    LaunchedEffect(codes, selected) {
        if (selected != null && selected !in codes) onSelected(null)
    }
    if (codes.size < 2) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(label = "All languages", isSelected = selected == null, onClick = { onSelected(null) })
        codes.forEach { code ->
            FilterChip(
                label = SourceLanguage.labelOf(code),
                isSelected = selected == code,
                onClick = { onSelected(code) },
            )
        }
    }
}
