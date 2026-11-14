package com.ilauncher.ios

import android.content.*
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.BatteryManager
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** Edit these for the weather widget. */
object Config {
    const val CITY = "Thiruvananthapuram"
    const val LAT = 8.5241
    const val LON = 76.9366
}

data class AppItem(
    val label: String, val pkg: String, val activity: String,
    val icon: ImageBitmap, val category: Int
)

fun safeStart(ctx: Context, i: Intent) {
    try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
}

fun launch(ctx: Context, a: AppItem) =
    safeStart(ctx, Intent().setClassName(a.pkg, a.activity))

fun web(ctx: Context, q: String) =
    safeStart(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))))

/** Makes Android adaptive icons look like full-square iOS icons. */
fun iconBitmap(d: Drawable, size: Int = 160): ImageBitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    if (d is AdaptiveIconDrawable) {
        val b = (size * 0.25f).toInt()
        d.background?.let { it.setBounds(-b, -b, size + b, size + b); it.draw(c) }
        d.foreground?.let { it.setBounds(-b, -b, size + b, size + b); it.draw(c) }
    } else {
        d.setBounds(0, 0, size, size); d.draw(c)
    }
    return bmp.asImageBitmap()
}

fun loadApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map {
            AppItem(
                it.loadLabel(pm).toString(), it.activityInfo.packageName, it.activityInfo.name,
                iconBitmap(it.loadIcon(pm)), it.activityInfo.applicationInfo.category
            )
        }
        .sortedBy { it.label.lowercase() }
}

object Torch {
    var on = false
    fun set(ctx: Context, value: Boolean) {
        try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            cm.setTorchMode(id, value); on = value
        } catch (_: Exception) {}
    }
}

fun readBattery(ctx: Context): Pair<Int, Boolean> {
    val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val lvl = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
    val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
    return (lvl * 100 / scale) to charging
}

@Composable
fun rememberBattery(): State<Pair<Int, Boolean>> {
    val ctx = LocalContext.current
    val s = remember { mutableStateOf(readBattery(ctx)) }
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { s.value = readBattery(ctx) }
        }
        ContextCompat.registerReceiver(ctx, r, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        onDispose { ctx.unregisterReceiver(r) }
    }
    return s
}

@Composable
fun rememberNow(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) { value = LocalDateTime.now(); delay(1000) }
}

data class Wx(val temp: Int, val hi: Int, val lo: Int, val desc: String)

suspend fun fetchWeather(): Wx? = withContext(Dispatchers.IO) {
    try {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${Config.LAT}&longitude=${Config.LON}" +
            "&current_weather=true&daily=temperature_2m_max,temperature_2m_min&timezone=auto"
        val j = JSONObject(URL(url).openStream().bufferedReader().use { it.readText() })
        val cw = j.getJSONObject("current_weather")
        val d = j.getJSONObject("daily")
        val code = cw.getInt("weathercode")
        val desc = when (code) {
            0 -> "Clear"; 1, 2, 3 -> "Mostly Cloudy"; 45, 48 -> "Fog"
            in 51..67 -> "Rain"; in 71..77 -> "Snow"; in 80..82 -> "Showers"
            in 95..99 -> "Thunderstorm"; else -> "Cloudy"
        }
        Wx(
            cw.getDouble("temperature").roundToInt(),
            d.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
            d.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(), desc
        )
    } catch (_: Exception) { null }
}

val AppItem.key: String get() = "$pkg/$activity"

fun saveOrder(ctx: Context, list: List<AppItem>) {
    ctx.getSharedPreferences("ilauncher", 0).edit()
        .putString("order", list.joinToString("\n") { it.key }).apply()
}
