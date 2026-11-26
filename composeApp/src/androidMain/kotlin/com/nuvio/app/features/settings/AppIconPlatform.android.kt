package com.nuvio.app.features.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

internal actual object AppIconPlatform {
    actual val requiresCloseConfirmation: Boolean = true

    private const val launcherPackage = "com.nuvio.app.launcher"
    private val launcherComponents = AppIconOption.entries.map { option ->
        option.platformName to "$launcherPackage.${option.platformName ?: "AppIconDefault"}"
    }

    private var context: Context? = null

    actual fun loadSelectedIconKey(): String? =
        context?.getSharedPreferences("app_icon_settings", Context.MODE_PRIVATE)
            ?.getString("selected_app_icon", null)

    actual fun saveSelectedIconKey(key: String) {
        context?.getSharedPreferences("app_icon_settings", Context.MODE_PRIVATE)
            ?.edit()
            ?.putString("selected_app_icon", key)
            ?.apply()
    }

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        this.context = appContext
        ensureDefaultLauncherOnly(appContext)
    }

    actual fun currentIconName(): String? {
        val appContext = context ?: return null
        return currentIconName(appContext)
    }

    fun currentLauncherIconResource(context: Context): Int {
        val option = AppIconOption.fromPlatformName(currentIconName(context))
        val resourceName = if (option == AppIconOption.ORIGINAL) {
            "ic_launcher"
        } else {
            "ic_launcher_${option.key}"
        }
        return context.resources
            .getIdentifier(resourceName, "mipmap", context.packageName)
            .takeIf { it != 0 }
            ?: com.nuvio.app.R.mipmap.ic_launcher
    }

    fun currentLauncherComponent(context: Context): ComponentName {
        val currentName = currentIconName(context)
        val className = launcherComponents.first { it.first == currentName }.second
        return component(context, className)
    }

    /** Icon variants are not switched at runtime: the default launcher alias is always the one in use. */
    private fun currentIconName(context: Context): String? = null

    /**
     * Puts every launcher alias back to its manifest state (default alias enabled, the variants disabled). An alias
     * switched off at runtime by an older version would otherwise leave the app without a launcher entry, and the
     * state survives in-app updates. Runs on every start.
     */
    private fun ensureDefaultLauncherOnly(context: Context) {
        val packageManager = context.packageManager
        runCatching {
            launcherComponents.forEach { (_, className) ->
                val component = component(context, className)
                if (packageManager.getComponentEnabledSetting(component) != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
                    packageManager.setComponentEnabledSetting(
                        component,
                        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                        PackageManager.DONT_KILL_APP,
                    )
                }
            }
        }
    }

    /** Icon switching is a no-op (all variants share the CinemaOS icon); the default launcher stays enabled. */
    actual suspend fun activateIcon(name: String?): Boolean {
        val appContext = context ?: return false
        ensureDefaultLauncherOnly(appContext)
        return true
    }

    private fun component(context: Context, className: String): ComponentName =
        ComponentName(context.packageName, className)
}
