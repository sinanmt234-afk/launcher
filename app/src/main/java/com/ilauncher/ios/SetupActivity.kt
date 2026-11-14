package com.ilauncher.ios

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class SetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent { SetupScreen() }
    }
}

@Composable
fun SetupScreen() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("ilauncher", 0) }
    var lockOn by remember { mutableStateOf(prefs.getBoolean("lock", false)) }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF0B0B10)).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("iLauncher setup", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Btn("1. Set iLauncher as default Home app") { safeStart(ctx, Intent(Settings.ACTION_HOME_SETTINGS)) }
        Btn("2. Allow display over other apps (for lock screen)") {
            safeStart(ctx, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
        }
        Btn("3. Allow notifications (needed by lock service)") {
            if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Btn(if (lockOn) "4. iOS lock screen: ON (tap to turn off)" else "4. Turn ON iOS lock screen") {
            lockOn = !lockOn
            prefs.edit().putBoolean("lock", lockOn).apply()
            val i = Intent(ctx, LockService::class.java)
            if (lockOn) ContextCompat.startForegroundService(ctx, i) else ctx.stopService(i)
        }
        Btn("Preview lock screen") { ctx.startActivity(Intent(ctx, LockActivity::class.java)) }
        Text(
            "Home screen: swipe left/right for pages and App Library. Swipe down = Spotlight. " +
                "Swipe down from the top-right = Control Center (torch, volume, Siri, StandBy, Health). " +
                "Long-press the Search pill = Siri. Put the phone in landscape while charging = StandBy. " +
                "Long-press an app for App Info / Remove.",
            color = Color(0xFFBBBBBB), fontSize = 14.sp
        )
    }
}

@Composable
fun Btn(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(text) }
}
