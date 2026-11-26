package com.nuvio.app.features.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.providers.ProvidersRepository
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.home_country_row_title
import org.jetbrains.compose.resources.stringResource

/** Origin countries offered as Home rows: TMDB `with_origin_country` code and display name. */
internal val HomeCountryCatalogs: List<Pair<String, String>> = listOf(
    "IN" to "India",
    "ID" to "Indonesia",
    "FR" to "France",
    "NG" to "Nigeria",
    "ZA" to "South Africa",
    "KE" to "Kenya",
)

/** One poster rail per country, each loaded lazily when it first scrolls into view. */
@Composable
internal fun HomeCountryRow(
    countryCode: String,
    countryName: String,
    sectionPadding: Dp,
    onPosterClick: ((MetaPreview) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var items by remember(countryCode) { mutableStateOf<List<MetaPreview>>(emptyList()) }
    LaunchedEffect(countryCode) {
        items = runCatching { ProvidersRepository.discoverByOriginCountry(countryCode) }.getOrDefault(emptyList())
    }
    if (items.isEmpty()) return
    val title = stringResource(Res.string.home_country_row_title, countryName)
    val section = remember(countryCode, title, items) {
        HomeCatalogSection(
            key = "country_$countryCode",
            title = title,
            subtitle = "",
            addonName = "TMDB",
            target = CatalogTarget.Addon(
                manifestUrl = "",
                contentType = "movie",
                catalogId = "country_$countryCode",
            ),
            items = items,
        )
    }
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        HomeCatalogRowSection(
            section = section,
            entries = items,
            sectionPadding = sectionPadding,
            onViewAllClick = null,
            onPosterClick = onPosterClick,
        )
    }
}
