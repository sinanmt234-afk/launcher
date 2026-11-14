package com.ilauncher.ios

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.time.format.DateTimeFormatter

class LockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.navigationBars())
        }
        setContent { LockScreen(onUnlock = { unlock() }) }
    }

    private fun unlock() {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() { finish() }
            })
        } else finish()
    }
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val ctx = LocalContext.current
    val now by rememberNow()
    var torch by remember { mutableStateOf(Torch.on) }
    var total by remember { mutableFloatStateOf(0f) }
    BackHandler { }
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFC9BEAA), Color(0xFF8E8472), Color(0xFF3F3A30))))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -220f) onUnlock() },
                    onDragCancel = {},
                    onVerticalDrag = { _, dy -> total += dy }
                )
            }
    ) {
        Column(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(now.format(DateTimeFormatter.ofPattern("EEE d MMMM")), color = Color.White.copy(alpha = 0.9f), fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Text(now.format(DateTimeFormatter.ofPattern("h:mm")), color = Color.White.copy(alpha = 0.85f), fontSize = 118.sp, fontWeight = FontWeight.Thin)
        }
        Text("Swipe up to unlock", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp))
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().fillMaxWidth().padding(start = 48.dp, end = 48.dp, bottom = 56.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(if (torch) Color.White else Color(0x66000000))
                    .clickable { torch = !torch; Torch.set(ctx, torch) },
                contentAlignment = Alignment.Center
            ) { Text("🔦", fontSize = 22.sp) }
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(Color(0x66000000)).clickable {
                    try { ctx.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    catch (_: Exception) { safeStart(ctx, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) }
                },
                contentAlignment = Alignment.Center
            ) { Text("📷", fontSize = 22.sp) }
        }
    }
}

/** Keeps listening for screen-off and shows the iOS lock screen on top of the keyguard. */
class LockService : Service() {
    private val rx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_SCREEN_OFF) {
                c.startActivity(
                    Intent(c, LockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("lock", "iOS lock screen", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, "lock")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("iLauncher lock screen is active")
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
        ContextCompat.registerReceiver(this, rx, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { unregisterReceiver(rx); super.onDestroy() }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED &&
            c.getSharedPreferences("ilauncher", 0).getBoolean("lock", false)
        ) {
            try { ContextCompat.startForegroundService(c, Intent(c, LockService::class.java)) } catch (_: Exception) {}
        }
    }
}
