package com.ilauncher.ios

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val Wallpaper = Brush.linearGradient(
    listOf(Color(0xFF4A7BE8), Color(0xFF6BC7D8), Color(0xFFF2E27B), Color(0xFFF29B7B))
)

/** Everything an icon needs to take part in edit (jiggle) mode. */
class EditState(
    val edit: Boolean,
    val dragKey: String?,
    val onStart: (String, Offset) -> Unit,
    val onDrag: (Offset) -> Unit,
    val onEnd: () -> Unit,
    val onRect: (String, Rect) -> Unit,
    val onGone: (String) -> Unit,
    val onRemove: (AppItem) -> Unit,
    val onEdit: () -> Unit
)

fun pageEnd(p: Int) = if (p == 0) 16 else 16 + 24 * p

fun dockApps(ctx: android.content.Context, apps: List<AppItem>): List<AppItem> {
    val pm = ctx.packageManager
    val intents = listOf(
        Intent(Intent.ACTION_DIAL),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
        Intent(MediaStore.ACTION_IMAGE_CAPTURE)
    )
    val picked = intents
        .mapNotNull { pm.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }
        .mapNotNull { pkg -> apps.firstOrNull { it.pkg == pkg } }
        .distinctBy { it.pkg }
    return (picked + apps.filter { a -> picked.none { it.pkg == a.pkg } }).take(4)
}

@Composable
fun HomeScreen(
    apps: List<AppItem>, wx: Wx?, homeTick: Int,
    openSpotlight: () -> Unit, openControl: () -> Unit, openSiri: () -> Unit
) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("ilauncher", 0) }
    var hidden by remember { mutableStateOf(prefs.getStringSet("hidden", emptySet())!!.toSet()) }
    val dock = remember(apps) { dockApps(ctx, apps) }

    // Grid apps in the user's saved order (hidden apps live only in the App Library)
    val order = remember(apps, dock, hidden) {
        val saved = (prefs.getString("order", "") ?: "").split("\n").filter { it.isNotEmpty() }
        val idx = saved.withIndex().associate { it.value to it.index }
        mutableStateListOf<AppItem>().apply {
            addAll(
                apps.filter { a -> dock.none { it.pkg == a.pkg } && a.key !in hidden }
                    .sortedBy { idx[it.key] ?: Int.MAX_VALUE }
            )
        }
    }
    val all = order.toList()
    val pages = listOf(all.take(16)) + all.drop(16).chunked(24)
    val pager = rememberPagerState(pageCount = { pages.size + 1 })

    var edit by remember { mutableStateOf(false) }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    val rects = remember { mutableMapOf<String, Rect>() }
    BackHandler(edit) { edit = false }
    LaunchedEffect(homeTick) { edit = false }

    val density = LocalDensity.current
    val widthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val topZone = with(density) { 140.dp.toPx() }
    val edgePx = with(density) { 48.dp.toPx() }
    var sx by remember { mutableFloatStateOf(0f) }
    var sy by remember { mutableFloatStateOf(0f) }
    var total by remember { mutableFloatStateOf(0f) }

    // Drag near the screen edge => flip page
    LaunchedEffect(dragKey) {
        while (dragKey != null) {
            val x = dragPos.x
            val last = pages.size - 1
            if (x < edgePx && pager.currentPage > 0) {
                delay(700)
                if (dragKey != null && dragPos.x < edgePx) pager.animateScrollToPage(pager.currentPage - 1)
            } else if (x > widthPx - edgePx && pager.currentPage < last) {
                delay(700)
                if (dragKey != null && dragPos.x > widthPx - edgePx) pager.animateScrollToPage(pager.currentPage + 1)
            } else delay(80)
        }
    }

    fun dropAt(pos: Offset) {
        val k = dragKey ?: return
        val p = pager.currentPage
        if (p >= pages.size) return
        val from = order.indexOfFirst { it.key == k }
        if (from < 0) return
        val tgtKey = rects.entries.firstOrNull { it.key != k && it.value.contains(pos) }?.key
        val tgtIdx = if (tgtKey != null) order.indexOfFirst { it.key == tgtKey } else -1
        val item = order.removeAt(from)
        val at = if (tgtIdx >= 0) tgtIdx else minOf(pageEnd(p) - 1, order.size)
        order.add(at.coerceIn(0, order.size), item)
        saveOrder(ctx, order)
    }

    val es = EditState(
        edit = edit, dragKey = dragKey,
        onStart = { k, o -> dragKey = k; dragPos = (rects[k]?.topLeft ?: Offset.Zero) + o },
        onDrag = { d -> dragPos += d },
        onEnd = { dropAt(dragPos); dragKey = null },
        onRect = { k, r -> rects[k] = r },
        onGone = { k -> rects.remove(k) },
        onRemove = { a ->
            saveOrder(ctx, order)
            val h = hidden + a.key
            prefs.edit().putStringSet("hidden", h.toHashSet()).apply()
            hidden = h
        },
        onEdit = { edit = true }
    )

    Box(
        Modifier.fillMaxSize().background(Wallpaper)
            .pointerInput(edit) {
                if (!edit) detectVerticalDragGestures(
                    onDragStart = { o -> sx = o.x; sy = o.y; total = 0f },
                    onDragEnd = {
                        if (total > 120f) {
                            if (sy < topZone && sx > widthPx / 2) openControl() else openSpotlight()
                        }
                    },
                    onDragCancel = {},
                    onVerticalDrag = { _, dy -> total += dy }
                )
            }
    ) {
        HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = dragKey == null) { p ->
            if (p < pages.size) AppPage(pages[p], p == 0, wx, es)
            else AppLibrary(apps, hidden) { a ->
                val h = hidden - a.key
                prefs.edit().putStringSet("hidden", h.toHashSet()).apply()
                hidden = h
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PageDots(pager.currentPage, pages.size + 1)
            Box(
                Modifier.padding(vertical = 10.dp).clip(RoundedCornerShape(50))
                    .background(Color(0x40FFFFFF))
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                        onClick = openSpotlight, onLongClick = openSiri
                    )
                    .padding(horizontal = 18.dp, vertical = 6.dp)
            ) { Text("🔍 Search", color = Color.White, fontSize = 12.sp) }
            Row(
                Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(30.dp))
                    .background(Color(0x40FFFFFF)).padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) { dock.forEach { AppIcon(it, showLabel = false, es = es, editable = false) } }
        }

        if (edit) {
            Box(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)
                    .clip(RoundedCornerShape(50)).background(Color(0x66000000))
                    .clickable { edit = false }.padding(horizontal = 16.dp, vertical = 7.dp)
            ) { Text("Done", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        }

        // icon that follows the finger while dragging
        dragKey?.let { k ->
            order.firstOrNull { it.key == k }?.let { a ->
                Image(
                    a.icon, null,
                    Modifier.offset { IntOffset((dragPos.x - 34.dp.toPx()).toInt(), (dragPos.y - 34.dp.toPx()).toInt()) }
                        .size(68.dp).clip(RoundedCornerShape(16.dp))
                )
            }
        }
    }
}

@Composable
fun PageDots(cur: Int, n: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(n) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (it == cur) Color.White else Color(0x66FFFFFF)))
        }
    }
}

@Composable
fun AppPage(apps: List<AppItem>, first: Boolean, wx: Wx?, es: EditState) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(top = 12.dp, start = 8.dp, end = 8.dp, bottom = 200.dp)
    ) {
        if (first) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(150.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                WeatherWidget(wx, Modifier.weight(1f).fillMaxHeight())
                CalendarWidget(Modifier.weight(1f).fillMaxHeight())
            }
            Spacer(Modifier.height(16.dp))
        }
        apps.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { AppIcon(it, es = es) }
                repeat(4 - row.size) { Spacer(Modifier.width(74.dp)) }
            }
        }
    }
}

@Composable
fun WeatherWidget(wx: Wx?, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF3A7BD5), Color(0xFF6FB1FC)))).padding(14.dp)
    ) {
        Text("${Config.CITY} ➤", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (wx != null) "${wx.temp}°" else "--°", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.weight(1f))
        Text(wx?.desc ?: "Loading…", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(if (wx != null) "H:${wx.hi}° L:${wx.lo}°" else "", color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun CalendarWidget(modifier: Modifier) {
    val ctx = LocalContext.current
    val d = LocalDate.now()
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(Color.White)
            .clickable { safeStart(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/"))) }
            .padding(14.dp)
    ) {
        Text(d.format(DateTimeFormatter.ofPattern("EEEE")).uppercase(), color = Color(0xFFFF3B30), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("${d.dayOfMonth}", color = Color.Black, fontSize = 36.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text("No events today", color = Color.Gray, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
    app: AppItem, showLabel: Boolean = true, es: EditState? = null,
    editable: Boolean = true, onAdd: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val editing = es != null && es.edit && editable
    val dragging = es != null && es.dragKey == app.key
    val rot = if (editing) rememberInfiniteTransition(label = "wiggle").animateFloat(
        -2f, 2f,
        infiniteRepeatable(tween(110 + (app.label.length % 5) * 12, easing = LinearEasing), RepeatMode.Reverse),
        label = "r"
    ) else null

    if (es != null && editable) {
        DisposableEffect(app.key) { onDispose { es.onGone(app.key) } }
    }

    Box(
        Modifier.width(74.dp).onGloballyPositioned { c ->
            if (es != null && editable) es.onRect(app.key, c.boundsInRoot())
        }
    ) {
        Column(
            Modifier.fillMaxWidth()
                .graphicsLayer {
                    rotationZ = if (rot != null && !dragging) rot.value else 0f
                    alpha = if (dragging) 0.25f else 1f
                }
                .pointerInput(editing, app.key) {
                    if (editing && es != null) detectDragGestures(
                        onDragStart = { o -> es.onStart(app.key, o) },
                        onDrag = { change, d -> change.consume(); es.onDrag(d) },
                        onDragEnd = { es.onEnd() },
                        onDragCancel = { es.onEnd() }
                    )
                }
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClick = { if (!editing) launch(ctx, app) },
                    onLongClick = { if (!editing) menu = true }
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                bitmap = app.icon, contentDescription = app.label,
                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(14.dp))
            )
            if (showLabel) Text(
                app.label, color = Color.White, fontSize = 11.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (editing && es != null) {
            Box(
                Modifier.align(Alignment.TopStart).offset(x = 0.dp, y = (-2).dp).size(22.dp)
                    .clip(CircleShape).background(Color(0xE68E8E93)).clickable { es.onRemove(app) },
                contentAlignment = Alignment.Center
            ) { Text("–", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (es != null && editable) DropdownMenuItem(text = { Text("Edit Home Screen") }, onClick = {
                menu = false; es.onEdit()
            })
            if (onAdd != null) DropdownMenuItem(text = { Text("Add to Home Screen") }, onClick = {
                menu = false; onAdd()
            })
            DropdownMenuItem(text = { Text("App Info") }, onClick = {
                menu = false
                safeStart(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")))
            })
            DropdownMenuItem(text = { Text("Remove App") }, onClick = {
                menu = false
                safeStart(ctx, Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}")))
            })
        }
    }
}

fun categoryName(c: Int) = when (c) {
    ApplicationInfo.CATEGORY_SOCIAL -> "Social"
    ApplicationInfo.CATEGORY_GAME -> "Games"
    ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> "Entertainment"
    ApplicationInfo.CATEGORY_IMAGE -> "Photo & Video"
    ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
    ApplicationInfo.CATEGORY_NEWS -> "Information & Reading"
    ApplicationInfo.CATEGORY_MAPS -> "Travel"
    else -> "Utilities"
}

@Composable
fun AppLibrary(apps: List<AppItem>, hidden: Set<String>, onAdd: (AppItem) -> Unit) {
    val ctx = LocalContext.current
    var folder by remember { mutableStateOf<Pair<String, List<AppItem>>?>(null) }
    val groups = remember(apps) { apps.groupBy { categoryName(it.category) }.toList().sortedBy { it.first } }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        Text("App Library", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp, bottom = 12.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 220.dp)) {
            items(groups.chunked(2)) { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { (name, list) ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(Color(0x40FFFFFF))
                                .clickable { folder = name to list }.padding(12.dp)
                        ) {
                            Text(name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(8.dp))
                            list.take(4).chunked(2).forEach { r ->
                                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    r.forEach { a ->
                                        Image(a.icon, a.label, Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)).clickable { launch(ctx, a) })
                                    }
                                    if (r.size == 1) Spacer(Modifier.size(44.dp))
                                }
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
    folder?.let { (n, l) ->
        Dialog(onDismissRequest = { folder = null }) {
            Column(Modifier.clip(RoundedCornerShape(28.dp)).background(Color(0xEE2C2C2E)).padding(16.dp)) {
                Text(n, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(l.chunked(4)) { r ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            r.forEach { a ->
                                AppIcon(a, onAdd = if (a.key in hidden) ({ onAdd(a) }) else null)
                            }
                            repeat(4 - r.size) { Spacer(Modifier.width(74.dp)) }
                        }
                    }
                }
            }
        }
    }
}
