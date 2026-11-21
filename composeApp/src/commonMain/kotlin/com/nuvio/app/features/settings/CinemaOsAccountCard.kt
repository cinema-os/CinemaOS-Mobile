package com.nuvio.app.features.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.features.cinemaos.CinemaOsAccount
import com.nuvio.app.features.cinemaos.CinemaOsDeviceCode
import com.nuvio.app.features.cinemaos.CinemaOsPollResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * cinemaos.tech account: shows signed-in state (sign out lives on the Account page), or the device login (code + QR that the
 * user approves on cinemaos.tech/tv-login, or "Sign in on this phone" which opens the same page
 * with the code pre-filled in the browser while the app keeps polling).
 */
@Composable
internal fun CinemaOsAccountCard() {
    val service = CinemaOsAccount.service
    if (!service.isSupported) return

    val session by service.session.collectAsStateWithLifecycle()
    val isSyncing by service.isSyncing.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    var flowActive by remember { mutableStateOf(false) }
    // When true, the website's approval page opens in the browser as soon as the code is issued.
    var openBrowserWhenReady by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf<CinemaOsDeviceCode?>(null) }
    var secondsLeft by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    val signedIn = session != null

    LaunchedEffect(flowActive, signedIn) {
        if (!flowActive || signedIn) {
            code = null
            return@LaunchedEffect
        }
        error = null
        try {
            while (isActive && service.session.value == null) {
                val started = service.startDeviceLogin().getOrNull()
                if (started == null) {
                    error = "Cannot reach cinemaos.tech. Retrying..."
                    delay(5_000)
                    continue
                }
                error = null
                code = started
                if (openBrowserWhenReady) {
                    openBrowserWhenReady = false
                    runCatching { uriHandler.openUri(started.verificationUrlComplete) }
                }
                var interval = started.intervalSec
                var elapsed = 0
                var sincePoll = 0
                var done = false
                while (isActive && !done) {
                    val left = started.expiresInSec - elapsed
                    if (left <= 0) break
                    secondsLeft = left
                    delay(1_000)
                    elapsed++
                    sincePoll++
                    if (sincePoll < interval) continue
                    sincePoll = 0
                    val result = service.pollDeviceLogin(started.deviceCode).getOrNull() ?: continue
                    when (result) {
                        is CinemaOsPollResult.Approved -> {
                            service.completeSignIn(result.customToken)
                                .onSuccess { service.syncNow() }
                                .onFailure { error = "Sign-in failed. Try again." }
                            done = true
                        }
                        CinemaOsPollResult.SlowDown -> interval += 5
                        CinemaOsPollResult.Expired -> break
                        CinemaOsPollResult.Denied -> {
                            error = "Sign-in was denied on the website."
                            flowActive = false
                            done = true
                        }
                        CinemaOsPollResult.Pending -> Unit
                    }
                }
                if (done) {
                    flowActive = false
                    return@LaunchedEffect
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
    }

    SettingsGroup(isTablet = false) {
      Column(modifier = Modifier.padding(18.dp)) {
        Text(
            text = "CinemaOS account",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        val current = session
        if (current != null) {
            Text(
                text = "Signed in as ${current.displayName ?: current.label}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!current.displayName.isNullOrBlank() && current.displayName != current.label) {
                Text(text = current.label, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isSyncing) {
                    "Syncing continue watching and watchlist..."
                } else {
                    "Continue watching and watchlist are synced with cinemaos.tech."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(14.dp))
            NuvioPrimaryButton(
                text = "Sync now",
                enabled = !isSyncing,
                onClick = { service.syncNow() },
            )
        } else if (!flowActive) {
            Text(
                text = "Sign in with your cinemaos.tech account to sync continue watching and your watchlist.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NuvioPrimaryButton(
                    text = "Log in with CinemaOS website",
                    onClick = {
                        error = null
                        openBrowserWhenReady = true
                        flowActive = true
                    },
                )
                TextButton(
                    onClick = {
                        error = null
                        openBrowserWhenReady = false
                        flowActive = true
                    },
                ) {
                    Text("Show a code or QR instead")
                }
            }
        } else {
            val dc = code
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (dc == null) {
                    Text(
                        text = error ?: "Getting a sign-in code...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        text = "Open ${dc.verificationUrl} on another device and enter this code, or scan the QR code.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    val qr = remember(dc.verificationUrlComplete) {
                        service.qrCode(dc.verificationUrlComplete)
                    }
                    if (qr != null) {
                        Image(
                            bitmap = qr,
                            contentDescription = "QR code to sign in",
                            modifier = Modifier.size(180.dp),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Text(
                        text = dc.userCode,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Code expires in ${secondsLeft / 60}:${(secondsLeft % 60).toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    NuvioPrimaryButton(
                        text = "Open cinemaos.tech to approve",
                        onClick = { runCatching { uriHandler.openUri(dc.verificationUrlComplete) } },
                    )
                }
                error?.takeIf { dc != null }?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = {
                    openBrowserWhenReady = false
                    flowActive = false
                }) {
                    Text("Cancel")
                }
            }
        }
      }
    }
}
