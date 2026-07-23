package com.xthan.xnotes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews

class DashboardWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidgetView(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateWidgetView(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_dashboard)

            // Intent to trigger the overlay creation dialog over the home screen
            val createIntent = Intent(context, WidgetActionActivity::class.java).apply {
                action = "ACTION_CREATE"
                putExtra("ACTION_TYPE", "CREATE_NOTE")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingCreate = PendingIntent.getActivity(
                context, 0, createIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_create, pendingCreate)

            // For export/import/delete buttons, falling back to main activity to handle full storage/dialog trees safely
            val intentMainActivity = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntentMain = PendingIntent.getActivity(
                context, 1, intentMainActivity, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_export, pendingIntentMain)
            views.setOnClickPendingIntent(R.id.widget_btn_import, pendingIntentMain)
            views.setOnClickPendingIntent(R.id.widget_btn_delete, pendingIntentMain)

            // Note: Populating dynamic list rows inside RemoteViews is handled via RemoteViewsService.
            // For now, updating the static structure layout bindings.
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun refreshAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, DashboardWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (appWidgetId in appWidgetIds) {
                updateWidgetView(context, appWidgetManager, appWidgetId)
            }
        }
    }
}