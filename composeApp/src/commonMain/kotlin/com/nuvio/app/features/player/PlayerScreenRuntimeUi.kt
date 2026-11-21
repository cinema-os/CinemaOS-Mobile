package com.nuvio.app.features.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioStatusModal
import com.nuvio.app.core.ui.LocalFold
import com.nuvio.app.features.p2p.P2pStreamingState
import com.nuvio.app.features.party.PartyRepository
import com.nuvio.app.features.p2p.formatP2pMegabytes
import com.nuvio.app.features.p2p.formatP2pSpeed
import com.nuvio.app.features.player.skip.internalSkipAction
import com.nuvio.app.features.servers.ServerStreams
import com.nuvio.app.features.streams.streamAddonInstanceId
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PlayerScreenRuntime.RenderPlayerRuntimeUi(
    partyPanelWidth: Dp,
    partyPanelInset: Dp,
    dockPartyPanel: Boolean,
) {
    val runtime = this
    val isInPip = rememberIsInPictureInPicture()
    val displayedPositionMs = scrubbingPositionMs ?: playbackSnapshot.positionMs
    val isEpisode = activeSeasonNumber != null && activeEpisodeNumber != null
    val introKey = "${activeVideoId ?: parentMetaId ?: title}:$activeSeasonNumber:$activeEpisodeNumber"
    // Same logo the loading screen shows.
    val nowPlayingLogo = logo ?: metaUiState.meta?.logo
    val (introVisible, introComplete) = rememberPlaybackTitleIntro(
        contentKey = introKey,
        ready = playbackSnapshot.isPlaying && !playbackSnapshot.isLoading && !controlsVisible &&
            !isPanelOpen && !isInPip && errorMessage == null,
    )
    // As in the React Native player: showing the controls ends the warnings for this title.
    LaunchedEffect(controlsVisible) {
        if (controlsVisible && showParentalGuide && introComplete) showParentalGuide = false
    }
    val currentGestureFeedback = liveGestureFeedback ?: gestureFeedback
    val isP2pPlaybackActive = activeTorrentInfoHash != null
    val p2pConnecting = p2pStreamingState as? P2pStreamingState.Connecting
    val p2pStats = p2pStreamingState as? P2pStreamingState.Streaming
    val p2pPeerInfo = p2pStats?.let { stats ->
        org.jetbrains.compose.resources.stringResource(
            nuvio.composeapp.generated.resources.Res.string.player_torrent_peer_info,
            stats.seeds,
            stats.peers,
        )
    }
    val p2pDownloadSpeed = p2pStats?.let { formatP2pSpeed(it.downloadSpeed) }
    val p2pLoadingBytes = p2pStats?.let { maxOf(it.downloadedBytes, it.deliveredBytes) } ?: 0L
    val connectingPeerInfo = p2pConnecting?.let { state ->
        org.jetbrains.compose.resources.stringResource(
            nuvio.composeapp.generated.resources.Res.string.player_torrent_peer_info,
            state.seeds,
            state.peers,
        )
    }
    val p2pInitialLoadingMessage = when {
        !isP2pPlaybackActive || initialLoadCompleted -> null
        p2pConnecting != null -> {
            if (p2pSettingsUiState.hideTorrentStats) {
                p2pConnectingPhaseLabel(p2pConnecting.phase)
            } else {
                org.jetbrains.compose.resources.stringResource(
                    nuvio.composeapp.generated.resources.Res.string.player_torrent_connecting_status,
                    p2pConnectingPhaseLabel(p2pConnecting.phase),
                    connectingPeerInfo.orEmpty(),
                    formatP2pSpeed(p2pConnecting.downloadSpeed),
                )
            }
        }
        p2pStats != null -> {
            if (p2pSettingsUiState.hideTorrentStats) {
                null
            } else {
                org.jetbrains.compose.resources.stringResource(
                    nuvio.composeapp.generated.resources.Res.string.player_torrent_loading_status,
                    formatP2pMegabytes(p2pLoadingBytes),
                    p2pPeerInfo.orEmpty(),
                    p2pDownloadSpeed.orEmpty(),
                )
            }
        }
        else -> org.jetbrains.compose.resources.stringResource(
            nuvio.composeapp.generated.resources.Res.string.player_torrent_starting_engine,
        )
    }
    val bufferedAheadMs = (playbackSnapshot.bufferedPositionMs - playbackSnapshot.positionMs)
        .coerceAtLeast(0L)
    val p2pInitialLoadingProgress = when {
        !isP2pPlaybackActive || initialLoadCompleted || p2pStats == null -> null
        else -> p2pInitialLoadingProgress(
            bufferedAheadMs = bufferedAheadMs,
            downloadedBytes = p2pStats.downloadedBytes,
            deliveredBytes = p2pStats.deliveredBytes,
        )
    }
    val showP2pRebufferStats = isP2pPlaybackActive &&
        initialLoadCompleted &&
        playbackSnapshot.isLoading &&
        p2pStats != null &&
        !p2pSettingsUiState.hideTorrentStats
    val p2pRebufferMessage = when {
        !showP2pRebufferStats -> null
        else -> {
            val bufferedSeconds = ((playbackSnapshot.bufferedPositionMs - playbackSnapshot.positionMs) / 1000L)
                .coerceAtLeast(0L)
            "${bufferedSeconds}s buffered · ${p2pPeerInfo.orEmpty()} · ${p2pDownloadSpeed.orEmpty()}"
        }
    }
    val p2pRebufferProgress = when {
        !showP2pRebufferStats -> null
        else -> {
            val bufferedSeconds = ((playbackSnapshot.bufferedPositionMs - playbackSnapshot.positionMs) / 1000f)
                .coerceAtLeast(0f)
            (bufferedSeconds / 10f).coerceIn(0f, 1f)
        }
    }
    val gestureCallbacks = rememberSurfaceGestureCallbacks()
    val fold = LocalFold.current?.takeIf { !isInPip }
    val videoModifier = if (fold != null) Modifier.fillMaxWidth().height(fold.top) else Modifier.fillMaxSize()
    val panelModifier = if (fold != null) Modifier.fillMaxSize().padding(top = fold.bottom) else Modifier.fillMaxSize()
    val playbackGesturesEnabled = initialLoadCompleted && errorMessage == null
    val keyFocusRequester = remember { FocusRequester() }
    LaunchedEffect(keyFocusRequester) {
        keyFocusRequester.requestFocus()
    }

    BindAutoSyncRuntimeEffects() // AutoSync hook

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(end = partyPanelInset)
            .clipToBounds()
            .onSizeChanged { layoutSize = it }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || playerControlsLocked) return@onKeyEvent false
                when {
                    event.key == Key.Spacebar -> togglePlayback()
                    event.key == Key.DirectionLeft && playbackGesturesEnabled -> seekBy(-10_000L)
                    event.key == Key.DirectionRight && playbackGesturesEnabled -> seekBy(10_000L)
                    event.key == Key.DirectionUp -> changeVolumeBy(PlayerKeyVolumeStep)
                    event.key == Key.DirectionDown -> changeVolumeBy(-PlayerKeyVolumeStep)
                    else -> return@onKeyEvent false
                }
                true
            }
            .focusRequester(keyFocusRequester)
            .focusable()
            .playerSurfaceTapGestures(
                layoutSize = layoutSize,
                playbackGesturesEnabled = playbackGesturesEnabled,
                playerControlsLockedState = gestureCallbacks.playerControlsLocked,
                onSurfaceTap = gestureCallbacks.onSurfaceTap,
                onSurfaceDoubleTap = gestureCallbacks.onSurfaceDoubleTap,
                activateHoldToSpeedState = gestureCallbacks.activateHoldToSpeed,
                deactivateHoldToSpeedState = gestureCallbacks.deactivateHoldToSpeed,
                revealLockedOverlayState = gestureCallbacks.revealLockedOverlay,
            )
            .playerSurfaceDragGestures(
                gestureController = gestureController,
                layoutSize = layoutSize,
                playbackGesturesEnabled = playbackGesturesEnabled,
                sideGestureSystemEdgeExclusionPx = sideGestureSystemEdgeExclusionPx,
                horizontalSeekSystemEdgeExclusionPx = horizontalSeekSystemEdgeExclusionPx,
                playerControlsLockedState = gestureCallbacks.playerControlsLocked,
                touchGesturesEnabledState = gestureCallbacks.touchGesturesEnabled,
                isHoldToSpeedGestureActiveState = gestureCallbacks.isHoldToSpeedGestureActive,
                currentPositionMsState = gestureCallbacks.currentPositionMs,
                currentDurationMsState = gestureCallbacks.currentDurationMs,
                deactivateHoldToSpeedState = gestureCallbacks.deactivateHoldToSpeed,
                showHorizontalSeekPreviewState = gestureCallbacks.showHorizontalSeekPreview,
                showBrightnessFeedbackState = gestureCallbacks.showBrightnessFeedback,
                showVolumeFeedbackState = gestureCallbacks.showVolumeFeedback,
                clearLiveGestureFeedbackState = gestureCallbacks.clearLiveGestureFeedback,
                revealLockedOverlayState = gestureCallbacks.revealLockedOverlay,
                commitHorizontalSeekState = gestureCallbacks.commitHorizontalSeek,
            ),
    ) {
        val playerSurfaceSourceUrl = if (isP2pPlaybackActive) p2pResolvedSourceUrl else activeSourceUrl
        val playbackKey = activePlaybackKey
        val initialPositionRequestKey = currentInitialPositionRequestKey()

        val resolvedAutoEngineState = remember(sourcePlaybackKey) {
            mutableStateOf<AndroidPlaybackEngine?>(null)
        }
        if (!initialLoadCompleted || resolvedAutoEngineState.value == null) {
            resolvedAutoEngineState.value = resolveAutoPlaybackEngine(
                playbackEngineSetting = playerSettingsUiState.androidPlaybackEngine,
                videoId = activeVideoId,
                streamFilename = activeTorrentFilename ?: activeStreamFilename,
                streamName = activeStreamTitle,
                streamDescription = activeStreamSubtitle,
                contentTitle = title,
                genres = playerMeta?.genres.orEmpty(),
                country = playerMeta?.country,
            )
        }
        val resolvedAutoEngine = resolvedAutoEngineState.value

        if (!playerSurfaceSourceUrl.isNullOrBlank()) {
            key(playbackKey, sourceRetryGeneration) {
                val active = remember { mutableStateOf(true) }
                DisposableEffect(Unit) {
                    onDispose { active.value = false }
                }
                PlatformPlayerSurface(
                    sourceUrl = playerSurfaceSourceUrl,
                    sourceAudioUrl = activeSourceAudioUrl,
                    sourceHeaders = activeSourceHeaders,
                    sourceResponseHeaders = activeSourceResponseHeaders,
                    externalSubtitles = externalSubtitles,
                    streamType = activeStreamType,
                    modifier = videoModifier,
                    playWhenReady = shouldPlay,
                    initialPositionMs = activeInitialPositionMs.takeIf { it > 0L },
                    initialPositionRequestKey = initialPositionRequestKey,
                    resizeMode = if (partyPanelInset > 0.dp) PlayerResizeMode.Fit else resizeMode,
                    playbackEngine = playbackEngineOverride ?: resolvedAutoEngine,
                    onInitialPositionHandled = { key, handled ->
                        if (active.value && playbackKey == activePlaybackKey && key == currentInitialPositionRequestKey()) {
                            initialSeekApplied = handled
                        }
                    },
                    onControllerReady = { controller ->
                        if (active.value && playbackKey == activePlaybackKey) {
                            playerController = controller
                            playerControllerSourceUrl = activeSourceUrl
                        }
                    },
                    onSnapshot = { snapshot ->
                        if (!active.value || !updatePlaybackSnapshot(snapshot, playbackKey)) return@PlatformPlayerSurface
                        refreshAudioTracksIfChanged()
                        if (!snapshot.isLoading) initialLoadCompleted = true
                        if (snapshot.isEnded) {
                            shouldPlay = false
                            controlsVisible = !playerControlsLocked
                        }
                    },
                    onError = { message ->
                        if (!active.value || playbackKey != activePlaybackKey) return@PlatformPlayerSurface
                        if (message != null && tryRefreshExpiredLinkAfterError(message)) {
                            return@PlatformPlayerSurface
                        }
                        if (message != null && tryFallbackToNextSourceAfterError(message)) {
                            return@PlatformPlayerSurface
                        }
                        // Engine failover is decided here, driven by its own player setting;
                        // the engine's own decoder/MIME retries have already been tried.
                        if (message != null && maybeFailoverPlaybackEngine(resolvedAutoEngine)) {
                            return@PlatformPlayerSurface
                        }
                        errorMessage = message
                        if (message != null) {
                            scrubbingPositionMs = null
                            controlsVisible = !playerControlsLocked
                            removeFailedStreamFromCache()
                        }
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = playerSettingsUiState.pauseOverlayEnabled && pausedOverlayVisible && !controlsVisible && !playerControlsLocked && fold == null,
            enter = fadeIn(animationSpec = tween(durationMillis = 220)),
            exit = fadeOut(animationSpec = tween(durationMillis = 180)),
        ) {
            PauseMetadataOverlay(
                title = title,
                logo = logo,
                isEpisode = isEpisode,
                seasonNumber = activeSeasonNumber,
                episodeNumber = activeEpisodeNumber,
                episodeTitle = activeEpisodeTitle,
                pauseDescription = activePauseDescription ?: activeStreamSubtitle,
                providerName = activeProviderName,
                metrics = metrics,
                horizontalSafePadding = horizontalSafePadding,
                modifier = Modifier.fillMaxSize(),
            )
        }

        RenderPlayerControls(
            introComplete = introComplete,
            displayedPositionMs = displayedPositionMs,
            isEpisode = isEpisode,
            folded = fold != null,
            initialLoadingMessage = p2pInitialLoadingMessage,
            modifier = panelModifier,
        )
        Box(modifier = videoModifier) {
            PlaybackTitleOverlay(
                title = title,
                episode = if (isEpisode) "S$activeSeasonNumber E$activeEpisodeNumber${activeEpisodeTitle?.let { " · $it" }.orEmpty()}" else null,
                visible = introVisible,
                logoUrl = nowPlayingLogo,
                metaLine = metaUiState.meta?.let { playbackTitleMetaLine(it.releaseInfo, it.genres, it.runtime) }.orEmpty(),
                rating = metaUiState.meta?.let { it.imdbRating ?: it.tmdbRating },
                modifier = Modifier.align(Alignment.TopStart).padding(start = 48.dp, top = 44.dp),
            )
            // One owner for playback loading, independent of visible, hidden, locked or folded controls.
            val hasPlaybackSource = activeSourceUrl.isNotBlank() || activeTorrentInfoHash != null
            val awaitingInitialPlayback = !initialLoadCompleted && hasPlaybackSource
            if (!isInPip && errorMessage == null && hasPlaybackSource &&
                (playbackSnapshot.isLoading || awaitingInitialPlayback)
            ) {
                CinemaosGradientLoader(
                    modifier = Modifier.align(Alignment.Center).size(metrics.playIconSize + 20.dp),
                )
            }
            RenderPlaybackOverlays(
                runtime = runtime,
                displayedPositionMs = displayedPositionMs,
                currentGestureFeedback = currentGestureFeedback,
                p2pInitialLoadingMessage = p2pInitialLoadingMessage,
                p2pInitialLoadingProgress = p2pInitialLoadingProgress,
                showP2pRebufferStats = showP2pRebufferStats,
                p2pRebufferMessage = p2pRebufferMessage,
                p2pRebufferProgress = p2pRebufferProgress,
                folded = fold != null,
            )
        }
    }
    Box(modifier = panelModifier) {
        RenderParty(
            runtime = runtime,
            panelWidth = partyPanelWidth,
            modal = !dockPartyPanel,
            panelVisible = showPartyPanel && !isInPip,
        )
        RenderPlayerModals(displayedPositionMs = displayedPositionMs)
    }
}

@Composable
private fun p2pConnectingPhaseLabel(phase: String): String = when (phase) {
    "add_magnet" -> org.jetbrains.compose.resources.stringResource(
        nuvio.composeapp.generated.resources.Res.string.player_torrent_fetching_metadata,
    )
    "prepare_stream", "attach_route" -> org.jetbrains.compose.resources.stringResource(
        nuvio.composeapp.generated.resources.Res.string.player_torrent_preparing_stream,
    )
    else -> org.jetbrains.compose.resources.stringResource(
        nuvio.composeapp.generated.resources.Res.string.player_torrent_starting_engine,
    )
}

private val PlayerScreenRuntime.activeAddonLogo: String?
    get() = addonsUiState.addons.firstNotNullOfOrNull { addon ->
        addon.manifest?.takeIf { addon.streamAddonInstanceId(it.id) == activeProviderAddonId }?.logoUrl
    }

private fun PlayerScreenRuntime.currentInitialPositionRequestKey(): String? {
    val positionMs = activeInitialPositionMs.takeIf { it > 0L } ?: return null
    return "$activePlaybackIdentity:${activeVideoId.orEmpty()}:$positionMs"
}

@Composable
private fun PlayerScreenRuntime.RenderPlayerControls(
    introComplete: Boolean,
    displayedPositionMs: Long,
    isEpisode: Boolean,
    folded: Boolean,
    initialLoadingMessage: String?,
    modifier: Modifier,
) {
    val isInPip = rememberIsInPictureInPicture()
    val partyState by PartyRepository.state.collectAsStateWithLifecycle()
    val partyActive = partyState.party != null
    val playerBadges = rememberPlayerBadges()
    val awaitingFirstFrame = (activeSourceUrl.isNotBlank() || activeTorrentInfoHash != null) &&
        !initialLoadCompleted && errorMessage == null
    val bufferingNow = activeSourceUrl.isNotBlank() && playbackSnapshot.isLoading && errorMessage == null
    val isLive = isLivePlayback
    LaunchedEffect(folded) {
        if (folded) {
            controlsVisible = true
            showParentalGuide = false
        }
    }
    AnimatedVisibility(
        visible = (controlsVisible || folded || showParentalGuide || awaitingFirstFrame || bufferingNow) && !playerControlsLocked && !isInPip &&
            !(folded && isPanelOpen),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        PlayerControlsShell(
            title = title,
            streamTitle = activeStreamTitle,
            providerName = activeProviderName,
            seasonNumber = activeSeasonNumber,
            episodeNumber = activeEpisodeNumber,
            episodeTitle = activeEpisodeTitle,
            playbackSnapshot = playbackSnapshot,
            displayedPositionMs = displayedPositionMs,
            metrics = metrics,
            resizeMode = resizeMode,
            isLocked = playerControlsLocked,
            useLegacyLayout = playerSettingsUiState.useLegacyPlayerLayout,
            showRemainingTime = showRemainingTime,
            onRuntimeClick = { showRemainingTime = !showRemainingTime },
            releaseInfo = metaUiState.meta?.takeIf { it.id == parentMetaId }?.releaseInfo,
            badges = playerBadges,
            hideDetails = activeSkipInterval != null && !skipIntervalDismissed,
            isLive = isLive,
            onNextEpisodeClick = if (!isLive && nextEpisodeInfo?.hasAired == true && !nextEpisodeAutoPlaySearching && nextEpisodeAutoPlayCountdown == null) {
                {
                    if (!partyBlocksControl()) playNextEpisode()
                }
            } else null,
            onInteraction = { controlsActivityTick += 1 },
            showPlaybackControls = controlsVisible || awaitingFirstFrame || bufferingNow,
            awaitingFirstFrame = awaitingFirstFrame,
            loadingMessage = if (playerSettingsUiState.showPlayerLoadingStatus && awaitingFirstFrame) {
                if (sourceFallbackAttempts > 0) {
                    stringResource(Res.string.player_loading_trying_next_source, sourceFallbackAttempts)
                } else {
                    initialLoadingMessage ?: playerLoadingStatusMessage(
                        showStatus = true,
                        controllerReady = playerController != null,
                        snapshot = playbackSnapshot,
                    )
                }
            } else null,
            onLockToggle = {
                if (playerControlsLocked) unlockPlayerControls() else lockPlayerControls()
            },
            onBack = {
                flushWatchProgress()
                args.onBack()
            },
            onPartyClick = if (PartyRepository.available.collectAsStateWithLifecycle().value) {
                { openPartyPanel() }
            } else {
                null
            },
            partyActive = partyActive,
            onTogglePlayback = { togglePlayback() },
            onSeekBack = { seekBy(-10_000L) },
            onSeekForward = { seekBy(10_000L) },
            onResizeModeClick = { cycleResizeMode() },
            onSpeedClick = { cyclePlaybackSpeed() },
            onSubtitleClick = {
                refreshTracks()
                showSubtitleModal = true
            },
            onAudioClick = {
                refreshTracks()
                showAudioModal = true
            },
            onVideoSettingsClick = {
                showVideoSettingsModal = true
                controlsVisible = true
            },
            onSourcesClick = if (activeVideoId != null) { { openSourcesPanel() } } else null,
            onQualityClick = if (activeVideoId != null) { { openQualityPanel() } } else null,
            qualityLabel = parseStreamInfo(listOf(activeStreamTitle, activeStreamSubtitle, activeSourceFilename)).qualityLabel,
            onEpisodesClick = if (isSeries && !isLive) { { openEpisodesPanel() } } else null,
            chapters = if (isLive) emptyList() else chapters,
            onChaptersClick = if (chapters.isNotEmpty() && !isLive) { { openChaptersPanel() } } else null,
            onOpenInExternalPlayer = args.onOpenInExternalPlayer
                ?.takeUnless { ServerStreams.isServerSourceId(activeProviderAddonId) }
                ?.let { openExternal ->
                {
                    val loadedSubtitles = addonSubtitles
                        .takeIf { it.isNotEmpty() }
                        ?.map { sub ->
                            SubtitleInput(
                                url = sub.url,
                                name = buildString {
                                    if (!sub.addonName.isNullOrBlank()) append("[${sub.addonName}] ")
                                    append(sub.display)
                                },
                                lang = sub.language,
                            )
                        }
                    PlayerStreamsRepository.pauseSearchForPlayback()
                    openExternal(
                        ExternalPlayerPlaybackRequest(
                            sourceUrl = activeSourceUrl,
                            title = title,
                            streamTitle = activeStreamTitle,
                            sourceHeaders = activeSourceHeaders,
                            resumePositionMs = playbackSnapshot.positionMs,
                            durationMs = playbackSnapshot.durationMs.takeIf { it > 0L },
                            playbackSession = playbackSession,
                            subtitles = loadedSubtitles,
                            season = activeSeasonNumber,
                            episode = activeEpisodeNumber,
                            episodeTitle = activeEpisodeTitle,
                        ),
                    )
                }
            },
            onSubmitIntroClick = if (
                isSeries && !isLive &&
                playerSettingsUiState.introSubmitEnabled &&
                playerSettingsUiState.introDbApiKey.isNotBlank()
            ) {
                { showSubmitIntroModal = true }
            } else {
                null
            },
            onSwitchEngineClick = if (playerController?.playbackEngine != null) {
                { showSwitchEngineConfirm = true }
            } else {
                null
            },
            onStreamInfoClick = { openStreamInfo() },
            parentalWarnings = parentalWarnings,
            showParentalGuide = showParentalGuide && introComplete,
            onParentalGuideAnimationComplete = { showParentalGuide = false },
            onScrubChange = { positionMs ->
                if (!isLive) {
                    isScrubbingTimeline = true
                    scrubbingPositionMs = positionMs
                }
            },
            onScrubFinished = { positionMs ->
                val targetMs = when {
                    isLive -> null
                    isPartyControlLocked -> ownSeekTarget(positionMs)
                    else -> positionMs
                }
                if (targetMs == null) {
                    isScrubbingTimeline = false
                    scrubbingPositionMs = null
                } else {
                    finishTimelineScrub(targetMs)
                    playerController?.seekTo(targetMs)
                    scheduleProgressSyncAfterSeek()
                    publishPartyPlayback(positionMs = targetMs)
                }
            },
            horizontalSafePadding = horizontalSafePadding,
            modifier = modifier,
            foldLayout = folded,
            gestureController = gestureController,
            onWake = {
                controlsVisible = true
                controlsActivityTick += 1
            },
        )
    }
    AnimatedVisibility(
        visible = folded && playerControlsLocked && !isInPip,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        FoldLockedControls(
            onUnlock = { unlockPlayerControls() },
            modifier = modifier,
        )
    }
}

@Composable
private fun BoxScope.RenderPlaybackOverlays(
    runtime: PlayerScreenRuntime,
    displayedPositionMs: Long,
    currentGestureFeedback: GestureFeedbackState?,
    p2pInitialLoadingMessage: String?,
    p2pInitialLoadingProgress: Float?,
    showP2pRebufferStats: Boolean,
    p2pRebufferMessage: String?,
    p2pRebufferProgress: Float?,
    folded: Boolean,
) {
    runtime.run {
        PlayerPlaybackOverlays(
            playerControlsLocked = playerControlsLocked,
            useLegacyLayout = playerSettingsUiState.useLegacyPlayerLayout,
            lockedOverlayVisible = lockedOverlayVisible && !folded,
            showRemainingTime = showRemainingTime,
            playbackSnapshot = playbackSnapshot,
        displayedPositionMs = displayedPositionMs,
        metrics = metrics,
        horizontalSafePadding = horizontalSafePadding,
        onUnlock = { unlockPlayerControls() },
        showOpeningOverlay = playerSettingsUiState.showLoadingOverlay && !initialLoadCompleted && errorMessage == null &&
            activeSourceUrl.isBlank() && activeTorrentInfoHash == null,
        backdropArtwork = background ?: poster,
        logo = logo,
        title = title,
        onBackWithProgress = {
            flushWatchProgress()
            args.onBack()
        },
        openingLoadingMessage = if (playerSettingsUiState.showPlayerLoadingStatus) {
            (if (sourceFallbackAttempts > 0 && !initialLoadCompleted) {
                stringResource(Res.string.player_loading_trying_next_source, sourceFallbackAttempts)
            } else null) ?: p2pInitialLoadingMessage ?: playerLoadingStatusMessage(
                showStatus = true,
                controllerReady = playerController != null,
                snapshot = playbackSnapshot,
            )
        } else null,
        p2pInitialLoadingProgress = p2pInitialLoadingProgress,
        showP2pRebufferStats = showP2pRebufferStats,
        p2pRebufferMessage = p2pRebufferMessage,
        p2pRebufferProgress = p2pRebufferProgress,
        currentGestureFeedback = currentGestureFeedback,
        renderedGestureFeedback = renderedGestureFeedback,
        initialLoadCompleted = initialLoadCompleted,
        pausedOverlayVisible = pausedOverlayVisible,
        activeSkipInterval = activeSkipInterval,
        skipsToPostCredits = activeSkipInterval?.internalSkipAction(skipIntervals, playbackSnapshot.durationMs)?.skipsToPostCredits == true,
        skipIntervalDismissed = skipIntervalDismissed,
        controlsVisible = controlsVisible,
        onSkipInterval = { interval ->
            interval.internalSkipAction(skipIntervals, playbackSnapshot.durationMs)?.let { action ->
                val durationMs = playbackSnapshot.durationMs
                val requestedMs = if (durationMs > 0L) action.targetMs.coerceAtMost(durationMs - 1) else action.targetMs
                val seekMs = if (isPartyControlLocked) ownSeekTarget(requestedMs) else requestedMs
                if (seekMs != null) {
                    playerController?.seekTo(seekMs)
                    scheduleProgressSyncAfterSeek()
                    publishPartyPlayback(positionMs = seekMs)
                    skipIntervalDismissed = true
                }
            }
        },
        onDismissSkipInterval = { skipIntervalDismissed = true },
        sliderEdgePadding = sliderEdgePadding,
        overlayBottomPadding = overlayBottomPadding,
        isSeries = isSeries,
        nextEpisodeInfo = nextEpisodeInfo,
        showNextEpisodeCard = showNextEpisodeCard,
        nextEpisodeAutoPlaySearching = nextEpisodeAutoPlaySearching,
        nextEpisodeAutoPlaySourceName = nextEpisodeAutoPlaySourceName,
        nextEpisodeAutoPlayCountdown = nextEpisodeAutoPlayCountdown,
        blurUnwatchedEpisodes = metaScreenSettingsUiState.blurUnwatchedEpisodes,
        onPlayNextEpisode = {
            if (!partyBlocksControl()) playNextEpisode()
        },
        onDismissNextEpisode = {
            cancelNextEpisodeAutoPlay()
            nextEpisodeCardDismissed = true
            showNextEpisodeCard = false
        },
        errorMessage = errorMessage,
            onDismissError = {
                flushWatchProgress()
                args.onBack()
            },
        )
    }
}

@Composable
private fun PlayerScreenRuntime.RenderPlayerModals(displayedPositionMs: Long) {
    PlayerChaptersPanel(
        visible = showChaptersPanel && chapters.isNotEmpty(),
        chapters = chapters,
        positionMs = displayedPositionMs,
        durationMs = playbackSnapshot.durationMs,
        onChapterSelected = { selectChapter(it) },
        onDismiss = {
            showChaptersPanel = false
            controlsVisible = true
        },
    )
    PlayerScreenModalHosts(
        pendingP2pSwitch = pendingP2pSwitch,
        onPendingP2pSwitchChanged = { pendingP2pSwitch = it },
        onP2pEpisodeStreamSelected = { stream, episode, isAutoPlay ->
            switchToP2pEpisodeStream(stream, episode, isAutoPlay)
        },
        onP2pSourceStreamSelected = { stream -> switchToP2pSourceStream(stream) },
        onNextEpisodeAutoPlaySearchingChanged = { nextEpisodeAutoPlaySearching = it },
        onNextEpisodeAutoPlayCountdownChanged = { nextEpisodeAutoPlayCountdown = it },
        onNextEpisodeAutoPlaySourceNameChanged = { nextEpisodeAutoPlaySourceName = it },
        showAudioModal = showAudioModal,
        audioTracks = serverAudioTracks.ifEmpty { audioTracks },
        selectedAudioIndex = serverAudioTracks.firstOrNull { it.isSelected }?.index ?: selectedAudioIndex,
        onAudioTrackSelected = { index ->
            if (serverAudioTracks.isNotEmpty()) {
                selectServerAudioTrack(index)
            } else {
                selectedAudioIndex = index
                persistAudioPreference(audioTracks.firstOrNull { it.index == index })
                playerController?.selectAudioTrack(index)
            }
            scope.launch {
                kotlinx.coroutines.delay(200)
                showAudioModal = false
            }
        },
        onAudioModalDismissed = { showAudioModal = false },
        showAudioDelay = playerController?.supportsAudioDelay == true,
        audioDelayMs = audioDelayMs,
        onAudioDelayChanged = { delayMs -> setAudioDelay(delayMs) },
        showAudioAmplification = playerController?.supportsAudioAmplification == true,
        audioAmplificationDb = audioAmplificationDb,
        keepAudioAmplification = playerSettingsUiState.persistAudioAmplification,
        onAudioAmplificationChanged = { db -> setAudioAmplification(db) },
        onKeepAudioAmplificationChanged = { keep -> setKeepAudioAmplification(keep) },
        showCenterMix = centerMixAvailable,
        centerMixLevelDb = centerMixLevelDb,
        onCenterMixLevelChanged = { db -> setCenterMixLevel(db) },
        showSubtitleModal = showSubtitleModal,
        subtitleTracks = serverSubtitleTracks.ifEmpty { subtitleTracks },
        selectedSubtitleIndex = if (serverSubtitleTracks.isEmpty()) {
            selectedSubtitleIndex
        } else {
            serverSubtitleTracks.firstOrNull { it.isSelected }?.index ?: -1
        },
        addonSubtitles = visibleAddonSubtitles,
        selectedAddonSubtitleId = selectedAddonSubtitleId,
        isLoadingAddonSubtitles = isLoadingAddonSubtitles,
        subtitleStyle = subtitleStyle,
        subtitleDelayMs = subtitleDelayMs,
        selectedAddonSubtitle = selectedAddonSubtitle,
        subtitleAutoSyncState = subtitleAutoSyncState,
        onBuiltInSubtitleTrackSelected = { index ->
            if (serverSubtitleTracks.isNotEmpty() && index >= 0) {
                selectServerSubtitleTrack(index)
            } else {
                val wasCustom = useCustomSubtitles
                isUserExplicitSubtitleSelection = true
                preferredSubtitleSelectionApplied = true
                selectedSubtitleIndex = index
                selectedAddonSubtitleId = null
                useCustomSubtitles = false
                persistInternalSubtitlePreference(subtitleTracks.firstOrNull { it.index == index })
                if (wasCustom) {
                    playerController?.clearExternalSubtitleAndSelect(index)
                } else {
                    playerController?.selectSubtitleTrack(index)
                }
                if (hasBurnedInServerSubtitle) clearServerSubtitleTrack()
            }
        },
        onAddonSubtitleSelected = { addon ->
            isUserExplicitSubtitleSelection = true
            selectedAddonSubtitleId = addon.selectionKey
            selectedSubtitleIndex = -1
            useCustomSubtitles = true
            preferredSubtitleSelectionApplied = true
            persistAddonSubtitlePreference(addon)
            attachSelectedAddonSubtitleWithAutoSync(addon.url) // AutoSync hook
            if (hasBurnedInServerSubtitle) clearServerSubtitleTrack()
        },
        onFetchAddonSubtitles = { fetchAddonSubtitlesForActiveItem() },
        onSubtitleStyleChanged = PlayerSettingsRepository::setSubtitleStyle,
        onSubtitleDelayChanged = { delayMs -> setSubtitleDelay(delayMs) },
        onSubtitleDelayReset = { setSubtitleDelay(0) },
        onAutoSyncCapture = { captureSubtitleAutoSyncTime() },
        onAutoSyncCueSelected = { cue -> applySubtitleAutoSyncCue(cue) },
        onAutoSyncReload = { loadSubtitleAutoSyncCues(force = true) },
        onSubtitleModalDismissed = { showSubtitleModal = false },
        showVideoSettingsModal = showVideoSettingsModal,
        playerSettings = playerSettingsUiState,
        settingsQuickActions = buildList {
            if (!isLivePlayback) {
                add(PlayerSettingsQuickAction(stringResource(Res.string.compose_player_speed), formatPlaybackSpeedLabel(playbackSnapshot.playbackSpeed), { cyclePlaybackSpeed() }))
            }
            add(PlayerSettingsQuickAction(stringResource(Res.string.cinemaos_player_aspect_ratio), stringResource(resizeMode.labelRes), { cycleResizeMode() }))
            if (activeVideoId != null) add(PlayerSettingsQuickAction(stringResource(Res.string.cinemaos_player_quality), onClick = {
                showVideoSettingsModal = false; openQualityPanel()
            }))
            if (playerController?.playbackEngine != null) add(PlayerSettingsQuickAction(stringResource(Res.string.cd_switch_player_engine), onClick = {
                showVideoSettingsModal = false; showSwitchEngineConfirm = true
            }))
            if (chapters.isNotEmpty() && !isLivePlayback) add(PlayerSettingsQuickAction(stringResource(Res.string.compose_player_chapters), onClick = {
                showVideoSettingsModal = false; openChaptersPanel()
            }))
            if (isSeries && !isLivePlayback && playerSettingsUiState.introSubmitEnabled && playerSettingsUiState.introDbApiKey.isNotBlank()) {
                add(PlayerSettingsQuickAction(stringResource(Res.string.submit_intro_action), onClick = {
                    showVideoSettingsModal = false; showSubmitIntroModal = true
                }))
            }
            add(PlayerSettingsQuickAction(stringResource(Res.string.cd_stream_info), onClick = {
                showVideoSettingsModal = false; openStreamInfo()
            }))
        },
        onVideoSettingsChanged = {
            playerController?.configureIosVideoOutput(PlayerSettingsRepository.uiState.value)
        },
        onVideoSettingsModalDismissed = { showVideoSettingsModal = false },
        showSourcesPanel = showSourcesPanel,
        showQualityPanel = showQualityPanel,
        onQualityPanelDismissed = {
            showQualityPanel = false
            PlayerStreamsRepository.stopSourcesLoading()
            controlsVisible = true
        },
        sourceStreamsState = sourceStreamsState,
        contentTitle = title,
        activeEpisodeTitle = activeEpisodeTitle,
        currentStream = PlayerCurrentStream(
            url = activeSourceUrl,
            name = activeStreamTitle,
            description = activeStreamSubtitle,
            addonId = activeProviderAddonId,
            infoHash = activeStream?.p2pInfoHash ?: activeTorrentInfoHash,
            fileIdx = activeStream?.p2pFileIdx ?: activeTorrentFileIdx,
            filename = activeSourceFilename,
        ),
        onSourceFilterSelected = PlayerStreamsRepository::selectSourceFilter,
        onSourceStreamSelected = { stream -> switchToSource(stream) },
        onDownloadSourceStream = { stream ->
            if (!isPlayingSourceStream(stream)) {
                // Rows other than the playing one download their own link (see PlayerSourceDownloads.kt).
                downloadOtherSourceStream(stream)
            } else if (activeTorrentInfoHash != null || !activeSourceAudioUrl.isNullOrBlank()) {
                com.nuvio.app.core.ui.NuvioToastController.show(
                    com.nuvio.app.features.downloads.DownloadEnqueueResult.UnsupportedFormat.toastMessage(),
                )
            } else {
                val downloadStream = stream.copy(
                    url = activeSourceUrl,
                    behaviorHints = stream.behaviorHints.copy(
                        proxyHeaders = com.nuvio.app.features.streams.StreamProxyHeaders(
                            request = activeSourceHeaders,
                            response = activeSourceResponseHeaders,
                        ),
                    ),
                )
                val result = com.nuvio.app.features.downloads.DownloadsRepository.enqueueFromStream(
                    contentType = contentType ?: parentMetaType,
                    videoId = activeVideoId ?: parentMetaId,
                    parentMetaId = parentMetaId,
                    parentMetaType = parentMetaType,
                    title = title,
                    logo = logo,
                    poster = poster,
                    background = background,
                    seasonNumber = activeSeasonNumber,
                    episodeNumber = activeEpisodeNumber,
                    episodeTitle = activeEpisodeTitle,
                    episodeThumbnail = activeEpisodeThumbnail,
                    stream = downloadStream,
                )
                com.nuvio.app.core.ui.NuvioToastController.show(result.toastMessage())
            }
        },
        onReloadSources = {
            val vid = activeVideoId
            if (vid != null) {
                PlayerStreamsRepository.loadSources(
                    type = contentType ?: parentMetaType,
                    videoId = vid,
                    season = activeSeasonNumber,
                    episode = activeEpisodeNumber,
                    forceRefresh = true,
                )
            }
        },
        onSourcesPanelDismissed = {
            showSourcesPanel = false
            PlayerStreamsRepository.stopSourcesLoading()
            controlsVisible = true
        },
        isSeries = isSeries,
        showEpisodesPanel = showEpisodesPanel,
        allEpisodes = playerMetaVideos,
        parentMetaType = parentMetaType,
        parentMetaId = parentMetaId,
        activeSeasonNumber = activeSeasonNumber,
        activeEpisodeNumber = activeEpisodeNumber,
        watchProgressByVideoId = watchProgressUiState.byVideoIdForContent(parentMetaId),
        watchedKeys = watchedUiState.watchedKeys,
        blurUnwatchedEpisodes = metaScreenSettingsUiState.blurUnwatchedEpisodes,
        episodeStreamsPanelState = episodeStreamsPanelState,
        episodeStreamsRepoState = episodeStreamsRepoState,
        onEpisodeSelectedForDownload = { episode ->
            selectDownloadedEpisodeForPlayback(
                parentMetaId = parentMetaId,
                episode = episode,
                onDownloadedEpisodeSelected = { item, video -> switchToDownloadedEpisode(item, video) },
            )
        },
        onEpisodeStreamsRequested = { episode ->
            PlayerStreamsRepository.loadEpisodeStreams(
                type = contentType ?: parentMetaType,
                videoId = episode.id,
                season = episode.season,
                episode = episode.episode,
            )
            episodeStreamsPanelState = EpisodeStreamsPanelState(showStreams = true, selectedEpisode = episode)
        },
        onEpisodeStreamFilterSelected = PlayerStreamsRepository::selectEpisodeStreamsFilter,
        onEpisodeStreamSelected = { stream, episode -> switchToEpisodeStream(stream, episode) },
        onBackToEpisodes = {
            episodeStreamsPanelState = EpisodeStreamsPanelState()
            PlayerStreamsRepository.clearEpisodeStreams()
        },
        onReloadEpisodeStreams = {
            val episode = episodeStreamsPanelState.selectedEpisode
            if (episode != null) {
                PlayerStreamsRepository.loadEpisodeStreams(
                    type = contentType ?: parentMetaType,
                    videoId = episode.id,
                    season = episode.season,
                    episode = episode.episode,
                    forceRefresh = true,
                )
            }
        },
        onEpisodesPanelDismissed = {
            showEpisodesPanel = false
            episodeStreamsPanelState = EpisodeStreamsPanelState()
            PlayerStreamsRepository.clearEpisodeStreams()
            controlsVisible = true
        },
        showSubmitIntroModal = showSubmitIntroModal,
        activeVideoId = activeVideoId,
        metaUiState = metaUiState,
        displayedPositionMs = displayedPositionMs,
        submitIntroSegmentType = submitIntroSegmentType,
        onSubmitIntroSegmentTypeChanged = { submitIntroSegmentType = it },
        submitIntroStartTimeStr = submitIntroStartTimeStr,
        onSubmitIntroStartTimeChanged = { submitIntroStartTimeStr = it },
        submitIntroEndTimeStr = submitIntroEndTimeStr,
        onSubmitIntroEndTimeChanged = { submitIntroEndTimeStr = it },
        onSubmitIntroDismissed = { showSubmitIntroModal = false },
        onSubmitIntroSuccess = {
            submitIntroStartTimeStr = "00:00"
            submitIntroEndTimeStr = "00:00"
            submitIntroSegmentType = "intro"
            showSubmitIntroModal = false
        },
    )
    StreamInfoOverlay(
        visible = showStreamInfo,
        addonName = activeProviderName,
        addonLogo = activeAddonLogo,
        streamName = activeStreamTitle,
        streamDescription = activeStreamSubtitle,
        playbackEngine = playerController?.playbackEngine,
        serverPlayback = serverPlaybackSummary(),
        mediaInfo = streamMediaInfo,
        audioTrack = audioTracks.firstOrNull { it.index == selectedAudioIndex },
        subtitleTrack = subtitleTracks.firstOrNull { it.index == selectedSubtitleIndex }.takeIf { !useCustomSubtitles },
        addonSubtitle = selectedAddonSubtitle.takeIf { useCustomSubtitles },
        onDismiss = { showStreamInfo = false },
    )
    val switchEngineTarget = playerController?.playbackEngine?.switchTarget
    if (switchEngineTarget != null) {
        NuvioStatusModal(
            isVisible = showSwitchEngineConfirm,
            title = stringResource(Res.string.player_engine_switch_title, switchEngineTarget.label),
            message = stringResource(Res.string.player_engine_switch_message),
            confirmText = stringResource(Res.string.action_switch),
            dismissText = stringResource(Res.string.action_cancel),
            onConfirm = {
                showSwitchEngineConfirm = false
                switchPlaybackEngine()
            },
            onDismiss = { showSwitchEngineConfirm = false },
        )
    }
}
