package com.project.lol.ui

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import com.project.lol.BuildConfig
import com.project.lol.R
import com.project.lol.proxy.LocalProxyManager
import com.project.lol.ui.theme.SpotifyTheme
import com.project.lol.util.BuildInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val MonochromeAccent = Color(0xFFE0E0E0)

class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        super.onCreate(savedInstanceState)

        requestedOrientation = if (
            getSharedPreferences("spotilol_prefs", MODE_PRIVATE)
                .getBoolean("LandscapeMode", false)
        ) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        val analytics = FirebaseAnalytics.getInstance(this)
        FirebaseCrashlytics.getInstance()
        FirebasePerformance.getInstance()
        analytics.logEvent(FirebaseAnalytics.Event.APP_OPEN, Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, "Spotilol")
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, "SplashActivity")
        })

        setContent {
            var certInstalled by remember { mutableStateOf(false) }
            var checkDone by remember { mutableStateOf(false) }
            var checking by remember { mutableStateOf(true) }
            var exiting by remember { mutableStateOf(false) }
            var contentAlpha by remember { mutableFloatStateOf(1f) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                if (getSharedPreferences("spotilol_prefs", MODE_PRIVATE)
                        .getBoolean("OfflineMode", false)
                ) {
                    startActivity(Intent(this@SplashActivity, OfflineActivity::class.java))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                    return@LaunchedEffect
                }
                withContext(Dispatchers.IO) {
                    val useProxy = getSharedPreferences("spotilol_prefs", MODE_PRIVATE)
                        .getString("ConnectionMode", "normal") == "proxy"
                    if (useProxy) {
                        LocalProxyManager.init(this@SplashActivity)
                        LocalProxyManager.start()
                        delay(800)
                        certInstalled = LocalProxyManager.isCAInstalled()
                    } else {
                        LocalProxyManager.stop()
                        delay(600)
                        certInstalled = true
                    }
                    checkDone = true
                    checking = false
                }
            }

            LaunchedEffect(certInstalled, checkDone) {
                if (checkDone && certInstalled && !exiting) {
                    exiting = true
                    animate(
                        initialValue = 1f,
                        targetValue = 0f,
                        animationSpec = tween(500, easing = LinearEasing)
                    ) { value, _ -> contentAlpha = value }
                    val linkIntent = intent?.takeIf { it.action == Intent.ACTION_VIEW && it.data != null }
                    startActivity(
                        if (linkIntent != null) {
                            Intent(linkIntent).setClass(this@SplashActivity, MainActivity::class.java)
                        } else {
                            Intent(this@SplashActivity, MainActivity::class.java)
                        }
                    )
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                }
            }

            SpotifyTheme {
                Box(modifier = Modifier.graphicsLayer { alpha = contentAlpha }) {
                    when {
                        checking -> LoadingScreen()
                        !certInstalled -> {
                            var certAlpha by remember { mutableStateOf(0f) }
                            LaunchedEffect(Unit) {
                                animate(
                                    initialValue = 0f,
                                    targetValue = 1f,
                                    animationSpec = tween(1300, easing = LinearEasing)
                                ) { value, _ -> certAlpha = value }
                            }
                            CACertScreen(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                                    .graphicsLayer { alpha = certAlpha },
                                onSwitchNormal = {
                                    getSharedPreferences("spotilol_prefs", MODE_PRIVATE)
                                        .edit()
                                        .putString("ConnectionMode", "normal")
                                        .putBoolean("ServiceOn", false)
                                        .apply()
                                    LocalProxyManager.stop()
                                    recreate()
                                },
                                onCheck = {
                                    checking = true
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            if (!LocalProxyManager.isRunning) {
                                                LocalProxyManager.start()
                                                delay(500)
                                            }
                                            certInstalled = LocalProxyManager.isCAInstalled()
                                        }
                                        checking = false
                                    }
                                },
                                onExport = {
                                    scope.launch {
                                        val path = withContext(Dispatchers.IO) {
                                            LocalProxyManager.exportCACert(this@SplashActivity)
                                        }
                                        Toast.makeText(
                                            this@SplashActivity,
                                            this@SplashActivity.getString(R.string.splash_exported_to, path),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "title")
    val titleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "titleAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Text(
            text = stringResource(R.string.splash_title),
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer { alpha = titleAlpha },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )

        Text(
            text = stringResource(R.string.splash_version_label, BuildConfig.VERSION_NAME, BuildInfo.id),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(bottom = 28.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.35f)
        )
    }
}

@Composable
private fun CACertScreen(
    modifier: Modifier = Modifier,
    onSwitchNormal: () -> Unit,
    onCheck: () -> Unit,
    onExport: () -> Unit
) {
    Column(
        modifier = modifier
            .padding(horizontal = 32.dp)
            .systemBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.splash_cert_title),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.splash_cert_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.06f)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Step(1, stringResource(R.string.splash_cert_step_1))
                Spacer(Modifier.height(16.dp))
                Step(2, stringResource(R.string.splash_cert_step_2))
                Spacer(Modifier.height(16.dp))
                Step(3, stringResource(R.string.splash_cert_step_3))
                Spacer(Modifier.height(16.dp))
                Step(4, stringResource(R.string.splash_cert_step_4))
            }
        }

        Spacer(Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                onClick = onExport,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.08f)
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.splash_cert_export_button), color = Color.White.copy(alpha = 0.7f))
                }
            }

            Surface(
                onClick = onCheck,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.splash_cert_check_button), color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Surface(
            onClick = onSwitchNormal,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.06f)
        ) {
            Box(
                modifier = Modifier.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.splash_cert_switch_normal), color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$number",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.5f),
            lineHeight = 18.sp
        )
    }
}
