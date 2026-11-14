package com.ilauncher.ios

import android.content.*
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LauncherActivity : ComponentActivity() {
    private var homeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val prefs = getSharedPreferences("ilauncher", 0)
        if (prefs.getBoolean("lock", false) && Settings.canDrawOverlays(this)) {
            ContextCompat.startForegroundService(this, Intent(this, LockService::class.java))
        }
        setContent { LauncherRoot(homeTick) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homeTick++ // Home button pressed: close overlays
    }
}

@Composable
fun LauncherRoot(homeTick: Int) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf(emptyList<AppItem>()) }
    var reload by remember { mutableIntStateOf(0) }
    var wx by remember { mutableStateOf<Wx?>(null) }
    var spotlight by remember { mutableStateOf(false) }
    var control by remember { mutableStateOf(false) }
    var siri by remember { mutableStateOf(false) }
    var health by remember { mutableStateOf(false) }
    var manualStandBy by remember { mutableStateOf(false) }
    var suppressed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { reload++ }
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(ctx, r, f, ContextCompat.RECEIVER_EXPORTED)
        onDispose { ctx.unregisterReceiver(r) }
    }
    LaunchedEffect(reload) { apps = withContext(Dispatchers.Default) { loadApps(ctx) } }
    LaunchedEffect(Unit) { wx = fetchWeather() }
    LaunchedEffect(homeTick) { spotlight = false; control = false; siri = false; health = false }

    val battery by rememberBattery()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val auto = landscape && battery.second
    LaunchedEffect(auto) { if (!auto) suppressed = false }

    Box(Modifier.fillMaxSize()) {
        HomeScreen(
            apps, wx, homeTick,
            openSpotlight = { spotlight = true }, openControl = { control = true },
            openSiri = { siri = true }
        )
        if (spotlight) Spotlight(apps) { spotlight = false }
        if (control) ControlCenter(
            onClose = { control = false },
            onSiri = { control = false; siri = true },
            onStandBy = { control = false; manualStandBy = true },
            onHealth = { control = false; health = true }
        )
        if (siri) SiriOverlay(apps) { siri = false }
        if (health) HealthScreen { health = false }
        if (manualStandBy || (auto && !suppressed)) StandBy { manualStandBy = false; suppressed = true }
    }
}
