package com.nuvio.app.features.widgets

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

private const val PREFS_NAME = "cinemaos_home_widgets"
internal const val KEY_CONTINUE_WATCHING = "continue_watching"
internal const val KEY_UPCOMING = "upcoming"

internal actual object HomeWidgetPublisher {
    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    actual fun publish(continueWatching: List<HomeWidgetItem>, upcoming: List<HomeWidgetItem>) {
        val context = appContext ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val continueJson = continueWatching.toJson()
        val upcomingJson = upcoming.toJson()
        if (prefs.getString(KEY_CONTINUE_WATCHING, null) == continueJson &&
            prefs.getString(KEY_UPCOMING, null) == upcomingJson
        ) {
            return
        }
        prefs.edit()
            .putString(KEY_CONTINUE_WATCHING, continueJson)
            .putString(KEY_UPCOMING, upcomingJson)
            .apply()
        ContinueWatchingWidgetProvider.refreshAll(context)
        UpcomingWidgetProvider.refreshAll(context)
    }
}

internal fun readWidgetItems(context: Context, key: String): List<HomeWidgetItem> {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(key, null)
        ?: return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val id = obj.optString("id")
            val title = obj.optString("title")
            if (id.isBlank() || title.isBlank()) return@mapNotNull null
            HomeWidgetItem(
                title = title,
                subtitle = obj.optString("subtitle"),
                type = obj.optString("type", "series"),
                id = id,
            )
        }
    }.getOrDefault(emptyList())
}

private fun List<HomeWidgetItem>.toJson(): String {
    val array = JSONArray()
    forEach { item ->
        array.put(
            JSONObject()
                .put("title", item.title)
                .put("subtitle", item.subtitle)
                .put("type", item.type)
                .put("id", item.id),
        )
    }
    return array.toString()
}
