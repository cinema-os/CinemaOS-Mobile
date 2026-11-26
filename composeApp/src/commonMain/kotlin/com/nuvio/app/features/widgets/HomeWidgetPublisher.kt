package com.nuvio.app.features.widgets

import com.nuvio.app.features.watchprogress.ContinueWatchingItem

/** One row shown by a home-screen widget. [type] is "movie" or "series"; [id] is the meta id. */
data class HomeWidgetItem(
    val title: String,
    val subtitle: String,
    val type: String,
    val id: String,
)

/**
 * Hands the Continue Watching and Upcoming rows to the platform's home-screen widgets.
 * Android writes a snapshot the widgets read; other platforms ignore it.
 */
internal expect object HomeWidgetPublisher {
    fun publish(continueWatching: List<HomeWidgetItem>, upcoming: List<HomeWidgetItem>)
}

internal fun List<ContinueWatchingItem>.toHomeWidgetItems(limit: Int = 8): List<HomeWidgetItem> =
    asSequence()
        .filter { it.parentMetaId.isNotBlank() && it.title.isNotBlank() }
        .map { item ->
            HomeWidgetItem(
                title = item.title,
                subtitle = item.subtitle,
                type = if (item.parentMetaType.trim().lowercase() == "movie") "movie" else "series",
                id = item.parentMetaId,
            )
        }
        .distinctBy { it.id }
        .take(limit)
        .toList()
