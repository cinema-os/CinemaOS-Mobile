package com.nuvio.app.features.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage

/**
 * Round avatar for [profile]: custom avatar URL, catalog avatar, or the profile colour with the
 * first letter of the name. Shows a person icon when [profile] is null.
 */
@Composable
fun ProfileAvatarView(
    profile: NuvioProfile?,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    initialSize: TextUnit = (size.value * 0.42f).sp,
) {
    val avatars by AvatarRepository.avatars.collectAsStateWithLifecycle()
    val avatarItem = remember(profile?.avatarId, avatars) {
        profile?.avatarId?.let { id -> avatars.find { it.id == id } }
    }
    val imageUrl = remember(profile?.avatarUrl, avatarItem) {
        profile?.let { profileAvatarImageUrl(it, avatarItem) }
    }
    val accent = remember(profile?.avatarColorHex, avatarItem) {
        parseHexColor(avatarItem?.bgColor ?: profile?.avatarColorHex ?: PROFILE_COLORS.first())
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (imageUrl != null) accent else accent.copy(alpha = 0.18f))
            .then(
                if (imageUrl == null) Modifier.border(2.dp, accent.copy(alpha = 0.4f), CircleShape)
                else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            imageUrl != null -> AsyncImage(
                model = imageUrl,
                contentDescription = profile?.name,
                modifier = Modifier.size(size).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            !profile?.name.isNullOrBlank() -> Text(
                text = profile!!.name.take(1).uppercase(),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = initialSize),
                color = accent,
                fontWeight = FontWeight.Bold,
            )
            else -> Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}
