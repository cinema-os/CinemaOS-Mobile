package com.nuvio.app.features.settings

import com.nuvio.app.core.storage.CinemaPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** CinemaOS-only switches: launch intro and the preferred source audio language. */
internal object CinemaSettingsRepository {
    private const val INTRO_KEY = "show_intro_on_launch"
    private const val AUDIO_LANG_KEY = "preferred_source_audio_language"
    const val DEFAULT_AUDIO_LANGUAGE = "en"

    private val _showIntro = MutableStateFlow(CinemaPrefs.getBoolean(INTRO_KEY, false))
    val showIntro: StateFlow<Boolean> = _showIntro.asStateFlow()

    private val _preferredAudioLanguage =
        MutableStateFlow(CinemaPrefs.getString(AUDIO_LANG_KEY)?.takeIf { it.isNotBlank() } ?: DEFAULT_AUDIO_LANGUAGE)
    val preferredAudioLanguage: StateFlow<String> = _preferredAudioLanguage.asStateFlow()

    /** Re-reads persisted values (the Android store is attached after this object may have been touched). */
    fun reload() {
        _showIntro.value = CinemaPrefs.getBoolean(INTRO_KEY, false)
        _preferredAudioLanguage.value =
            CinemaPrefs.getString(AUDIO_LANG_KEY)?.takeIf { it.isNotBlank() } ?: DEFAULT_AUDIO_LANGUAGE
    }

    fun setShowIntro(enabled: Boolean) {
        _showIntro.value = enabled
        CinemaPrefs.putBoolean(INTRO_KEY, enabled)
    }

    fun setPreferredAudioLanguage(code: String) {
        _preferredAudioLanguage.value = code
        CinemaPrefs.putString(AUDIO_LANG_KEY, code)
    }
}
