package com.project.lol.ui

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.project.lol.R
import com.project.lol.profile.ProfileManager
import com.project.lol.service.OfflineMediaService
import com.project.lol.ui.screens.OfflineScreen
import com.project.lol.ui.theme.SpotifyTheme

class OfflineActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences

    private val materialYouState = mutableStateOf(false)
    private val amoledState = mutableStateOf(false)
    private val hideTopBarState = mutableStateOf(false)
    private val landscapeState = mutableStateOf(false)
    private val keepScreenOnState = mutableStateOf(false)
    private val paletteSeedState = mutableStateOf<String?>(null)
    private val pipActiveState = mutableStateOf(false)

    private var pipPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("spotilol_prefs", MODE_PRIVATE)

        materialYouState.value = prefs.getBoolean("MaterialYou", false)
        amoledState.value = prefs.getBoolean("AmoledTheme", false)
        hideTopBarState.value = prefs.getBoolean("HideTopBar", false)
        landscapeState.value = prefs.getBoolean("LandscapeMode", false)
        keepScreenOnState.value = prefs.getBoolean("KeepScreenOn", false)
        paletteSeedState.value = prefs.getString("PaletteSeed", null)

        applyOrientation()
        applyKeepScreenOn()

        setContent {
            SpotifyTheme(
                useDynamicColor = materialYouState.value,
                amoled = amoledState.value,
                seedColor = paletteSeedState.value?.let { hex ->
                    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
                }
            ) {
                OfflineScreen(
                    prefs = prefs,
                    materialYou = materialYouState.value,
                    onMaterialYouChange = { enabled ->
                        materialYouState.value = enabled
                        prefs.edit().putBoolean("MaterialYou", enabled).apply()
                    },
                    amoledTheme = amoledState.value,
                    onAmoledThemeChange = { enabled ->
                        amoledState.value = enabled
                        prefs.edit().putBoolean("AmoledTheme", enabled).apply()
                    },
                    hideTopBar = hideTopBarState.value,
                    onHideTopBarChange = { enabled ->
                        hideTopBarState.value = enabled
                        prefs.edit().putBoolean("HideTopBar", enabled).apply()
                    },
                    landscapeMode = landscapeState.value,
                    onLandscapeModeChange = { enabled ->
                        landscapeState.value = enabled
                        prefs.edit().putBoolean("LandscapeMode", enabled).apply()
                        applyOrientation()
                    },
                    keepScreenOn = keepScreenOnState.value,
                    onKeepScreenOnChange = { enabled ->
                        keepScreenOnState.value = enabled
                        prefs.edit().putBoolean("KeepScreenOn", enabled).apply()
                        applyKeepScreenOn()
                    },
                    paletteSeed = paletteSeedState.value,
                    onPaletteSeedChange = { hex ->
                        paletteSeedState.value = hex
                        if (hex.isNullOrBlank()) {
                            prefs.edit().remove("PaletteSeed").apply()
                        } else {
                            prefs.edit().putString("PaletteSeed", hex).apply()
                        }
                    },
                    onConnectionModeChange = { mode ->
                        prefs.edit().putString("ConnectionMode", mode).apply()
                        restartToSplash()
                    },
                    onOfflineModeChange = { enabled ->
                        prefs.edit().putBoolean("OfflineMode", enabled).apply()
                        restartToSplash()
                    },
                    onSaveProfile = { name, cookies ->
                        val saved = runCatching { ProfileManager.saveProfile(this, name, cookies) }.isSuccess
                        Toast.makeText(this, if (saved) getString(R.string.offline_act_toast_account_saved)
                            else "Could not save encrypted profile", Toast.LENGTH_SHORT).show()
                    },
                    onLoadProfile = { cookies ->
                        if (!ProfileManager.applyProfile(this, cookies)) {
                            Toast.makeText(this, getString(R.string.offline_act_toast_profile_load_failed), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, getString(R.string.offline_act_toast_profile_loaded), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDeleteProfile = { name ->
                        val deleted = runCatching { ProfileManager.deleteProfile(this, name) }.isSuccess
                        Toast.makeText(this, if (deleted) getString(R.string.offline_act_toast_profile_deleted)
                            else "Could not update encrypted profiles", Toast.LENGTH_SHORT).show()
                    },
                    onClearCache = { clearWebViewCache() },
                    onClearData = { clearAllData() },
                    pipActive = pipActiveState.value,
                    onEnterPip = { enterPipMode() },
                    onPlaybackStateChange = { playing ->
                        pipPlaying = playing
                        updatePipParams()
                    },
                    onExit = { exitOfflineMode() }
                )
            }
        }
    }

    private fun applyOrientation() {
        requestedOrientation = if (landscapeState.value) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    private fun applyKeepScreenOn() {
        if (keepScreenOnState.value) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipActiveState.value = isInPictureInPictureMode
        updatePipParams()
    }

    private fun enterPipMode() {
        if (isInPictureInPictureMode) return
        runCatching { enterPictureInPictureMode(buildPipParams()) }
    }

    private fun updatePipParams() {
        if (!isInPictureInPictureMode) return
        runCatching { setPictureInPictureParams(buildPipParams()) }
    }

    private fun buildPipParams(): PictureInPictureParams =
        PictureInPictureParams.Builder()
            .setAspectRatio(Rational(1, 1))
            .setActions(buildPipActions())
            .build()

    private fun buildPipActions(): List<RemoteAction> {
        val previous = RemoteAction(
            Icon.createWithResource(this, R.drawable.ic_skip_prev),
            getString(R.string.offline_desc_previous),
            getString(R.string.offline_desc_previous),
            pipActionIntent(OfflineMediaService.ACTION_PREV)
        )
        val playPause = RemoteAction(
            Icon.createWithResource(this, if (pipPlaying) R.drawable.ic_pause else R.drawable.ic_play),
            getString(if (pipPlaying) R.string.offline_desc_pause else R.string.offline_desc_play),
            getString(if (pipPlaying) R.string.offline_desc_pause else R.string.offline_desc_play),
            pipActionIntent(OfflineMediaService.ACTION_PLAY_PAUSE)
        )
        val next = RemoteAction(
            Icon.createWithResource(this, R.drawable.ic_skip_next),
            getString(R.string.offline_desc_next),
            getString(R.string.offline_desc_next),
            pipActionIntent(OfflineMediaService.ACTION_NEXT)
        )
        return listOf(previous, playPause, next)
    }

    private fun pipActionIntent(action: String): PendingIntent {
        val intent = Intent(action).setPackage(packageName)
        return PendingIntent.getBroadcast(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun clearWebViewCache() {
        val wv = WebView(applicationContext)
        wv.clearCache(true)
        wv.clearHistory()
        wv.destroy()
        Toast.makeText(this, getString(R.string.offline_act_toast_cache_cleared), Toast.LENGTH_SHORT).show()
    }

    private fun clearAllData() {
        val wv = WebView(applicationContext)
        wv.clearCache(true)
        wv.clearHistory()
        wv.clearFormData()
        wv.destroy()
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        prefs.edit().putBoolean("LoggedIn", false).apply()
        Toast.makeText(this, getString(R.string.offline_act_toast_all_data_cleared), Toast.LENGTH_SHORT).show()
    }

    private fun restartToSplash() {
        startActivity(
            Intent(this, SplashActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    private fun exitOfflineMode() {
        prefs.edit().putBoolean("OfflineMode", false).apply()
        restartToSplash()
    }
}
