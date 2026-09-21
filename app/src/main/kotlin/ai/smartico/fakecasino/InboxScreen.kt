package ai.smartico.fakecasino

import ai.smartico.publicapi.Smartico
import ai.smartico.publicapi.api.deleteInboxMessage
import ai.smartico.publicapi.api.getInboxMessageBody
import ai.smartico.publicapi.api.getInboxMessages
import ai.smartico.publicapi.api.markAllInboxMessagesAsRead
import ai.smartico.publicapi.api.markInboxMessageAsRead
import ai.smartico.publicapi.api.reportClickEvent
import ai.smartico.publicapi.api.reportImpressionEvent
import ai.smartico.publicapi.types.ActivityTypeLimited
import ai.smartico.publicapi.api.markUnmarkInboxMessageAsFavorite
import ai.smartico.publicapi.types.TInboxMessage
import ai.smartico.publicapi.types.TInboxMessageBody
import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch

/**
 * Inbox.
 *
 * The LIST is native (getInboxMessages), but a message BODY is HTML authored in
 * the operator's backoffice — images, styling, links — so it is rendered in a
 * WebView rather than flattened to text. The WebView reports its own height
 * back, and link taps are handed to the deep-link router instead of navigating
 * inside the message.
 */
@Composable
fun InboxScreen() {
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<TInboxMessage>>(emptyList()) }
    val bodies = remember { mutableStateMapOf<String, TInboxMessageBody>() }
    var loading by remember { mutableStateOf(true) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<String?>(null) }
    // The mutations below don't refresh the list server-side, so local overlays
    // keep the UI honest until the next load.
    val readOverlay = remember { mutableStateMapOf<String, Boolean>() }
    val favOverlay = remember { mutableStateMapOf<String, Boolean>() }
    val deleted = remember { mutableStateMapOf<String, Boolean>() }
    var reload by remember { mutableIntStateOf(0) }
    /**
     * Engagement analytics are per message, not per render: expanding the same
     * message twice is still one impression. Popups get this for free — their
     * wrapper page reports itself over the bridge — but an inbox body is our
     * own WebView, so the reporting is ours too.
     */
    val impressed = remember { mutableSetOf<String>() }

    LaunchedEffect(reload) {
        loading = true
        messages = runCatching { Smartico.api.getInboxMessages() }.getOrDefault(emptyList())
        // titles/previews live in the body payload, so the list needs them upfront
        for (m in messages) {
            val guid = m.message_guid ?: continue
            if (bodies[guid] == null) {
                runCatching { Smartico.api.getInboxMessageBody(guid) }.getOrNull()?.let { bodies[guid] = it }
            }
        }
        loading = false
    }

    fun isRead(m: TInboxMessage) = readOverlay[m.message_guid] ?: (m.read == true)
    fun isFav(m: TInboxMessage) = favOverlay[m.message_guid] ?: (m.favorite == true)

    val visible = messages
        .filter { deleted[it.message_guid] != true }
        .filter { !favoritesOnly || isFav(it) }
    val unread = messages.count { deleted[it.message_guid] != true && !isRead(it) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Inbox" + if (unread > 0) "  ($unread)" else "",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (unread > 0) {
                Text(
                    "Mark all read",
                    color = Accent,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable {
                        scope.launch {
                            runCatching { Smartico.api.markAllInboxMessagesAsRead() }
                            messages.forEach { m -> m.message_guid?.let { readOverlay[it] = true } }
                        }
                    },
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InboxTab("All", !favoritesOnly) { favoritesOnly = false }
            InboxTab("★ Favorites", favoritesOnly) { favoritesOnly = true }
        }

        when {
            loading && messages.isEmpty() -> Loading()
            visible.isEmpty() -> Text("Nothing here yet.", color = Muted)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(visible.size) { i ->
                    val m = visible[i]
                    val guid = m.message_guid.orEmpty()
                    val body = bodies[guid]
                    Card {
                        Column(
                            Modifier.fillMaxWidth().clickable {
                                open = if (open == guid) null else guid
                                if (!isRead(m)) {
                                    readOverlay[guid] = true
                                    scope.launch { runCatching { Smartico.api.markInboxMessageAsRead(guid) } }
                                }
                            },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!isRead(m)) {
                                    Text("●", color = Accent, fontSize = 12.sp)
                                    Spacer(Modifier.width(6.dp))
                                }
                                Thumb(body?.icon, 36)
                                if (body?.icon != null) Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stripHtml(body?.title).ifEmpty { "(no title)" },
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = if (isRead(m)) FontWeight.Normal else FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (stripHtml(body?.preview_body).isNotEmpty()) {
                                        Text(
                                            stripHtml(body?.preview_body),
                                            color = Muted,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Meta(m.sent_date.orEmpty())
                                }
                                Text(
                                    if (isFav(m)) "★" else "☆",
                                    color = if (isFav(m)) Gold else Muted,
                                    fontSize = 18.sp,
                                    modifier = Modifier.clickable {
                                        val target = !isFav(m)
                                        favOverlay[guid] = target
                                        scope.launch {
                                            runCatching { Smartico.api.markUnmarkInboxMessageAsFavorite(guid, target) }
                                        }
                                    },
                                )
                            }
                        }

                        if (open == guid) {
                            // Reported here rather than on tap: this branch is
                            // what puts the message on screen, which is what an
                            // impression means.
                            LaunchedEffect(guid) {
                                if (impressed.add(guid)) {
                                    Smartico.api.reportImpressionEvent(
                                        guid,
                                        ActivityTypeLimited.Inbox.toLong(),
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            val html = body?.html_body
                            if (!html.isNullOrEmpty()) {
                                HtmlBody(html) { url ->
                                    Smartico.api.reportClickEvent(
                                        guid,
                                        ActivityTypeLimited.Inbox.toLong(),
                                        url,
                                    )
                                }
                            } else {
                                Meta(stripHtml(body?.preview_body).ifEmpty { "—" })
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                body?.action?.takeIf { it.isNotEmpty() }?.let { action ->
                                    ActionButton("Open") {
                                        Smartico.api.reportClickEvent(
                                            guid,
                                            ActivityTypeLimited.Inbox.toLong(),
                                            action,
                                        )
                                        Smartico.dp(action)
                                        ""
                                    }
                                }
                                ActionButton("Delete") {
                                    deleted[guid] = true
                                    open = null
                                    runCatching { Smartico.api.deleteInboxMessage(guid) }
                                    ""
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InboxTab(label: String, on: Boolean, onClick: () -> Unit) = Box(
    Modifier
        .clip(RoundedCornerShape(9.dp))
        .background(if (on) Accent else Card)
        .clickable { onClick() }
        .padding(horizontal = 14.dp, vertical = 7.dp),
) {
    Text(label, color = if (on) Color.White else Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
}

/**
 * Renders one message's HTML. The page measures itself and reports its height
 * back through a JS bridge (a WebView has no intrinsic height inside a scrolling
 * list), and every link tap goes to the deep-link router — so an operator link
 * to, say, tournaments opens the native screen instead of a browser.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlBody(html: String, onLinkClick: (String) -> Unit) {
    var heightDp by remember(html) { mutableIntStateOf(60) }
    val doc = remember(html) {
        """
        <!DOCTYPE html><html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
          body{margin:0;padding:0;background:transparent;color:#d7d7ee;
               font-family:system-ui,sans-serif;font-size:14px;line-height:1.5}
          img{max-width:100%;height:auto;border-radius:8px}
          a{color:#9d86ff}
          table{max-width:100%}
        </style></head><body>$html</body></html>
        """.trimIndent()
    }

    AndroidView(
        modifier = Modifier.fillMaxWidth().height(heightDp.dp),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun setHeight(value: String) {
                            val h = value.toFloatOrNull()?.toInt() ?: return
                            post { if (h > 0) heightDp = h.coerceIn(40, 2400) }
                        }
                    },
                    "SmarticoBody",
                )
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, req: WebResourceRequest?): Boolean {
                        val url = req?.url?.toString() ?: return false
                        // A link inside the body is a click on the engagement,
                        // whatever it turns out to open.
                        onLinkClick(url)
                        Smartico.dp(url) // operator links land on native screens
                        return true
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        // images arrive after load, so re-measure a few times
                        view?.evaluateJavascript(
                            """
                            (function(){
                              function post(){ SmarticoBody.setHeight(String(document.documentElement.scrollHeight)); }
                              post(); setTimeout(post, 100); setTimeout(post, 500); setTimeout(post, 1500);
                            })();
                            """.trimIndent(),
                            null,
                        )
                    }
                }
                loadDataWithBaseURL(null, doc, "text/html", "utf-8", null)
            }
        },
    )
}
