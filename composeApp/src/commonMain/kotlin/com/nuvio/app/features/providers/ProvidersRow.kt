package com.nuvio.app.features.providers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
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

/** Home tile: the brand tile ([ProviderLogo], 160x90) with the service name underneath. */
@Composable
internal fun ProviderLogoTile(
    provider: StreamingProvider,
    onClick: () -> Unit,
    width: Dp = 160.dp,
) {
    Column(
        modifier = Modifier.width(width),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ProviderLogo(
            provider = provider,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            onClick = onClick,
        )
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

/**
 * Streaming provider tile artwork: 16:9, 12dp corners, brand gradient (145deg, brand -> 58% brand +
 * 42% #050505) with a soft radial highlight, and the rectangular wordmark centred on top (max 78% x
 * 52%, Fit, drop shadow). No grey surface behind it. Falls back to the TMDB square icon, then to the
 * provider name, when the wordmark can't be loaded.
 */
@Composable
internal fun ProviderLogo(
    provider: StreamingProvider,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    onClick: (() -> Unit)? = null,
) {
    val brand = providerBrand(provider.id)
    val shape = RoundedCornerShape(cornerRadius)
    // 58% brand + 42% #050505.
    val gradientEnd = remember(brand.color) { lerp(Color(0xFF050505), brand.color, 0.58f) }
    var wordmarkFailed by remember(brand.wordmarkUrl) { mutableStateOf(brand.wordmarkUrl.isBlank()) }
    var tmdbFailed by remember(provider.logoUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind { drawBrandBackground(brand.color, gradientEnd) }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val tmdbLogo = provider.logoUrl
        when {
            !wordmarkFailed -> {
                val box = Modifier.fillMaxWidth(0.78f).fillMaxHeight(0.52f)
                // Drop shadow (black 45%): a blurred, offset black copy of the same wordmark.
                AsyncImage(
                    model = brand.wordmarkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.45f), BlendMode.SrcIn),
                    modifier = box
                        .offset(y = 1.dp)
                        .blur(1.5.dp, BlurredEdgeTreatment.Unbounded),
                )
                AsyncImage(
                    model = brand.wordmarkUrl,
                    contentDescription = provider.name,
                    contentScale = ContentScale.Fit,
                    colorFilter = if (brand.tintWhite) ColorFilter.tint(Color.White, BlendMode.SrcIn) else null,
                    onError = { wordmarkFailed = true },
                    modifier = box,
                )
            }

            tmdbLogo != null && !tmdbFailed -> AsyncImage(
                model = tmdbLogo,
                contentDescription = provider.name,
                contentScale = ContentScale.Fit,
                onError = { tmdbFailed = true },
                modifier = Modifier.fillMaxWidth(0.78f).fillMaxHeight(0.7f),
            )

            else -> Text(
                text = provider.name,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

/**
 * linear-gradient(145deg, [start], [end]) plus radial-gradient(ellipse at 80% -10%,
 * rgba(255,255,255,.18), transparent 55%), as in the web reference.
 */
private fun DrawScope.drawBrandBackground(start: Color, end: Color) {
    // CSS angles: 0deg points up, 90deg right; the gradient line passes through the centre with
    // length |w*sin| + |h*cos| so the corners get exactly the first/last colour.
    val angle = 145.0 * PI / 180.0
    val dx = sin(angle).toFloat()
    val dy = -cos(angle).toFloat()
    val length = abs(size.width * dx) + abs(size.height * dy)
    val half = Offset(dx * length / 2f, dy * length / 2f)
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(start, end),
            start = center - half,
            end = center + half,
        ),
    )

    // Farthest-corner ellipse at (80%, -10%): radii = farthest-side distances (0.8w, 1.1h) * sqrt(2).
    // Compose only has circular radial gradients, so draw a circle squashed vertically by ry/rx.
    val highlightCenter = Offset(size.width * 0.8f, -size.height * 0.1f)
    val rx = size.width * 0.8f * 1.4142135f
    val ry = size.height * 1.1f * 1.4142135f
    withTransform({ scale(scaleX = 1f, scaleY = ry / rx, pivot = highlightCenter) }) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.18f),
                0.55f to Color.White.copy(alpha = 0f),
                center = highlightCenter,
                radius = rx,
            ),
            radius = rx,
            center = highlightCenter,
        )
    }
}
