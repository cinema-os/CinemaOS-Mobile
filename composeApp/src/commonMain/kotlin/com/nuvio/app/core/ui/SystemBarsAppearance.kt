package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable

/**
 * Keeps system status and navigation bar icons readable: dark icons when the app background is
 * light, light icons otherwise. No-op on platforms without such bars.
 */
@Composable
expect fun SystemBarsAppearance(lightBackground: Boolean)
