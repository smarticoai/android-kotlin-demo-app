package ai.smartico.fakecasino

import ai.smartico.publicapi.Smartico
import ai.smartico.publicapi.bridge.SMTO_WRAPPER_UA
import ai.smartico.publicapi.bridge.WidgetSessionHooks
import ai.smartico.publicapi.bridge.buildWrapperUrl
import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Hosts a Smartico widget deep link (mini-games) in a WebView.
 *
 * The page identifies itself from the URL and runs the deep link; the SDK's
 * WidgetBridgeSession handles the whole postMessage conversation, so this
 * screen only supplies the WebView, a loader and what "close"/"navigate"
 * should do.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WidgetScreen(dp: String, onClose: () -> Unit) {
    var ready by remember { mutableStateOf(false) }
    var currentDp by remember { mutableStateOf(dp) }
    var web by remember { mutableStateOf<WebView?>(null) }

    val session = remember {
        Smartico.createWidgetSession(object : WidgetSessionHooks {
            override fun onReady() { ready = true }
            override fun onClose() { onClose() }
            override fun onNavigateInWidget(dpRaw: String) {
                // in-widget flows (respin offers, section jumps) stay inside
                ready = false
                currentDp = dpRaw
            }
        })
    }

    val url = remember(currentDp) {
        buildWrapperUrl(
            labelKey = Sdk.LABEL_KEY,
            brandKey = Sdk.BRAND_KEY,
            extUserId = Sdk.extUserId,
            base = Sdk.prop("native_app_gf_url"), // server-built URL when identify supplied one
            hash = Sdk.demoHash(Sdk.extUserId),
            dp = currentDp,
        )
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    // the page detects the wrapper by user agent
                    settings.userAgentString = SMTO_WRAPPER_UA
                    // the page posts bridge messages to window.ReactNativeWebView
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun postMessage(data: String) {
                                post { session.handleMessage(data) }
                            }
                        },
                        "ReactNativeWebView",
                    )
                    web = this
                    loadUrl(url)
                }
            },
            update = { view -> if (view.url != url) view.loadUrl(url) },
        )
        if (!ready) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading game…", color = Muted)
            }
        }
    }
}
