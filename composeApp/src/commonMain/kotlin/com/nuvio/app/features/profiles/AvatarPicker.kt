package com.nuvio.app.features.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.nuvio

/**
 * Picture avatar grid. Avatars are grouped by category; when there is more than one category a
 * scrollable tab row (All, Cinema, Animals, ...) filters the grid.
 */
@Composable
internal fun AvatarPicker(
    avatars: List<AvatarCatalogItem>,
    selectedAvatarId: String?,
    onAvatarSelected: (AvatarCatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (avatars.isEmpty()) return
    val tokens = MaterialTheme.nuvio
    // groupBy keeps first-appearance order, so remote catalog categories stay ahead of built-ins.
    val groups = remember(avatars) { avatars.groupBy { it.category }.toList() }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    val activeCategory = selectedCategory?.takeIf { category -> groups.any { it.first == category } }
    val visibleGroups = if (activeCategory == null) groups else groups.filter { it.first == activeCategory }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (groups.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AvatarCategoryTab(
                    title = "All",
                    selected = activeCategory == null,
                    onClick = { selectedCategory = null },
                )
                groups.forEach { (category, _) ->
                    AvatarCategoryTab(
                        title = avatarCategoryTitle(category),
                        selected = activeCategory == category,
                        onClick = { selectedCategory = category },
                    )
                }
            }
        }

        visibleGroups.forEach { (category, items) ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (groups.size > 1 && activeCategory == null) {
                    Text(
                        text = avatarCategoryTitle(category),
                        style = MaterialTheme.typography.labelLarge,
                        color = tokens.colors.textMuted,
                        fontWeight = FontWeight.Bold,
                    )
                }
                AvatarGrid(
                    avatars = items,
                    selectedAvatarId = selectedAvatarId,
                    onAvatarSelected = onAvatarSelected,
                )
            }
        }
    }
}

@Composable
private fun AvatarCategoryTab(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier = Modifier
            .clip(tokens.shapes.chip)
            .background(if (selected) tokens.colors.accent else tokens.colors.surfaceCard)
            .border(
                width = 1.dp,
                color = if (selected) tokens.colors.accent else tokens.colors.borderDefault,
                shape = tokens.shapes.chip,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) tokens.colors.onAccent else tokens.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun AvatarGrid(
    avatars: List<AvatarCatalogItem>,
    selectedAvatarId: String?,
    onAvatarSelected: (AvatarCatalogItem) -> Unit,
) {
    val spacing = 10.dp
    val minAvatarSize = 58.dp
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = ((maxWidth + spacing) / (minAvatarSize + spacing)).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            avatars.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    row.forEach { avatar ->
                        AvatarChoiceItem(
                            avatar = avatar,
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                            isSelected = avatar.id == selectedAvatarId,
                            onClick = { onAvatarSelected(avatar) },
                        )
                    }
                    repeat(columns - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarChoiceItem(
    avatar: AvatarCatalogItem,
    modifier: Modifier,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    BoxWithConstraints(
        modifier = modifier
            .clip(CircleShape)
            .background(
                avatar.bgColor?.let(::parseHexColor)
                    ?: MaterialTheme.colorScheme.surfaceVariant,
            )
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) tokens.colors.borderSelected else tokens.colors.borderDefault,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val emoji = avatar.emoji
        if (emoji != null) {
            Text(
                text = emoji,
                modifier = Modifier.semantics { contentDescription = avatar.displayName },
                fontSize = (maxWidth.value * 0.5f).sp,
                textAlign = TextAlign.Center,
            )
        } else {
            AsyncImage(
                model = avatarImageUrl(avatar),
                contentDescription = avatar.displayName,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(tokens.colors.accent)
                    .border(1.5.dp, tokens.colors.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = tokens.colors.onAccent,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}
