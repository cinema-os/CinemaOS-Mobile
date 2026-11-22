package com.nuvio.app.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.hazeEffect
import org.jetbrains.compose.resources.painterResource
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.glass.GlassBarSurface
import com.nuvio.app.core.ui.jelly.JellyMotion
import com.nuvio.app.core.ui.jelly.JellyTabRow
import com.nuvio.app.core.ui.jelly.JellyTabTargets
import com.nuvio.app.core.ui.jelly.drawJellyGlow
import com.nuvio.app.core.ui.jelly.drawJellyPill
import com.nuvio.app.core.ui.jelly.jellyPillPath
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints

internal actual val floatingNavigationGlowSupported: Boolean
    get() = true

@Composable
internal actual fun FloatingNavigationBar(
    items: List<FloatingNavigationItem>,
    modifier: Modifier,
    scrollState: NuvioNavBarScrollState?,
    hazeState: HazeState?,
    contentPadding: PaddingValues,
    compactSize: Boolean,
    glowEnabled: Boolean,
) {
    if (items.isEmpty()) return
    // CinemaOS dock: black glass capsule. One white pill slides between tabs and grows to fit the
    // active tab's name; the other tabs stay as quiet icons. The whole bar is a single custom layout
    // driven by ONE spring ("position"), so a tab switch only re-measures/re-draws (no recomposition
    // per frame) and every slot, the pill and the label are sized from the same animated value.
    val dockHeight = if (compactSize) 52.dp else 60.dp
    val slotHeight = if (compactSize) 40.dp else 46.dp
    val iconSize = if (compactSize) 22.dp else 24.dp
    val capsule = RoundedCornerShape(50)
    val pillShape = RoundedCornerShape(50)
    val colors = MaterialTheme.colorScheme
    val dockSurface = colors.surface
    val dockBorder = colors.onSurface
    val dockShadow = colors.scrim
    val pillColor = colors.primary
    val onPillColor = colors.onPrimary
    val idleTint = colors.onSurfaceVariant

    val selectedIndex = items.indexOfFirst { it.selected }
    val position = remember { Animatable(max(selectedIndex, 0).toFloat()) }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) {
            position.animateTo(
                selectedIndex.toFloat(),
                spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
            )
        }
    }
    val selectionStrength = animateFloatAsState(
        targetValue = if (selectedIndex >= 0) 1f else 0f,
        animationSpec = tween(180),
        label = "dock_selection",
    )
    // Nav bar style: Expanded keeps the active tab name, Compact shows icons only, Adaptive follows scrolling.
    val labelVisibility = animateFloatAsState(
        targetValue = scrollState?.labelVisibility ?: 1f,
        animationSpec = tween(220),
        label = "dock_labels",
    )

    val count = items.size
    // Coverage 0..1 of tab [index] by the pill, derived from the single spring position.
    val coverage: (Int) -> Float = remember(count) {
        { index ->
            val pos = position.value.coerceIn(0f, (count - 1).toFloat())
            (1f - abs(pos - index)).coerceIn(0f, 1f) * selectionStrength.value
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Layout(
            modifier = Modifier
                .height(dockHeight)
                .shadow(14.dp, capsule, clip = false, ambientColor = dockShadow, spotColor = dockShadow)
                .clip(capsule)
                .then(
                    if (hazeState != null) {
                        Modifier.hazeEffect(state = hazeState) {
                            blurEnabled = true
                            blurRadius = 32.dp
                            backgroundColor = dockSurface
                            tints = listOf(HazeTint(dockSurface.copy(alpha = 0.28f)))
                            noiseFactor = 0.08f
                        }
                    } else {
                        Modifier
                    },
                )
                .background(dockSurface.copy(alpha = if (hazeState != null) 0.12f else 0.94f))
                .border(
                    1.dp,
                    Brush.verticalGradient(listOf(dockBorder.copy(alpha = 0.38f), dockBorder.copy(alpha = 0.08f))),
                    capsule,
                )
                .selectableGroup(),
            content = {
                // Child 0: the sliding pill (behind everything).
                Box(
                    Modifier
                        .then(
                            if (glowEnabled) {
                                Modifier.shadow(10.dp, pillShape, clip = false, ambientColor = pillColor, spotColor = pillColor)
                            } else {
                                Modifier
                            },
                        )
                        .clip(pillShape)
                        .background(pillColor, pillShape),
                )
                // Children 1 + 3i .. 3 + 3i: hit target, icon, label for tab i.
                items.forEachIndexed { index, item ->
                    Box(
                        Modifier
                            .clip(pillShape)
                            .selectable(
                                selected = item.selected,
                                role = Role.Tab,
                                onClick = item.onClick,
                            )
                            .semantics { contentDescription = item.label },
                    )
                    val kind = item.morph
                    when {
                        kind != null -> DockMorphIcon(
                            kind = kind,
                            progress = { coverage(index) },
                            tint = { lerpColor(idleTint, onPillColor, coverage(index)) },
                            modifier = Modifier.size(iconSize),
                        )
                        item.drawable != null -> Icon(
                            painterResource(item.drawable), null, Modifier.size(iconSize),
                            tint = if (item.selected) onPillColor else idleTint,
                        )
                        item.icon != null -> Icon(
                            item.icon, null, Modifier.size(iconSize),
                            tint = if (item.selected) onPillColor else idleTint,
                        )
                        else -> Spacer(Modifier.size(iconSize))
                    }
                    Text(
                        text = item.label,
                        color = onPillColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.2.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Visible,
                        softWrap = false,
                        modifier = Modifier.graphicsLayer {
                            // Fade the label in only once the pill is mostly open so it is never
                            // visible while it would still overflow the pill.
                            val c = coverage(index) * labelVisibility.value
                            alpha = ((c - 0.55f) / 0.45f).coerceIn(0f, 1f)
                        },
                    )
                }
            },
        ) { measurables, constraints ->
            val n = items.size
            val hPad = 6.dp.roundToPx()
            val gap = 2.dp.roundToPx()
            val labelGap = 6.dp.roundToPx()
            val grow = 3.dp.roundToPx()
            val iconPx = iconSize.roundToPx()
            val slotH = slotHeight.roundToPx()
            val height = dockHeight.roundToPx()
            val maxCw = 44.dp.roundToPx()
            val minCw = 32.dp.roundToPx()

            val cov = FloatArray(n) { coverage(it) }
            val vis = labelVisibility.value
            val labelPlaceables = arrayOfNulls<Placeable>(n)
            val extra = FloatArray(n)
            var extraSum = 0f
            for (i in 0 until n) {
                val lc = cov[i] * vis
                if (lc > 0.001f) {
                    // Measure the full label (unbounded) so the pill can always fit the whole word.
                    val p = measurables[3 + 3 * i].measure(Constraints())
                    labelPlaceables[i] = p
                    extra[i] = lc * (2 * grow + labelGap + p.width)
                    extraSum += extra[i]
                }
            }
            // Idle tabs share whatever width is left after the active tab's pill has taken its natural
            // width, so the full label ("Profile", "Library", "Live TV") fits even on narrow phones.
            val available = if (constraints.hasBoundedWidth) {
                (constraints.maxWidth - 2 * hPad - gap * (n - 1)).toFloat()
            } else {
                Float.MAX_VALUE
            }
            val cw = ((available - extraSum) / n).coerceIn(minCw.toFloat(), maxCw.toFloat())

            val widths = IntArray(n) { (cw + extra[it]).roundToInt() }
            val xs = IntArray(n)
            var cursor = hPad
            for (i in 0 until n) {
                xs[i] = cursor
                cursor += widths[i] + gap
            }
            val contentWidth = cursor - gap + hPad
            val totalWidth = if (constraints.hasBoundedWidth) {
                min(contentWidth, constraints.maxWidth)
            } else {
                contentWidth
            }.coerceAtLeast(constraints.minWidth)

            // Sliding pill rectangle: interpolate between the two neighbouring slots.
            val pos = position.value.coerceIn(0f, (n - 1).toFloat())
            val lo = floor(pos).toInt().coerceIn(0, n - 1)
            val hi = min(lo + 1, n - 1)
            val frac = pos - lo
            val pillLeft = lerpFloat(xs[lo].toFloat(), xs[hi].toFloat(), frac)
            val pillRight = lerpFloat((xs[lo] + widths[lo]).toFloat(), (xs[hi] + widths[hi]).toFloat(), frac)
            val pillWidth = ((pillRight - pillLeft) * selectionStrength.value).roundToInt().coerceAtLeast(0)
            val pillPlaceable = measurables[0].measure(Constraints.fixed(pillWidth, slotH))

            val slotY = (height - slotH) / 2
            val iconY = (height - iconPx) / 2

            val hits = Array(n) { measurables[1 + 3 * it].measure(Constraints.fixed(widths[it], slotH)) }
            val icons = Array(n) { measurables[2 + 3 * it].measure(Constraints()) }

            layout(totalWidth, height) {
                pillPlaceable.placeRelative(pillLeft.roundToInt(), slotY)
                for (i in 0 until n) {
                    hits[i].placeRelative(xs[i], slotY)
                    val lc = cov[i] * vis
                    val labelPart = lc * (labelGap + (labelPlaceables[i]?.width ?: 0))
                    val iconX = xs[i] + ((widths[i] - iconPx - labelPart) / 2f).roundToInt()
                    icons[i].placeRelative(iconX, iconY)
                    labelPlaceables[i]?.let { label ->
                        label.placeRelative(iconX + iconPx + labelGap, (height - label.height) / 2)
                    }
                }
            }
        }
    }
}

private fun lerpFloat(a: Float, b: Float, t: Float): Float = a + (b - a) * t

internal fun visualNavIndex(logicalIndex: Int, count: Int, isRtl: Boolean): Int =
    if (logicalIndex in 0 until count && isRtl) count - 1 - logicalIndex else logicalIndex

internal fun logicalNavIndex(visualIndex: Int, count: Int, isRtl: Boolean): Int =
    if (visualIndex in 0 until count && isRtl) count - 1 - visualIndex else visualIndex

internal suspend fun PointerInputScope.detectJellyTabGestures(
    motion: JellyMotion,
    density: Float,
    currentItems: () -> List<FloatingNavigationItem>,
    isRtl: () -> Boolean,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        motion.begin(down.position.x / density, down.position.y / density)
        var claimed = false
        var finished = false
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (!motion.dragging) {
                    finished = true
                    break
                }
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (event.changes.count { it.pressed } > 1) break
                val delta = change.position - down.position
                if (max(abs(delta.x), abs(delta.y)) > viewConfiguration.touchSlop) claimed = true
                if (claimed) change.consume()
                awaitPointerEvent(PointerEventPass.Main)
                if (!motion.dragging) {
                    finished = true
                    break
                }
                if (change.isConsumed && !claimed) break
                motion.drag(delta.x / density, delta.y / density)
                if (!change.pressed) {
                    val visualIndex = motion.finish()
                    val items = currentItems()
                    val logicalIndex = logicalNavIndex(visualIndex, items.size, isRtl())
                    finished = true
                    items.getOrNull(logicalIndex)?.onClick?.invoke()
                    change.consume()
                    break
                }
            }
        } finally {
            if (!finished) {
                val items = currentItems()
                val selectedIndex = items.indexOfFirst { it.selected }
                motion.cancel(visualNavIndex(selectedIndex, items.size, isRtl()))
            }
        }
    }
}
