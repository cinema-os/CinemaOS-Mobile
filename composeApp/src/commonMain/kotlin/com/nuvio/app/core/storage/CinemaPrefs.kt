package com.nuvio.app.core.storage

/** Tiny device-wide key-value store for CinemaOS-specific switches (not profile scoped). */
internal expect object CinemaPrefs {
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}
