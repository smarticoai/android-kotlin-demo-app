package ai.smartico.fakecasino

import ai.smartico.publicapi.Smartico
import ai.smartico.publicapi.bridge.buildWrapperUrl
import ai.smartico.publicapi.dp.isWidgetAction
import ai.smartico.publicapi.dp.DeepLink
import ai.smartico.publicapi.dp.DpBindings
import ai.smartico.publicapi.dp.DpRouter
import ai.smartico.publicapi.dp.DpScreen
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.rememberNavController

/** Palette lifted from the web fake-casino so the demo looks familiar. */
val Bg = Color(0xFF0F1020)
val Card = Color(0xFF1A1B30)
val Accent = Color(0xFF7C5CFF)
val Muted = Color(0xFF8A8BA6)
val Good = Color(0xFF7CD39A)
val Gold = Color(0xFFFFD479)
/** Filled progress once the mission is complete. */
val ProgressDone = Color(0xFF1F6D47)

/**
 * Operator site paths → native screens. Mirrors the RN demo's tryOperatorUrl:
 * campaign links like https://play.smartico.ai/tournament-list must land on our
 * Tournaments screen, not in the browser. Unknown hosts/paths return false and
 * open externally.
 */
private val OPERATOR_HOSTS = setOf("play.smartico.ai")

private fun openOperatorUrl(nav: androidx.navigation.NavHostController, url: String): Boolean {
    val m = Regex("^https?://([^/?#]+)([^?#]*)", RegexOption.IGNORE_CASE).find(url.trim()) ?: return false
    if (m.groupValues[1].lowercase() !in OPERATOR_HOSTS) return false
    val parts = m.groupValues[2].split("/").filter { it.isNotEmpty() }
    val head = parts.getOrNull(0).orEmpty()
    val arg = parts.getOrNull(1)
    when (head) {
        "" -> nav.navigate(Routes.LOBBY)
        "tournament-list" -> nav.navigate(Routes.TOURNAMENTS)
        "tournament" -> arg?.toLongOrNull()
            ?.let { nav.navigate(Routes.tournament(it)) }
            ?: nav.navigate(Routes.TOURNAMENTS)
        "missions" -> nav.navigate(Routes.MISSIONS)
        "mini-games" -> nav.navigate(Routes.GAMES)
        "jackpots" -> nav.navigate(Routes.JACKPOTS)
        "raffles" -> nav.navigate(Routes.RAFFLES)
        "store" -> Smartico.dp("dp:gf_store&standalone=true")
        "vip" -> nav.navigate(Routes.VIP)
        "profile" -> nav.navigate(Routes.PROFILE)
        "inbox" -> nav.navigate(Routes.INBOX)
        "leaderboard" -> nav.navigate(Routes.LEADERBOARD)
        "game" -> arg?.let { nav.navigate(Routes.game(it)) } ?: return false
        "cashier", "wallet-page" -> nav.navigate(Routes.CASHIER)
        else -> return false // unknown operator path → let the browser have it
    }
    return true
}

class MainActivity : ComponentActivity() {

    /**
     * Android 13+ runtime prompt. Declining is a normal outcome — campaigns
     * simply never reach this device — so there is nothing to handle here.
     */
    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DpRouter.debug = true // log deep links nothing handled, dev only

        // Before setContent: this consumes the notification extras, so the
        // generic `dp` entry point below does not run the same link twice.
        Push.onTap(intent)

        setContent {
            val nav = rememberNavController()

            // Teach the SDK's deep-link router about THIS app's screens. Done
            // inside the composition because it needs the navigation handle.
            LaunchedEffect(nav) {
                Smartico.configureDp(object : DpBindings {
                    override fun openScreen(screen: DpScreen, dp: DeepLink): Boolean {
                        val route = when (screen) {
                            DpScreen.MISSIONS -> Routes.MISSIONS
                            DpScreen.TOURNAMENTS -> Routes.TOURNAMENTS
                            DpScreen.JACKPOTS -> Routes.JACKPOTS
                            DpScreen.RAFFLES -> Routes.RAFFLES
                            DpScreen.LEVELS -> Routes.LEVELS
                            DpScreen.PROFILE -> Routes.PROFILE
                            DpScreen.INBOX -> Routes.INBOX
                            DpScreen.LEADERBOARD -> Routes.LEADERBOARD
                        }
                        nav.navigate(route)
                        return true
                    }

                    /** Mini-games render in-app; other widget sections decline here. */
                    override fun openWidget(dp: DeepLink): Boolean {
                        if (dp.action !in setOf("gf_saw", "gf_section", "gf_quiz", "gf_matchx")) return false
                        nav.navigate(Routes.widget(dp.raw))
                        return true
                    }

                    /**
                     * Campaign CTAs are usually authored for the WEB, as plain
                     * links onto the operator's site. Opening those in a browser
                     * throws the player out of the app onto the web version of
                     * the same casino — so operator paths are mapped to our own
                     * screens, and only unknown links leave the app.
                     */
                    override fun openUrl(url: String, target: String?) {
                        if (openOperatorUrl(nav, url)) return
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                })

                // Our app's own deep links (fake-casino parity).
                Smartico.registerDpHandler { dp ->
                    when (dp.action) {
                        "deposit", "opencashier" -> { nav.navigate(Routes.CASHIER); true }
                        else -> false
                    }
                }

                // LAST RESORT (registered last): widget sections we do not render
                // natively — store, bonuses, clans… — open in the phone browser.
                // Any handler registered earlier wins over this one.
                Smartico.registerDpHandler { dp ->
                    if (!isWidgetAction(dp.action)) return@registerDpHandler false
                    val ext = Sdk.extUserId
                    if (ext.isEmpty()) return@registerDpHandler true
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(
                                buildWrapperUrl(
                                    labelKey = Sdk.LABEL_KEY,
                                    brandKey = Sdk.BRAND_KEY,
                                    extUserId = ext,
                                    hash = Sdk.demoHash(ext),
                                    dp = dp.raw,
                                ),
                            ),
                        ),
                    )
                    true
                }
            }

            // Deep-link entry point: a link handed to the app (push tap, OS
            // intent, `adb … --es dp "<link>"`) runs through the same router as
            // campaign CTAs, so both paths behave identically.
            val loggedInNow by Sdk.loggedIn.collectAsState()
            LaunchedEffect(loggedInNow) {
                if (loggedInNow) intent?.getStringExtra("dp")?.let { Smartico.dp(it) }
            }

            // A push tap can cold-start the app: the deep link is parked until
            // the SDK has identified the user, otherwise it would route into a
            // screen with no session behind it.
            LaunchedEffect(Unit) {
                Sdk.identified.collect { identified ->
                    if (!identified) return@collect
                    Push.flushPendingDp()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !Push.allowed(this@MainActivity)
                    ) {
                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            // the demo backend owns the money; poll it once identified
            LaunchedEffect(Unit) { Sdk.identified.collect { if (it) Economy.startPolling() } }

            val loggedIn by Sdk.loggedIn.collectAsState()
            // A stored session token means the user already signed in on this
            // device — resolve it with the backend before showing the login.
            var restoring by remember { mutableStateOf(Auth.cookie(this@MainActivity) != null) }
            LaunchedEffect(Unit) {
                if (restoring) {
                    Auth.restore(this@MainActivity)?.let { user ->
                        Sdk.start(user.user_ext_id)
                        Sdk.enrichProfile(user)
                    }
                    restoring = false
                }
            }

            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Card)) {
                Box(Modifier.fillMaxSize().background(Bg)) {
                    if (restoring) {
                        Loading()
                    } else if (!loggedIn) {
                        LoginScreen(onSession = { user ->
                            Sdk.start(user.user_ext_id)
                            Sdk.enrichProfile(user)
                        })
                    } else {
                        AppNav(nav)
                        // Campaign popups float above everything, like in the web app
                        PopupHost()
                    }
                }
            }
        }
    }

    /**
     * singleTop in the manifest means a tap on a notification while the app is
     * alive is delivered here instead of creating a second activity. Replacing
     * the stored intent keeps getIntent() in sync with what actually arrived.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Push.onTap(intent)
        Push.flushPendingDp()
        // A bare `dp` extra — an OS deep link or `adb … --es dp "<link>"` — with
        // no engagement behind it. Push.onTap consumes the extra when the intent
        // WAS a notification tap, so this cannot run the same link twice.
        intent.getStringExtra("dp")?.let { Smartico.dp(it) }
    }
}
