package com.project.lol.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.project.lol.service.MediaNotificationService
import com.project.lol.service.OfflineMediaService

internal enum class WidgetCommand { PLAY_PAUSE, NEXT, PREV, SHUFFLE, REPEAT, FAVORITE }

internal object WidgetSource {

    private const val PREFS = "spotilol_widget_prefs"
    private const val KEY = "active_source"

    fun set(context: Context, source: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, source).apply()
    }

    fun get(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, WidgetState.SOURCE_WEB) ?: WidgetState.SOURCE_WEB
}

internal object WidgetActions {

    fun send(context: Context, command: WidgetCommand) {
        val action = if (WidgetSource.get(context) == WidgetState.SOURCE_OFFLINE) {
            offlineAction(command)
        } else {
            webAction(command)
        } ?: return
        context.sendBroadcast(Intent(action).setPackage(context.packageName))
    }

    fun requestRefresh(context: Context) {
        val action = if (WidgetSource.get(context) == WidgetState.SOURCE_OFFLINE) {
            OfflineMediaService.ACTION_WIDGET_REFRESH
        } else {
            MediaNotificationService.ACTION_WIDGET_REFRESH
        }
        context.sendBroadcast(Intent(action).setPackage(context.packageName))
    }

    private fun webAction(command: WidgetCommand): String? = when (command) {
        WidgetCommand.PLAY_PAUSE -> MediaNotificationService.ACTION_PLAY_PAUSE
        WidgetCommand.NEXT -> MediaNotificationService.ACTION_NEXT
        WidgetCommand.PREV -> MediaNotificationService.ACTION_PREV
        WidgetCommand.SHUFFLE -> MediaNotificationService.ACTION_SHUFFLE
        WidgetCommand.REPEAT -> MediaNotificationService.ACTION_REPEAT
        WidgetCommand.FAVORITE -> MediaNotificationService.ACTION_FAVORITE
    }

    private fun offlineAction(command: WidgetCommand): String? = when (command) {
        WidgetCommand.PLAY_PAUSE -> OfflineMediaService.ACTION_PLAY_PAUSE
        WidgetCommand.NEXT -> OfflineMediaService.ACTION_NEXT
        WidgetCommand.PREV -> OfflineMediaService.ACTION_PREV
        WidgetCommand.SHUFFLE, WidgetCommand.REPEAT, WidgetCommand.FAVORITE -> null
    }
}

class PlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetActions.send(context, WidgetCommand.PLAY_PAUSE)
    }
}

class NextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetActions.send(context, WidgetCommand.NEXT)
    }
}

class PreviousAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetActions.send(context, WidgetCommand.PREV)
    }
}

class ShuffleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetActions.send(context, WidgetCommand.SHUFFLE)
    }
}

class RepeatAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetActions.send(context, WidgetCommand.REPEAT)
    }
}

class FavoriteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetActions.send(context, WidgetCommand.FAVORITE)
    }
}
