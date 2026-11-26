package com.nuvio.app.features.widgets

internal actual object HomeWidgetPublisher {
    actual fun publish(continueWatching: List<HomeWidgetItem>, upcoming: List<HomeWidgetItem>) = Unit
}
