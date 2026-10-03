package com.project.lol.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.project.lol.R
import com.project.lol.ui.MainActivity
import com.project.lol.ui.OfflineActivity

private val SURFACE = Color(0xFF1D1D20)
private val SCRIM = Color(0xCC1D1D20)
private val BUTTON_SURFACE = Color(0x24FFFFFF)
private val TITLE_COLOR = Color(0xFFF2F2F4)
private val SUBTITLE_COLOR = Color(0xFFAEAEB4)
private val PROGRESS_TRACK = Color(0x33FFFFFF)
private const val RADIUS = 18
private const val ICON_TINT = 0xFFE6E6EA.toInt()
private const val ICON_DISABLED_TINT = 0xFF6A6A70.toInt()
private const val PLAY_ICON_TINT = 0xFF101010.toInt()

class SpotilolPlayerWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(180.dp, 48.dp),
            DpSize(260.dp, 110.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            PlayerContent()
        }
    }
}

class SpotilolPlayerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SpotilolPlayerWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetActions.requestRefresh(context)
    }
}

@Composable
private fun PlayerContent() {
    val prefs = currentState<Preferences>()
    val state = WidgetState.from(prefs)
    val version = WidgetState.coverVersionOf(prefs)
    val size = LocalSize.current
    val context = LocalContext.current

    if (!state.hasTrack) {
        EmptyContent(state, context)
        return
    }

    if (size.height < 60.dp) {
        MiniContent(state, version, context)
    } else {
        FullContent(state, version, context)
    }
}

private fun tint(color: Int): ColorFilter = ColorFilter.tint(ColorProvider(Color(color)))

private fun openAction(offline: Boolean): Action =
    if (offline) actionStartActivity(OfflineActivity::class.java)
    else actionStartActivity(MainActivity::class.java)

private fun frame(cover: Bitmap?): GlanceModifier {
    val base = GlanceModifier.fillMaxSize()
    val filled = if (cover != null) {
        base.background(ImageProvider(cover), ContentScale.Crop)
    } else {
        base.background(ColorProvider(SURFACE))
    }
    return filled.cornerRadius(RADIUS.dp)
}

private fun scrim(): GlanceModifier = GlanceModifier
    .fillMaxSize()
    .background(ColorProvider(SCRIM))
    .cornerRadius(RADIUS.dp)

@Composable
private fun EmptyContent(state: WidgetState, context: Context) {
    Box(modifier = frame(null)) {
        Row(
            modifier = scrim().clickable(openAction(state.offline)).padding(10.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_notification),
                contentDescription = null,
                modifier = GlanceModifier.size(26.dp)
            )
            Spacer(GlanceModifier.width(12.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = context.getString(R.string.widget_empty_title),
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(TITLE_COLOR), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                )
                Text(
                    text = context.getString(R.string.widget_empty_subtitle),
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(SUBTITLE_COLOR), fontSize = 12.sp)
                )
            }
        }
    }
}

@Composable
private fun FullContent(state: WidgetState, version: Long, context: Context) {
    val cover = CoverCache.load(context, version)
    Box(modifier = frame(cover)) {
        Column(modifier = scrim().padding(10.dp)) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Row(
                    modifier = GlanceModifier.defaultWeight().clickable(openAction(state.offline)),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Cover(cover, 46.dp, 10.dp)
                    Spacer(GlanceModifier.width(11.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = state.title,
                            maxLines = 1,
                            style = TextStyle(color = ColorProvider(TITLE_COLOR), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = state.artist,
                            maxLines = 1,
                            style = TextStyle(color = ColorProvider(SUBTITLE_COLOR), fontSize = 12.sp)
                        )
                        Spacer(GlanceModifier.height(7.dp))
                        ProgressBar(state)
                    }
                }
                if (state.supportsExtras) {
                    Spacer(GlanceModifier.width(8.dp))
                    CircleIconButton(
                        resId = state.favoriteIcon,
                        contentDescription = context.getString(if (state.favorite) R.string.notif_action_unlike else R.string.notif_action_like),
                        iconTint = if (state.favorite) state.accent else ICON_TINT,
                        dimension = 26.dp,
                        iconSize = 14.dp,
                        action = actionRunCallback<FavoriteAction>()
                    )
                }
            }
            Spacer(GlanceModifier.height(6.dp))
            Controls(state, context)
        }
    }
}

@Composable
private fun MiniContent(state: WidgetState, version: Long, context: Context) {
    val cover = CoverCache.load(context, version)
    Box(modifier = frame(cover)) {
        Row(
            modifier = scrim().clickable(openAction(state.offline)).padding(8.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Cover(cover, 32.dp, 9.dp)
            Spacer(GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = state.title,
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(TITLE_COLOR), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                )
                Text(
                    text = state.artist,
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(SUBTITLE_COLOR), fontSize = 11.sp)
                )
            }
            Spacer(GlanceModifier.width(8.dp))
            CircleIconButton(
                resId = state.playPauseIcon,
                contentDescription = context.getString(if (state.playing) R.string.notif_action_pause else R.string.notif_action_play),
                iconTint = PLAY_ICON_TINT,
                background = Color(state.accent),
                dimension = 30.dp,
                iconSize = 15.dp,
                action = actionRunCallback<PlayPauseAction>()
            )
        }
    }
}

@Composable
private fun Cover(bitmap: Bitmap?, dimension: Dp, radius: Dp) {
    if (bitmap != null) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = null,
            modifier = GlanceModifier.size(dimension).cornerRadius(radius),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = GlanceModifier
                .size(dimension)
                .background(ColorProvider(Color(0x33FFFFFF)))
                .cornerRadius(radius),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_notification),
                contentDescription = null,
                modifier = GlanceModifier.size(dimension / 2)
            )
        }
    }
}

@Composable
private fun ProgressBar(state: WidgetState) {
    if (state.duration <= 0L) return
    LinearProgressIndicator(
        progress = state.progress,
        modifier = GlanceModifier.fillMaxWidth().height(3.dp).cornerRadius(2.dp),
        color = ColorProvider(Color(state.accent)),
        backgroundColor = ColorProvider(PROGRESS_TRACK)
    )
}

@Composable
private fun Controls(state: WidgetState, context: Context) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Spacer(GlanceModifier.defaultWeight())
        if (state.supportsExtras) {
            CircleIconButton(
                resId = state.shuffleIcon,
                contentDescription = context.getString(R.string.widget_action_shuffle),
                iconTint = if (state.shuffleOn) state.accent else ICON_TINT,
                dimension = 28.dp,
                iconSize = 15.dp,
                enabled = state.shuffleAvailable,
                action = actionRunCallback<ShuffleAction>()
            )
            Spacer(GlanceModifier.defaultWeight())
        }
        CircleIconButton(
            resId = R.drawable.ic_skip_prev,
            contentDescription = context.getString(R.string.notif_action_previous),
            iconTint = ICON_TINT,
            dimension = 28.dp,
            iconSize = 15.dp,
            action = actionRunCallback<PreviousAction>()
        )
        Spacer(GlanceModifier.defaultWeight())
        CircleIconButton(
            resId = state.playPauseIcon,
            contentDescription = context.getString(if (state.playing) R.string.notif_action_pause else R.string.notif_action_play),
            iconTint = PLAY_ICON_TINT,
            background = Color(state.accent),
            dimension = 36.dp,
            iconSize = 17.dp,
            action = actionRunCallback<PlayPauseAction>()
        )
        Spacer(GlanceModifier.defaultWeight())
        CircleIconButton(
            resId = R.drawable.ic_skip_next,
            contentDescription = context.getString(R.string.notif_action_next),
            iconTint = ICON_TINT,
            dimension = 28.dp,
            iconSize = 15.dp,
            action = actionRunCallback<NextAction>()
        )
        if (state.supportsExtras) {
            Spacer(GlanceModifier.defaultWeight())
            CircleIconButton(
                resId = state.repeatIcon,
                contentDescription = context.getString(R.string.widget_action_repeat),
                iconTint = if (state.repeatOn) state.accent else ICON_TINT,
                dimension = 28.dp,
                iconSize = 15.dp,
                action = actionRunCallback<RepeatAction>()
            )
        }
        Spacer(GlanceModifier.defaultWeight())
    }
}

@Composable
private fun CircleIconButton(
    resId: Int,
    contentDescription: String,
    iconTint: Int,
    dimension: Dp,
    iconSize: Dp,
    action: Action,
    background: Color = BUTTON_SURFACE,
    enabled: Boolean = true
) {
    val base = GlanceModifier
        .size(dimension)
        .background(ColorProvider(background))
        .cornerRadius(dimension / 2)
    val modifier = if (enabled) base.clickable(action) else base
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(resId),
            contentDescription = contentDescription,
            modifier = GlanceModifier.size(iconSize),
            colorFilter = tint(if (enabled) iconTint else ICON_DISABLED_TINT)
        )
    }
}
