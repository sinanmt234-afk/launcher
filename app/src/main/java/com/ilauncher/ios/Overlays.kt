package com.ilauncher.ios

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ---------------------------------------------------------------- Spotlight
@Composable
fun Spotlight(apps: List<AppItem>, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    BackHandler(onBack = onClose)
    Column(
        Modifier.fillMaxSize().background(Color(0xCC000000)).statusBarsPadding().imePadding()
            .pointerInput(Unit) { detectTapGestures { } }.padding(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x33FFFFFF)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🔍  ", color = Color.White)
            BasicTextField(
                value = q, onValueChange = { q = it },
                modifier = Modifier.weight(1f).focusRequester(fr),
                textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
                cursorBrush = SolidColor(Color.White), singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (q.isNotBlank()) { web(ctx, q); onClose() } })
            )
            Text("Cancel", color = Color(0xFF0A84FF), modifier = Modifier.clickable(onClick = onClose))
        }
        Spacer(Modifier.height(12.dp))
        val results = if (q.isBlank()) apps.take(8) else apps.filter { it.label.contains(q, true) }
        LazyColumn {
            items(results) { a ->
                Row(
                    Modifier.fillMaxWidth().clickable { launch(ctx, a); onClose() }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(a.icon, a.label, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(12.dp))
                    Text(a.label, color = Color.White, fontSize = 16.sp)
                }
            }
            if (q.isNotBlank()) item {
                Text("🌐  Search web for \"$q\"", color = Color.White, fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth().clickable { web(ctx, q); onClose() }.padding(vertical = 12.dp))
            }
        }
    }
}

// ----------------------------------------------------------- Control Center
@Composable
fun ControlCenter(onClose: () -> Unit, onSiri: () -> Unit, onStandBy: () -> Unit, onHealth: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler(onBack = onClose)
    var torch by remember { mutableStateOf(Torch.on) }
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
    var vol by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }

    data class Tile(val icon: String, val name: String, val active: Boolean = false, val tap: () -> Unit)
    val tiles = listOf(
        Tile("📶", "Wi-Fi") { safeStart(ctx, Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) },
        Tile("🔵", "Bluetooth") { safeStart(ctx, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
        Tile("✈️", "Airplane") { safeStart(ctx, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)) },
        Tile("🔦", "Torch", torch) { torch = !torch; Torch.set(ctx, torch) },
        Tile("🌙", "Focus") { safeStart(ctx, Intent("android.settings.ZEN_MODE_SETTINGS")) },
        Tile("🎙️", "Siri") { onSiri() },
        Tile("🕘", "StandBy") { onStandBy() },
        Tile("❤️", "Health") { onHealth() },
    )

    Box(
        Modifier.fillMaxSize().background(Color(0xB3000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }
    ) {
        Column(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp).width(320.dp)
                .clip(RoundedCornerShape(28.dp)).background(Color(0x66FFFFFF))
                .pointerInput(Unit) { detectTapGestures { } }.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            tiles.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { t ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(58.dp).clip(RoundedCornerShape(29.dp))
                                    .background(if (t.active) Color.White else Color(0x66000000))
                                    .clickable { t.tap() },
                                contentAlignment = Alignment.Center
                            ) { Text(t.icon, fontSize = 24.sp) }
                            Text(t.name, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            Text("🔊 Volume", color = Color.White, fontSize = 12.sp)
            Slider(value = vol, valueRange = 0f..maxVol, onValueChange = {
                vol = it; am.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0)
            })
        }
    }
}

// --------------------------------------------------------------------- Siri
fun siriRespond(ctx: Context, text: String, apps: List<AppItem>): String {
    val t = text.trim().lowercase()
    return when {
        t.startsWith("open ") -> {
            val q = t.removePrefix("open ").trim()
            val a = apps.firstOrNull { it.label.lowercase().contains(q) }
            if (a != null) { launch(ctx, a); "Opening ${a.label}" } else "I couldn't find $q"
        }
        t.startsWith("call ") -> {
            safeStart(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(t.removePrefix("call ").trim()))))
            "Opening the dialer"
        }
        "flashlight" in t || "torch" in t -> {
            val on = !t.contains(" off"); Torch.set(ctx, on); if (on) "Flashlight on" else "Flashlight off"
        }
        "time" in t -> "It's " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("h:mm a"))
        "date" in t || "day is it" in t -> "Today is " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy"))
        t.startsWith("play ") -> {
            safeStart(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=" + Uri.encode(t.removePrefix("play ")))))
            "Searching for ${t.removePrefix("play ")}"
        }
        t.isBlank() -> "What can I help you with?"
        else -> { web(ctx, t); "Here's what I found on the web" }
    }
}

@Composable
fun SiriOverlay(apps: List<AppItem>, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    var reply by remember { mutableStateOf("What can I help you with?") }
    BackHandler(onBack = onClose)
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val s = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (s != null) { text = s; reply = siriRespond(ctx, s, apps) }
    }
    val tr = rememberInfiniteTransition(label = "orb")
    val angle by tr.animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "a")

    Box(Modifier.fillMaxSize().background(Color(0xE6000000)).pointerInput(Unit) { detectTapGestures { } }) {
        Text("Done", color = Color(0xFF0A84FF), modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(20.dp).clickable(onClick = onClose))
        Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(140.dp).rotate(angle)) {
                drawCircle(
                    Brush.sweepGradient(listOf(Color(0xFF00E5FF), Color(0xFFFF2DAA), Color(0xFF5B5BFF), Color(0xFF00E5FF))),
                    radius = size.minDimension / 2
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(reply, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        }
        Row(
            Modifier.align(Alignment.BottomCenter).imePadding().navigationBarsPadding().padding(16.dp).fillMaxWidth()
                .clip(RoundedCornerShape(26.dp)).background(Color(0x33FFFFFF)).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = Color.White, fontSize = 17.sp), cursorBrush = SolidColor(Color.White),
                singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { reply = siriRespond(ctx, text, apps) }),
                decorationBox = { inner -> if (text.isEmpty()) Text("Ask Siri…", color = Color(0x99FFFFFF), fontSize = 17.sp); inner() }
            )
            Text("🎤", fontSize = 22.sp, modifier = Modifier.clickable {
                try {
                    mic.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
                } catch (_: ActivityNotFoundException) { reply = "Voice input isn't available on this device" }
            })
        }
    }
}
