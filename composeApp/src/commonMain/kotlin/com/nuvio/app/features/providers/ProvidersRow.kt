package com.nuvio.app.features.providers

import androidx.compose.foundation.Image
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
import org.jetbrains.compose.resources.painterResource
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
 * Streaming provider tile artwork: 16:9, 12dp corners, the provider's own background (solid / linear /
 * radial, see [ProviderBrand]) plus a faint white radial highlight, and the bundled logo centred on
 * top (max 78% x 52%, Fit, soft shadow). No grey surface behind it. Providers without a bundled logo
 * fall back to the TMDB square icon, then to the provider name.
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
    var tmdbFailed by remember(provider.logoUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind { drawBrandBackground(brand.background) }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val tmdbLogo = provider.logoUrl
        val logo = brand.logo
        when {
            logo != null -> {
                val box = Modifier.fillMaxWidth(0.78f).fillMaxHeight(0.52f)
                // Soft drop shadow (black 45%): a blurred, offset black copy of the same logo.
                Image(
                    painter = painterResource(logo),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.45f), BlendMode.SrcIn),
                    modifier = box
                        .offset(y = 2.dp)
                        .blur(3.dp, BlurredEdgeTreatment.Unbounded),
                )
                Image(
                    painter = painterResource(logo),
                    contentDescription = provider.name,
                    contentScale = ContentScale.Fit,
                    colorFilter = if (brand.tintWhite) ColorFilter.tint(Color.White, BlendMode.SrcIn) else null,
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

/** Fills the tile with [background], then the faint white highlight common to every tile. */
private fun DrawScope.drawBrandBackground(background: ProviderBackground) {
    when (background) {
        is ProviderBackground.Solid -> drawRect(background.color)
        is ProviderBackground.Linear -> {
            // CSS angles: 0deg points up, 90deg right; the gradient line passes through the centre with
            // length |w*sin| + |h*cos| so the corners get exactly the first/last colour.
            val angle = background.angleDeg * PI / 180.0
            val dx = sin(angle).toFloat()
            val dy = -cos(angle).toFloat()
            val length = abs(size.width * dx) + abs(size.height * dy)
            val half = Offset(dx * length / 2f, dy * length / 2f)
            drawRect(
                brush = Brush.linearGradient(
                    *background.stops.toTypedArray(),
                    start = center - half,
                    end = center + half,
                ),
            )
        }

        is ProviderBackground.Radial -> drawEllipse(
            center = Offset(size.width * background.cx, size.height * background.cy),
            rx = size.width * background.rxFraction,
            ry = size.height * background.ryFraction,
            stops = background.stops,
        )
    }

    // radial-gradient(120% 80% at 80% -10%, rgba(255,255,255,.10), transparent 55%)
    drawEllipse(
        center = Offset(size.width * 0.8f, -size.height * 0.1f),
        rx = size.width * 1.2f,
        ry = size.height * 0.8f,
        stops = listOf(0f to Color.White.copy(alpha = 0.10f), 0.55f to Color.White.copy(alpha = 0f)),
    )
}

/**
 * Elliptical radial gradient (radii [rx], [ry], colour [stops] at 0..1 along the radius). Compose only
 * has circular radial gradients, so draw a circle of radius [rx] squashed vertically by ry/rx.
 */
private fun DrawScope.drawEllipse(center: Offset, rx: Float, ry: Float, stops: List<Pair<Float, Color>>) {
    withTransform({ scale(scaleX = 1f, scaleY = ry / rx, pivot = center) }) {
        drawCircle(
            brush = Brush.radialGradient(
                *stops.toTypedArray(),
                center = center,
                radius = rx,
            ),
            radius = rx,
            center = center,
        )
    }
}
