package com.nuvio.app.core.storage

import android.content.Context
import android.content.SharedPreferences

internal actual object CinemaPrefs {
    private const val preferencesName = "cinemaos_prefs"

    @Volatile
    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean =
        preferences?.getBoolean(key, default) ?: default

    actual fun putBoolean(key: String, value: Boolean) {
        preferences?.edit()?.putBoolean(key, value)?.apply()
    }

    actual fun getString(key: String): String? = preferences?.getString(key, null)

    actual fun putString(key: String, value: String) {
        preferences?.edit()?.putString(key, value)?.apply()
    }
}
