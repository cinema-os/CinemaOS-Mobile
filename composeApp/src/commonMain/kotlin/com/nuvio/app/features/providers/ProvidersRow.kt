package com.nuvio.app.features.providers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioShelfSection
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.providers_row_title
import org.jetbrains.compose.resources.stringResource

/** Home row of rounded streaming-service logo tiles (mirrors the CinemaOS website providers strip). */
@Composable
fun HomeProvidersRow(
    sectionPadding: Dp,
    onProviderClick: (StreamingProvider) -> Unit,
    modifier: Modifier = Modifier,
) {
    var providers by remember { mutableStateOf(ProvidersRepository.cachedProvidersWithLogos()) }
    LaunchedEffect(Unit) {
        providers = runCatching { ProvidersRepository.providersWithLogos() }.getOrDefault(providers)
    }
    NuvioShelfSection(
        title = stringResource(Res.string.providers_row_title),
        entries = providers,
        modifier = modifier.fillMaxWidth(),
        headerHorizontalPadding = sectionPadding,
        rowContentPadding = PaddingValues(horizontal = sectionPadding),
        itemSpacing = 14.dp,
        key = { provider -> provider.id },
    ) { provider ->
        ProviderLogoTile(provider = provider, onClick = { onProviderClick(provider) })
    }
}

@Composable
internal fun ProviderLogoTile(
    provider: StreamingProvider,
    onClick: () -> Unit,
    size: Dp = 72.dp,
) {
    Column(
        modifier = Modifier
            .width(size + 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProviderLogo(provider = provider, size = size)
        Text(
            text = provider.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun ProviderLogo(
    provider: StreamingProvider,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(size * 0.3f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), shape),
        contentAlignment = Alignment.Center,
    ) {
        val logo = provider.logoUrl
        if (logo != null) {
            AsyncImage(
                model = logo,
                contentDescription = provider.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = provider.name.take(6).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}
