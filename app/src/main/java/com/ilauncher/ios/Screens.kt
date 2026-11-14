package com.ilauncher.ios

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.time.format.DateTimeFormatter

// ------------------------------------------------------------------ StandBy
@Composable
fun StandBy(onExit: () -> Unit) {
    val now by rememberNow()
    val battery by rememberBattery()
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    val red = Color(0xFFFF453A)
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onExit() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(now.format(DateTimeFormatter.ofPattern("h:mm")), color = red, fontSize = 140.sp, fontWeight = FontWeight.Light)
            Text(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")).uppercase(), color = red.copy(alpha = 0.7f), fontSize = 18.sp)
            Text("🔋 ${battery.first}%", color = red.copy(alpha = 0.7f), fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

// ------------------------------------------------------------------- Health
@Composable
fun HealthScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler(onBack = onClose)
    var steps by remember { mutableIntStateOf(-1) }
    var granted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED)
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) perm.launch(Manifest.permission.ACTIVITY_RECOGNITION) }
    DisposableEffect(granted) {
        if (!granted) onDispose { } else {
            val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val s = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            val l = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) { steps = e.values[0].toInt() }
                override fun onAccuracyChanged(s: Sensor?, a: Int) {}
            }
            if (s != null) sm.registerListener(l, s, SensorManager.SENSOR_DELAY_UI)
            onDispose { sm.unregisterListener(l) }
        }
    }
    val st = if (steps < 0) 0 else steps
    val progress = (st / 6000f).coerceIn(0f, 1f)

    Column(
        Modifier.fillMaxSize().background(Color(0xFF0B0B10)).statusBarsPadding().navigationBarsPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Summary", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("Done", color = Color(0xFF0A84FF), fontSize = 17.sp, modifier = Modifier.clickable(onClick = onClose))
        }
        Spacer(Modifier.height(28.dp))
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(180.dp)) {
                val w = 24f
                val sz = Size(size.width - w, size.height - w)
                drawArc(Color(0x33FF2D55), 0f, 360f, false, Offset(w / 2, w / 2), sz, style = Stroke(w, cap = StrokeCap.Round))
                drawArc(Color(0xFFFF2D55), -90f, 360f * progress, false, Offset(w / 2, w / 2), sz, style = Stroke(w, cap = StrokeCap.Round))
            }
            Text(if (steps < 0) "--" else "$st", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
        listOf(
            "👟 Steps (since last reboot)" to (if (steps < 0) "--" else "$st"),
            "📍 Distance (estimate)" to String.format("%.2f km", st * 0.00075),
            "🔥 Active energy (estimate)" to "${(st * 0.04).toInt()} kcal"
        ).forEach { (k, v) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1C1C1E)).padding(16.dp)
            ) {
                Text(k, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text(v, color = Color(0xFFFF2D55), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (!granted) Text("Allow Physical activity permission to see your steps.", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
    }
}
