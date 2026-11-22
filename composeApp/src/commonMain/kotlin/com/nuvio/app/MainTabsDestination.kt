package com.nuvio.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.LocalNuvioBottomNavigationOverlayPadding
import com.nuvio.app.core.ui.LocalNuvioNavBarScrollState
import com.nuvio.app.core.ui.NuvioNavBarScrollState
import com.nuvio.app.core.ui.DockIconKind
import com.nuvio.app.core.ui.FloatingNavigationBar
import com.nuvio.app.core.ui.FloatingNavigationItem
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.rememberNuvioNavBarScrollState
import com.nuvio.app.features.profiles.NuvioProfile
import com.nuvio.app.features.settings.NavBarStyle
import com.nuvio.app.features.settings.ThemeSettingsRepository
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_nav_anime
import nuvio.composeapp.generated.resources.compose_nav_home
import nuvio.composeapp.generated.resources.compose_nav_library
import nuvio.composeapp.generated.resources.compose_nav_profile
import nuvio.composeapp.generated.resources.compose_nav_search
import nuvio.composeapp.generated.resources.compose_nav_sports
import nuvio.composeapp.generated.resources.dock_anime
import nuvio.composeapp.generated.resources.dock_home
import nuvio.composeapp.generated.resources.dock_library
import nuvio.composeapp.generated.resources.dock_search
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MainTabsDestination(
    selectedTab: AppScreenTab,
    initialHomeReady: Boolean,
    rootRouteActive: Boolean,
    useTabletFloatingTabBar: Boolean,
    useNativeNavigation: Boolean,
    useNativeTabBar: Boolean,
    liquidGlassNativeTabBarSupported: Boolean,
    liquidGlassNativeTabBarEnabled: Boolean,
    requests: AppTabRequests,
    state: AppTabState,
    actions: (isTabletLayout: Boolean) -> AppTabActions,
    onBack: () -> Unit,
    onTabSelected: (AppScreenTab) -> Unit,
    onProfileSelected: (NuvioProfile) -> Unit,
    onAddProfileRequested: () -> Unit,
) {
    PlatformBackHandler(enabled = rootRouteActive, onBack = onBack)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTabletLayout = useTabletFloatingTabBar || maxWidth >= 768.dp
        val tabActions = remember(actions, isTabletLayout) { actions(isTabletLayout) }
        val hiddenPages by ThemeSettingsRepository.hiddenNavigationPages.collectAsStateWithLifecycle()
        val useNativeBottomTabs = hiddenPages.isEmpty() && if (useNativeNavigation) {
            useNativeTabBar
        } else {
            liquidGlassNativeTabBarSupported && liquidGlassNativeTabBarEnabled && initialHomeReady
        }
        val tabsRouteActive = rootRouteActive
        val navBarScrollState = rememberNuvioNavBarScrollState()
        val navBarHazeState = rememberHazeState()
        val savedNavBarStyle by remember { ThemeSettingsRepository.navBarStyle }.collectAsStateWithLifecycle()
        val navBarStyleSetting = savedNavBarStyle
        val navBarGlowEnabled by ThemeSettingsRepository.navBarGlowEnabled.collectAsStateWithLifecycle()
        val homeLabel = stringResource(Res.string.compose_nav_home)
        val searchLabel = stringResource(Res.string.compose_nav_search)
        val libraryLabel = stringResource(Res.string.compose_nav_library)
        val animeLabel = stringResource(Res.string.compose_nav_anime)
        val sportsLabel = stringResource(Res.string.compose_nav_sports)
        val profileLabel = stringResource(Res.string.compose_nav_profile)
        // Rebuilt only when the selected tab, hidden pages, labels or the tab callback change, so
        // unrelated recompositions of this screen do not hand the dock a fresh list of new lambdas.
        val currentOnTabSelected by rememberUpdatedState(onTabSelected)
        val floatingNavigationItems = remember(
            selectedTab, hiddenPages, homeLabel, searchLabel, libraryLabel, animeLabel, sportsLabel, profileLabel,
        ) {
            val navigationOrder = listOf(
                AppScreenTab.Home, AppScreenTab.Search, AppScreenTab.Library,
                AppScreenTab.Anime, AppScreenTab.Sports, AppScreenTab.Settings,
            )
            val kinds = listOf(
                DockIconKind.Home, DockIconKind.Search, DockIconKind.Library,
                DockIconKind.Anime, DockIconKind.Sports, DockIconKind.Profile,
            )
            val labels = listOf(homeLabel, searchLabel, libraryLabel, animeLabel, sportsLabel, profileLabel)
            navigationOrder.indices
                .filter { navigationOrder[it].name !in hiddenPages }
                .map { index ->
                    val tab = navigationOrder[index]
                    FloatingNavigationItem(
                        selected = selectedTab == tab,
                        onClick = { currentOnTabSelected(tab) },
                        morph = kinds[index],
                        label = labels[index],
                    )
                }
        }

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (initialHomeReady) 1f else 0f),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(
                    LocalNuvioBottomNavigationOverlayPadding provides if (useNativeBottomTabs) 49.dp else 72.dp,
                    LocalNuvioNavBarScrollState provides navBarScrollState,
                ) {
                    AppTabHost(
                        selectedTab = selectedTab,
                        requests = requests,
                        state = state,
                        actions = tabActions,
                        modifier = Modifier
                            .fillMaxSize()
                            .hazeSource(state = navBarHazeState)
                            .then(if (navBarStyleSetting == NavBarStyle.ADAPTIVE) Modifier.nestedScroll(navBarScrollState.nestedScrollConnection) else Modifier)
                            .padding(innerPadding),
                    )
                }

                if (!useNativeBottomTabs) {
                    LaunchedEffect(navBarStyleSetting) {
                        when (navBarStyleSetting) {
                            NavBarStyle.EXPANDED -> navBarScrollState.expand()
                            NavBarStyle.COMPACT -> navBarScrollState.collapse()
                            NavBarStyle.ADAPTIVE -> navBarScrollState.expand()
                        }
                    }
                    FloatingNavigationBar(
                        modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 500.dp),
                        scrollState = navBarScrollState,
                        hazeState = navBarHazeState,
                        items = floatingNavigationItems,
                        glowEnabled = navBarGlowEnabled,
                    )
                }
            }
        }
    }
}
