package com.nuvio.app.features.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.nuvio.app.R
import com.nuvio.app.core.deeplink.buildMetaDeepLinkUrl

private val RowIds = intArrayOf(R.id.widget_row0, R.id.widget_row1, R.id.widget_row2, R.id.widget_row3)
private val RowTitleIds = intArrayOf(R.id.widget_row0_title, R.id.widget_row1_title, R.id.widget_row2_title, R.id.widget_row3_title)
private val RowSubtitleIds = intArrayOf(
    R.id.widget_row0_subtitle,
    R.id.widget_row1_subtitle,
    R.id.widget_row2_subtitle,
    R.id.widget_row3_subtitle,
)

/** Shared rendering for the Continue Watching and Upcoming widgets: a header and up to four tappable titles. */
abstract class TitleListWidgetProvider : AppWidgetProvider() {
    protected abstract val prefsKey: String
    protected abstract val headerRes: Int
    protected abstract val emptyRes: Int

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, buildViews(context)) }
    }

    private fun buildViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_title_list)
        views.setTextViewText(R.id.widget_header, context.getString(headerRes))
        val items = readWidgetItems(context, prefsKey).take(RowIds.size)
        if (items.isEmpty()) {
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            views.setTextViewText(R.id.widget_empty, context.getString(emptyRes))
            // An empty widget still opens the app.
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context, null, 0))
        } else {
            views.setViewVisibility(R.id.widget_empty, View.GONE)
        }
        RowIds.indices.forEach { index ->
            val item = items.getOrNull(index)
            if (item == null) {
                views.setViewVisibility(RowIds[index], View.GONE)
                return@forEach
            }
            views.setViewVisibility(RowIds[index], View.VISIBLE)
            views.setTextViewText(RowTitleIds[index], item.title)
            views.setTextViewText(RowSubtitleIds[index], item.subtitle)
            views.setViewVisibility(
                RowSubtitleIds[index],
                if (item.subtitle.isBlank()) View.GONE else View.VISIBLE,
            )
            views.setOnClickPendingIntent(RowIds[index], openAppIntent(context, item, index + 1))
        }
        return views
    }

    private fun openAppIntent(context: Context, item: HomeWidgetItem?, requestCode: Int): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            val launchComponent = launch?.component
            if (launchComponent != null) component = launchComponent else setPackage(context.packageName)
            if (item != null) data = Uri.parse(buildMetaDeepLinkUrl(item.type, item.id))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        // Distinct request codes per widget class and row keep each PendingIntent separate.
        val code = (javaClass.name.hashCode() and 0xFFFF) * 16 + requestCode
        return PendingIntent.getActivity(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

private fun refreshWidgets(context: Context, provider: Class<out TitleListWidgetProvider>) {
    val manager = AppWidgetManager.getInstance(context)
    val ids = manager.getAppWidgetIds(ComponentName(context, provider))
    if (ids.isEmpty()) return
    context.sendBroadcast(
        Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
            component = ComponentName(context, provider)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        },
    )
}

class ContinueWatchingWidgetProvider : TitleListWidgetProvider() {
    override val prefsKey = KEY_CONTINUE_WATCHING
    override val headerRes = R.string.widget_continue_watching_header
    override val emptyRes = R.string.widget_continue_watching_empty

    companion object {
        fun refreshAll(context: Context) = refreshWidgets(context, ContinueWatchingWidgetProvider::class.java)
    }
}

class UpcomingWidgetProvider : TitleListWidgetProvider() {
    override val prefsKey = KEY_UPCOMING
    override val headerRes = R.string.widget_upcoming_header
    override val emptyRes = R.string.widget_upcoming_empty

    companion object {
        fun refreshAll(context: Context) = refreshWidgets(context, UpcomingWidgetProvider::class.java)
    }
}
