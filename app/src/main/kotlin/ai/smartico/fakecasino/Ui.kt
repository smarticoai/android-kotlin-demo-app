package ai.smartico.fakecasino

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import coil.compose.AsyncImage

/** Server text fields carry HTML markup — strip it before display. */
fun stripHtml(s: String?): String =
    s.orEmpty().replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ").trim()

/**
 * Loads data once per screen and renders loading/error/empty states around it.
 * Keeps every screen down to "here's my list, here's a row".
 */
@Composable
fun <T> ApiList(
    title: String,
    load: suspend () -> List<T>,
    reloadKey: Any = Unit,
    row: @Composable (T) -> Unit,
) {
    val state by produceState<Result<List<T>>?>(initialValue = null, reloadKey) {
        value = runCatching { load() }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp))
        when {
            state == null -> Loading()
            state!!.isFailure -> Text("Error: ${state!!.exceptionOrNull()?.message}", color = Color(0xFFFF6B6B))
            state!!.getOrNull()!!.isEmpty() -> Text("Nothing here yet.", color = Muted)
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(state!!.getOrNull()!!.size) { i -> row(state!!.getOrNull()!![i]) }
            }
        }
    }
}

@Composable
fun Loading() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(color = Accent)
}

@Composable
fun Card(content: @Composable () -> Unit) = Surface(
    color = ai.smartico.fakecasino.Card,
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth(),
) {
    Column(Modifier.padding(14.dp)) { content() }
}

@Composable
fun Title(text: String) = Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)

@Composable
fun Meta(text: String) = Text(text, color = Muted, fontSize = 12.sp)

@Composable
fun Thumb(url: String?, size: Int = 56) {
    if (!url.isNullOrEmpty()) {
        AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(size.dp))
    }
}

/**
 * Mission/level progress. The fill ANIMATES to its new value (650ms, ease-out)
 * so a push-driven jump reads as movement rather than a redraw, and turns green
 * once the item is complete — same behaviour as the RN demo's ProgressBar.
 */
@Composable
fun Progress(percent: Double, completed: Boolean = false) {
    val target = (percent / 100.0).toFloat().coerceIn(0f, 1f)
    val width by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "progress",
    )
    LinearProgressIndicator(
        progress = { width },
        color = if (completed) ProgressDone else Accent,
        trackColor = Color(0xFF2A2B45),
        modifier = Modifier.fillMaxWidth().height(6.dp),
    )
}

/** Small status pill (Locked / Completed / In Progress …). */
@Composable
fun Badge(label: String, tone: Color) = Surface(
    color = tone.copy(alpha = 0.18f),
    shape = RoundedCornerShape(6.dp),
) {
    Text(
        label,
        color = tone,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun Section(text: String) = Text(
    text.uppercase(),
    color = Muted,
    fontSize = 10.sp,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.padding(bottom = 4.dp),
)

/** "2d 03:04:05" style remaining time, or null once the deadline has passed. */
fun countdown(endTs: Long, now: Long): String? {
    val left = endTs - now
    if (left <= 0) return null
    val s = left / 1000
    val d = s / 86400
    val h = (s % 86400) / 3600
    val m = (s % 3600) / 60
    return (if (d > 0) "${d}d " else "") + "%02d:%02d:%02d".format(h, m, s % 60)
}

/** Compact remaining time for tiles: "2d 3h" once past a day, else h:mm:ss. */
fun countdownShort(endTs: Long, now: Long): String {
    val left = endTs - now
    if (left <= 0) return "—"
    val s = left / 1000
    val d = s / 86400
    val h = (s % 86400) / 3600
    return if (d > 0) "${d}d ${h}h" else "%02d:%02d:%02d".format(h, (s % 3600) / 60, s % 60)
}

/** "August 12 — August 19", the range shown above a tournament's details. */
fun dateRange(startTs: Long?, endTs: Long?): String {
    val fmt = java.text.SimpleDateFormat("MMMM dd", java.util.Locale.ENGLISH)
    fun f(ts: Long?) = if (ts == null || ts <= 0) "—" else fmt.format(java.util.Date(ts))
    return "${f(startTs)} — ${f(endTs)}"
}

/** A ticking clock, live only while [active] — a screen with no timer stays idle. */
@Composable
fun rememberNow(active: Boolean = true): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) {
        while (active) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }
    return now
}

/** A button that runs a suspend action and shows its result inline. */
@Composable
fun ActionButton(label: String, onResult: (String) -> Unit = {}, action: suspend () -> String) {
    var busy by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Button(
        onClick = {
            busy = true
            scope.launch {
                val msg = runCatching { action() }.getOrElse { "Failed: ${it.message}" }
                busy = false
                onResult(msg)
            }
        },
        enabled = !busy,
        colors = ButtonDefaults.buttonColors(containerColor = Accent),
    ) { Text(if (busy) "…" else label) }
}
