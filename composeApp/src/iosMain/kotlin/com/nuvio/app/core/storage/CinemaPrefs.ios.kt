package com.nuvio.app.core.storage

import platform.Foundation.NSUserDefaults

internal actual object CinemaPrefs {
    private const val prefix = "cinemaos_prefs."

    actual fun getBoolean(key: String, default: Boolean): Boolean {
        val defaults = NSUserDefaults.standardUserDefaults
        return if (defaults.objectForKey(prefix + key) != null) defaults.boolForKey(prefix + key) else default
    }

    actual fun putBoolean(key: String, value: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(value, forKey = prefix + key)
    }

    actual fun getString(key: String): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(prefix + key)

    actual fun putString(key: String, value: String) {
        NSUserDefaults.standardUserDefaults.setObject(value, forKey = prefix + key)
    }
}
