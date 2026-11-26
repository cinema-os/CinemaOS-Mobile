package com.nuvio.app.features.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioStatusModal
import com.nuvio.app.core.ui.NuvioSurfaceCard
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.membership.CosmeticEntitlement
import com.nuvio.app.features.membership.MemberAccessRepository
import com.nuvio.app.features.membership.ProfileBackgroundRepository
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProfileEditScreen(
    profile: NuvioProfile? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isNew = profile == null
    val scope = rememberCoroutineScope()
    val profileState by ProfileRepository.state.collectAsStateWithLifecycle()
    val currentProfile = remember(profile?.profileIndex, profileState.profiles, profile) {
        profile?.let { snapshot ->
            profileState.profiles.find { it.profileIndex == snapshot.profileIndex } ?: snapshot
        }
    }
    var selectedColorHex by rememberSaveable {
        mutableStateOf(currentProfile?.avatarColorHex ?: PROFILE_COLORS.first())
    }
    val fallbackColorHex = selectedColorHex

    var name by rememberSaveable { mutableStateOf(currentProfile?.name ?: "") }
    var selectedAvatarId by rememberSaveable { mutableStateOf(currentProfile?.avatarId) }
    var avatarUrl by rememberSaveable { mutableStateOf(currentProfile?.avatarUrl.orEmpty()) }
    var selectedBackgroundId by rememberSaveable { mutableStateOf(currentProfile?.profileBackgroundId) }
    var selectedBackgroundUrl by rememberSaveable { mutableStateOf(currentProfile?.profileBackgroundUrl) }
    var usesPrimaryAddons by rememberSaveable { mutableStateOf(currentProfile?.usesPrimaryAddons ?: false) }
    var defaultAvatarApplied by rememberSaveable { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPinSetup by remember { mutableStateOf(false) }
    var showPinClear by remember { mutableStateOf(false) }
    val memberAccess by remember {
        MemberAccessRepository.ensureStarted()
        MemberAccessRepository.access
    }.collectAsStateWithLifecycle()
    val backgroundCatalog by ProfileBackgroundRepository.catalog.collectAsStateWithLifecycle()
    val canChooseBackground = !isNew && memberAccess.entitlements.includes(CosmeticEntitlement.PROFILE_BACKGROUNDS)

    val avatars by AvatarRepository.avatars.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        AvatarRepository.refreshAvatars()
    }
    LaunchedEffect(canChooseBackground) {
        if (canChooseBackground) ProfileBackgroundRepository.preloadLandscapeImages()
    }
    // New profiles start with a picture avatar. This runs once so picking a colour afterwards
    // (which switches back to an initial avatar) is not undone.
    LaunchedEffect(isNew, avatars, defaultAvatarApplied) {
        if (isNew && !defaultAvatarApplied && avatarUrl.isBlank() && selectedAvatarId == null && avatars.isNotEmpty()) {
            selectedAvatarId = avatars.first().id
            defaultAvatarApplied = true
        }
    }

    val customAvatarUrl = remember(avatarUrl) { normalizedAvatarUrl(avatarUrl) }
    val avatarUrlIsInvalid = avatarUrl.isNotBlank() && customAvatarUrl == null
    val selectedAvatarItem = remember(selectedAvatarId, avatars) {
        selectedAvatarId?.let { id -> avatars.find { it.id == id } }
    }
    val visibleAvatarItem = if (customAvatarUrl == null) selectedAvatarItem else null
    val previewAccent = remember(visibleAvatarItem, fallbackColorHex) {
        parseHexColor(visibleAvatarItem?.bgColor ?: fallbackColorHex)
    }
    val previewProfile = remember(name, fallbackColorHex, visibleAvatarItem, customAvatarUrl) {
        NuvioProfile(
            name = name,
            avatarColorHex = fallbackColorHex,
            avatarId = visibleAvatarItem?.id,
            avatarUrl = customAvatarUrl,
        )
    }

    NuvioScreen(modifier = modifier) {
        stickyHeader {
            NuvioScreenHeader(
                title = if (isNew) {
                    stringResource(Res.string.profile_edit_add_title)
                } else {
                    stringResource(Res.string.profile_edit_edit_title)
                },
                onBack = onBack,
            )
        }

        item {
            ProfileIdentityCard(
                previewProfile = previewProfile,
                name = name,
                usesPrimaryAddons = usesPrimaryAddons,
                onNameChange = { name = it },
                onUsesPrimaryAddonsChange = { usesPrimaryAddons = it },
                avatarCaption = when {
                    customAvatarUrl != null -> stringResource(Res.string.profile_custom_avatar_selected)
                    visibleAvatarItem != null -> stringResource(
                        Res.string.profile_avatar_selected,
                        visibleAvatarItem.displayName,
                    )
                    else -> "Using your initial on the colour below"
                },
            )
        }

        item {
            NuvioSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    EditSectionHeader(
                        title = stringResource(Res.string.profile_choose_avatar),
                        subtitle = "Pick a picture, browse by category.",
                    )
                    AvatarPicker(
                        avatars = avatars,
                        selectedAvatarId = selectedAvatarId.takeIf { customAvatarUrl == null },
                        onAvatarSelected = { avatar ->
                            avatarUrl = ""
                            selectedAvatarId = avatar.id
                        },
                    )
                }
            }
        }

        item {
            NuvioSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    EditSectionHeader(
                        title = "Profile colour",
                        subtitle = "Choosing a colour switches to an initial avatar on that colour.",
                    )
                    ProfileColorPicker(
                        selectedHex = selectedColorHex,
                        active = visibleAvatarItem == null && customAvatarUrl == null,
                        onSelected = { hex ->
                            selectedColorHex = hex
                            selectedAvatarId = null
                            avatarUrl = ""
                        },
                    )
                }
            }
        }

        if (!isNew) {
            item {
                NuvioSurfaceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        EditSectionHeader(
                            title = stringResource(Res.string.profile_security),
                            subtitle = if (currentProfile?.pinEnabled == true) {
                                stringResource(Res.string.profile_security_pin_enabled)
                            } else {
                                stringResource(Res.string.profile_security_pin_disabled)
                            },
                        )
                        if (currentProfile?.pinEnabled == true) {
                            NuvioPrimaryButton(
                                text = stringResource(Res.string.profile_remove_pin_lock),
                                onClick = { showPinClear = true },
                            )
                        } else {
                            NuvioPrimaryButton(
                                text = stringResource(Res.string.profile_set_pin_lock),
                                onClick = { showPinSetup = true },
                            )
                        }
                    }
                }
            }
        }

        if (canChooseBackground) {
            item {
                NuvioSurfaceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        EditSectionHeader(
                            title = stringResource(Res.string.profile_choose_background),
                            subtitle = stringResource(Res.string.profile_background_member_note),
                        )
                        ProfileBackgroundPicker(
                            backgrounds = backgroundCatalog,
                            selectedBackgroundId = selectedBackgroundId,
                            selectedBackgroundUrl = selectedBackgroundUrl,
                            customBackgroundUrl = currentProfile?.profileBackgroundUrl,
                            standardBackgroundColor = previewAccent,
                            onSelectionChange = { id, url ->
                                selectedBackgroundId = id
                                selectedBackgroundUrl = url
                            },
                        )
                    }
                }
            }
        }

        item {
            NuvioSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    EditSectionHeader(
                        title = stringResource(Res.string.profile_custom_avatar_url),
                        subtitle = stringResource(Res.string.profile_custom_avatar_url_description),
                    )
                    NuvioInputField(
                        value = avatarUrl,
                        onValueChange = { value ->
                            avatarUrl = value
                            if (value.isNotBlank()) {
                                selectedAvatarId = null
                            }
                        },
                        placeholder = stringResource(Res.string.profile_custom_avatar_url_placeholder),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
                        ),
                    )
                    if (avatarUrlIsInvalid) {
                        Text(
                            text = stringResource(Res.string.profile_avatar_url_invalid),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            NuvioPrimaryButton(
                text = if (isSaving) {
                    stringResource(Res.string.profile_saving)
                } else if (isNew) {
                    stringResource(Res.string.profile_create_profile)
                } else {
                    stringResource(Res.string.collections_editor_save_changes)
                },
                enabled = name.isNotBlank() && !avatarUrlIsInvalid && !isSaving,
                onClick = {
                    isSaving = true
                    scope.launch {
                        val avatarColorHex = visibleAvatarItem?.bgColor ?: fallbackColorHex
                        if (isNew) {
                            ProfileRepository.createProfile(
                                name = name,
                                avatarColorHex = avatarColorHex,
                                avatarId = if (customAvatarUrl == null) selectedAvatarId else null,
                                avatarUrl = customAvatarUrl,
                                usesPrimaryAddons = usesPrimaryAddons,
                            )
                        } else {
                            ProfileRepository.updateProfile(
                                profileIndex = currentProfile!!.profileIndex,
                                name = name,
                                avatarColorHex = avatarColorHex,
                                avatarId = if (customAvatarUrl == null) selectedAvatarId else null,
                                avatarUrl = customAvatarUrl,
                                profileBackgroundId = selectedBackgroundId,
                                profileBackgroundUrl = selectedBackgroundUrl,
                                usesPrimaryAddons = usesPrimaryAddons,
                            )
                        }
                        isSaving = false
                        onSaved()
                    }
                },
            )
        }

        if (!isNew && (currentProfile?.profileIndex ?: 0) > 1) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(
                        text = stringResource(Res.string.profile_delete_title),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    NuvioStatusModal(
        title = stringResource(Res.string.profile_delete_title),
        message = stringResource(
            Res.string.profile_delete_confirm_message,
            currentProfile?.name.orEmpty(),
        ),
        isVisible = showDeleteConfirm,
        destructive = true,
        confirmText = stringResource(Res.string.action_delete),
        dismissText = stringResource(Res.string.action_cancel),
        onConfirm = {
            showDeleteConfirm = false
            scope.launch {
                currentProfile?.let { ProfileRepository.deleteProfile(it.profileIndex) }
                onBack()
            }
        },
        onDismiss = { showDeleteConfirm = false },
    )

    if (showPinSetup && currentProfile != null) {
        PinSetupDialog(
            profileIndex = currentProfile.profileIndex,
            hasExistingPin = currentProfile.pinEnabled,
            onDone = {
                showPinSetup = false
            },
            onDismiss = { showPinSetup = false },
        )
    }

    if (showPinClear && currentProfile != null) {
        PinEntryDialog(
            profileName = stringResource(Res.string.profile_remove_pin_for, currentProfile.name),
            onVerify = { pin -> ProfileRepository.clearPin(currentProfile.profileIndex, pin) },
            onVerified = {
                showPinClear = false
            },
            onDismiss = {
                showPinClear = false
            },
        )
    }
}

@Composable
private fun EditSectionHeader(
    title: String,
    subtitle: String? = null,
) {
    val tokens = MaterialTheme.nuvio
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = tokens.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
            )
        }
    }
}

@Composable
private fun ProfileIdentityCard(
    previewProfile: NuvioProfile,
    name: String,
    usesPrimaryAddons: Boolean,
    onNameChange: (String) -> Unit,
    onUsesPrimaryAddonsChange: (Boolean) -> Unit,
    avatarCaption: String,
) {
    val tokens = MaterialTheme.nuvio
    NuvioSurfaceCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProfileAvatarView(
                profile = previewProfile,
                size = 128.dp,
            )
            Text(
                text = avatarCaption,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
                textAlign = TextAlign.Center,
            )

            NuvioInputField(
                value = name,
                onValueChange = onNameChange,
                placeholder = stringResource(Res.string.profile_name_placeholder),
            )

            ProfileOptionRow(
                title = stringResource(Res.string.profile_use_primary_addons),
                description = stringResource(Res.string.profile_use_primary_addons_description),
                checked = usesPrimaryAddons,
                onCheckedChange = onUsesPrimaryAddonsChange,
            )
        }
    }
}

@Composable
private fun ProfileColorPicker(
    selectedHex: String,
    active: Boolean,
    onSelected: (String) -> Unit,
) {
    val spacing = 10.dp
    val swatchSize = 40.dp
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = ((maxWidth + spacing) / (swatchSize + spacing)).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            PROFILE_COLORS.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    row.forEach { hex ->
                        val selected = active && hex.equals(selectedHex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(swatchSize)
                                .clip(CircleShape)
                                .background(parseHexColor(hex))
                                .border(
                                    width = if (selected) 3.dp else 0.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .clickable { onSelected(hex) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileOptionRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
    }
}

@Composable
fun PinSetupDialog(
    profileIndex: Int,
    hasExistingPin: Boolean,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableStateOf(if (hasExistingPin) "current" else "new") }
    var currentPin by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    when (step) {
        "current" -> PinEntryDialog(
            profileName = stringResource(Res.string.profile_enter_current_pin),
            onVerify = { pin -> ProfileRepository.verifyPin(profileIndex, pin) },
            onVerified = { pin ->
                currentPin = pin
                step = "new"
            },
            onDismiss = onDismiss,
        )

        "new" -> PinEntryDialog(
            profileName = stringResource(Res.string.profile_enter_new_pin),
            onVerify = { pin ->
                ProfileRepository.setPin(
                    profileIndex = profileIndex,
                    pin = pin,
                    currentPin = currentPin.ifEmpty { null },
                )
            },
            onVerified = {
                onDone()
            },
            onDismiss = onDismiss,
        )
    }
}
