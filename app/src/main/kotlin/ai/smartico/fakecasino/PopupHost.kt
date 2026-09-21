package ai.smartico.fakecasino

import ai.smartico.publicapi.Smartico
import ai.smartico.publicapi.bridge.SMTO_WRAPPER_UA
import ai.smartico.publicapi.bridge.WRAPPER_POPUP_URL
import ai.smartico.publicapi.bridge.buildWrapperUrl
import ai.smartico.publicapi.engagement.EngagementPayload
import ai.smartico.publicapi.engagement.PopupSessionHooks
import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Campaign popups (cid 110, activityType 30).
 *
 * The SDK owns the pipeline — dedupe, queue, and the bcid handshake. This host
 * only decides WHEN to show one (here: as soon as nothing else is showing) and
 * renders the transparent WebView the operator's HTML lands in.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PopupHost() {
    val pending by Sdk.pendingPopups.collectAsState()
    var current by remember { mutableStateOf<EngagementPayload?>(null) }
    var visible by remember { mutableStateOf(false) }
    var web by remember { mutableStateOf<WebView?>(null) }

    // pump: take the next popup whenever the queue has one and we're free
    LaunchedEffect(pending, current) {
        if (current == null && Smartico.pendingEngagements() > 0) {
            current = Smartico.takeEngagement()
            android.util.Log.i("SmarticoDemo", "popup taken, opening wrapper")
        }
    }

    val payload = current ?: return
    val session = remember(payload) {
        Smartico.createPopupSession(payload, object : PopupSessionHooks {
            override fun injectJs(js: String) {
                web?.post { web?.evaluateJavascript(js, null) }
            }
            override fun onReadyToShow() { visible = true }
            override fun onClose() {
                visible = false
                current = null
            }
        })
    }

    val url = remember {
        // the popup wrapper gets its content injected (bcid 3), so no hash here
        buildWrapperUrl(
            labelKey = Sdk.LABEL_KEY,
            brandKey = Sdk.BRAND_KEY,
            extUserId = Sdk.extUserId,
            wrapper = WRAPPER_POPUP_URL,
        )
    }

    // The WebView is ALWAYS composed, never wrapped in AnimatedVisibility:
    // the wrapper page only reports "ready to be shown" after it has loaded and
    // rendered, so a WebView that is created on `visible` can never get there —
    // the popup is taken off the queue and then silently never appears.
    // Instead it starts INVISIBLE (loads, runs JS, takes no touches) and is
    // revealed when the page says it is ready.
    val alpha by animateFloatAsState(if (visible) 1f else 0f, label = "popup")
    Box(Modifier.fillMaxSize()) {
        run {
            AndroidView(
                modifier = Modifier.fillMaxSize().alpha(alpha),
                update = { it.visibility = if (visible) View.VISIBLE else View.INVISIBLE },
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = SMTO_WRAPPER_UA
                        setBackgroundColor(AndroidColor.TRANSPARENT)
                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun postMessage(data: String) {
                                    android.util.Log.i("SmarticoDemo", "popup bridge ← ${data.take(160)}")
                                    post { session.handleMessage(data) }
                                }
                            },
                            "ReactNativeWebView",
                        )
                        web = this
                        visibility = View.INVISIBLE
                        loadUrl(url)
                    }
                },
            )
        }
    }
}
