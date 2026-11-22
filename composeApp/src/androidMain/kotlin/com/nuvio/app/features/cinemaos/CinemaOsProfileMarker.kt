package com.nuvio.app.features.cinemaos

import org.json.JSONObject

/**
 * Client-side profile tag for rows pushed to cinemaos.tech (/api/watch-history, /api/watchlist).
 *
 * The API strips unknown keys and stores fixed columns only, so the marker rides in `description`
 * (the only free-text column stored and returned for both tables):
 *
 *     description = {"profile_id":"2","profile_name":"Kids"} + "\n" + <original description>
 *
 * `profile_id` / `profile_name` are also sent as top-level item fields so the website can adopt them
 * as real columns later (dropped by the server today). Same format as the CinemaOS TV app.
 * No marker, or a first line that is not a JSON object with "profile_id", means profile "1".
 */
internal object CinemaOsProfileMarker {
    const val FIELD_PROFILE_ID = "profile_id"
    const val FIELD_PROFILE_NAME = "profile_name"
    const val PRIMARY_PROFILE_ID = "1"

    data class Parsed(val profileId: String, val profileName: String?, val description: String)

    fun mark(profileId: String, profileName: String, description: String): String {
        val marker = JSONObject()
            .put(FIELD_PROFILE_ID, profileId)
            .put(FIELD_PROFILE_NAME, profileName)
            .toString()
        return marker + "\n" + description
    }

    fun parse(raw: String?): Parsed {
        val text = raw.orEmpty()
        val firstLine = text.substringBefore('\n')
        if (firstLine.trimStart().startsWith("{")) {
            val obj = runCatching { JSONObject(firstLine) }.getOrNull()
            val id = obj?.opt(FIELD_PROFILE_ID)?.toString()?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
            if (id != null) {
                return Parsed(
                    profileId = id,
                    profileName = obj.optString(FIELD_PROFILE_NAME).takeIf { it.isNotBlank() },
                    description = text.substringAfter('\n', ""),
                )
            }
        }
        return Parsed(profileId = PRIMARY_PROFILE_ID, profileName = null, description = text)
    }
}
