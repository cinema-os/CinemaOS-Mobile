package com.nuvio.app.features.updater

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.ui.AppTheme
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.themePalette
import com.nuvio.app.core.ui.accentBrush
import com.nuvio.app.core.ui.appTheme
import com.nuvio.app.core.ui.nuvio
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_close
import nuvio.composeapp.generated.resources.action_continue
import nuvio.composeapp.generated.resources.action_install
import nuvio.composeapp.generated.resources.action_later
import nuvio.composeapp.generated.resources.action_retry
import nuvio.composeapp.generated.resources.action_update
import nuvio.composeapp.generated.resources.updates_debug_test_complete
import nuvio.composeapp.generated.resources.updates_downloading_progress
import nuvio.composeapp.generated.resources.updates_message_allow_installs
import nuvio.composeapp.generated.resources.updates_message_ready
import nuvio.composeapp.generated.resources.updates_no_release_notes
import nuvio.composeapp.generated.resources.updates_preparing_download
import nuvio.composeapp.generated.resources.updates_release_notes
import nuvio.composeapp.generated.resources.updates_title_allow_installs
import nuvio.composeapp.generated.resources.updates_title_available
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppUpdaterHost(
    controller: AppUpdaterController,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!AppFeaturePolicy.inAppUpdaterEnabled || !AppUpdaterPlatform.isSupported) {
        content()
        return
    }

    val state by controller.uiState.collectAsStateWithLifecycle()
    var showReleaseNotes by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(controller) {
        controller.ensureAutoCheckStarted()
    }
    LaunchedEffect(state.update?.tag) {
        showReleaseNotes = false
    }

    val update = state.update
    val showBanner = state.showDialog && update != null

    Box(modifier = modifier) {
        content()
        AnimatedVisibility(
            visible = showBanner,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically(tween(320)) { -it } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(240)) { -it } + fadeOut(tween(160)),
        ) {
            update?.let { availableUpdate ->
                AppUpdateBanner(
                    state = state,
                    update = availableUpdate,
                    onDownload = controller::downloadUpdate,
                    onInstall = controller::installDownloadedUpdate,
                    onShowReleaseNotes = { showReleaseNotes = true },
                    onDismiss = controller::dismissDialog,
                )
            }
        }
    }

    if (showReleaseNotes && update != null) {
        ReleaseNotesDialog(
            update = update,
            onDismiss = { showReleaseNotes = false },
        )
    }

    if (state.showUnknownSourcesDialog) {
        UnknownSourcesDialog(
            onContinue = controller::resumeInstallation,
            onDismiss = controller::dismissDialog,
        )
    }
}

@Composable
private fun AppUpdateBanner(
    state: AppUpdaterUiState,
    update: AppUpdate,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onShowReleaseNotes: () -> Unit,
    onDismiss: () -> Unit,
) {
    val targetProgress = when {
        state.isDownloading -> state.downloadProgress ?: 0f
        state.isDebugTest && !state.isUpdateAvailable -> 1f
        else -> 0f
    }.coerceIn(0f, 1f)
    val progress by animateFloatAsState(targetProgress, tween(180), label = "updateBannerProgress")
    val accent = MaterialTheme.themePalette.accentBrush()
    val debugTestComplete = state.isDebugTest && !state.isDownloading && !state.isUpdateAvailable
    val subtitle = when {
        state.errorMessage != null -> state.errorMessage
        state.isDownloading && state.downloadProgress != null -> stringResource(
            Res.string.updates_downloading_progress,
            (state.downloadProgress * 100).toInt().coerceIn(0, 100),
        )
        state.isDownloading -> stringResource(Res.string.updates_preparing_download)
        debugTestComplete -> stringResource(Res.string.updates_debug_test_complete)
        state.downloadedApkPath != null -> stringResource(Res.string.updates_message_ready)
        else -> listOfNotNull(
            stringResource(Res.string.updates_title_available),
            update.assetSizeBytes?.let(::formatFileSize),
        ).joinToString(" · ")
    }
    val cardShape = RoundedCornerShape(22.dp)
    val ink = Color.White

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .shadow(24.dp, cardShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(cardShape)
            .background(Color(0xF2141418))
            .border(1.dp, Color.White.copy(alpha = 0.10f), cardShape),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (debugTestComplete) Icons.Rounded.CheckCircle else Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "CinemaOS ${update.tag.removePrefix("v").let { "v$it" }}",
                        style = MaterialTheme.typography.titleSmall,
                        color = ink,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.errorMessage != null) Color(0xFFFF8A80) else ink.copy(alpha = 0.62f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (update.notes.isNotBlank()) {
                    IconButton(onClick = onShowReleaseNotes, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Info, stringResource(Res.string.updates_release_notes),
                            tint = ink.copy(alpha = 0.75f), modifier = Modifier.size(20.dp))
                    }
                }
                if (!state.isDownloading && !debugTestComplete) {
                    val canAct = state.downloadedApkPath != null || state.isUpdateAvailable
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (canAct) Color.White else Color.White.copy(alpha = 0.3f))
                            .clickable(enabled = canAct, onClick = if (state.downloadedApkPath != null) onInstall else onDownload)
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    ) {
                        Text(
                            text = when {
                                state.downloadedApkPath != null -> stringResource(Res.string.action_install)
                                AppUpdaterPlatform.isMetaQuest -> "Download from cinemaos.tech/apps"
                                state.errorMessage != null -> stringResource(Res.string.action_retry)
                                else -> stringResource(Res.string.action_update)
                            },
                            color = Color.Black,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (!state.isDownloading) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Close, stringResource(Res.string.action_close),
                            tint = ink.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (state.isDownloading || progress > 0f) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.08f))) {
                    Box(Modifier.fillMaxWidth(progress).height(3.dp).background(accent))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReleaseNotesDialog(
    update: AppUpdate,
    onDismiss: () -> Unit,
) {
    DialogSurface(
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(Res.string.updates_release_notes),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = update.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(Res.string.action_close),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Text(
            text = update.notes.ifBlank { stringResource(Res.string.updates_no_release_notes) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnknownSourcesDialog(
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    DialogSurface(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.updates_title_allow_installs),
    ) {
        Text(
            text = stringResource(Res.string.updates_message_allow_installs),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_later),
                onClick = onDismiss,
            )
            DialogButton(
                text = stringResource(Res.string.action_continue),
                onClick = onContinue,
                style = DialogButtonStyle.Primary,
            )
        }
    }
}
