package ai.smartico.fakecasino

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    const val PROFILE = "profile"
    const val MISSIONS = "missions"
    const val TOURNAMENTS = "tournaments"
    const val GAMES = "games"
    const val INBOX = "inbox"
    const val LEVELS = "levels"
    const val LEADERBOARD = "leaderboard"
    const val JACKPOTS = "jackpots"
    const val RAFFLES = "raffles"
    const val STORE = "store"
    const val WIDGET = "widget/{dp}"
    const val LOBBY = "lobby"
    const val GAME = "game/{extId}"
    const val CASHIER = "cashier"
    const val AVATAR = "avatar"
    const val VIP = "vip"
    const val TOURNAMENT = "tournament/{instanceId}"
    const val RAFFLE = "raffle/{raffleId}"
    const val DRAW = "raffle/{raffleId}/draw/{drawId}"

    fun widget(dp: String) = "widget/" + URLEncoder.encode(dp, "UTF-8")
    fun game(extId: String) = "game/$extId"
    fun tournament(instanceId: Long) = "tournament/$instanceId"
    fun raffle(raffleId: Long) = "raffle/$raffleId"
    fun draw(raffleId: Long, drawId: Long) = "raffle/$raffleId/draw/$drawId"
}

/**
 * RN-parity structure: the four TAB routes (Lobby · VIP · Inbox · Profile) get
 * the persistent AppHeader and the five-item BottomBar; feature screens are
 * pushed on top with a plain back row and no bar; game/widget run full-screen.
 */
private val TAB_ROUTES = setOf(Routes.LOBBY, Routes.VIP, Routes.INBOX, Routes.PROFILE)

@Composable
fun AppNav(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    var burgerOpen by remember { mutableStateOf(false) }
    var promosOpen by remember { mutableStateOf(false) }

    val fullScreen = current?.startsWith("widget/") == true || current?.startsWith("game/") == true

    // back collapses the promos spread before it can pop anything
    androidx.activity.compose.BackHandler(enabled = promosOpen) { promosOpen = false }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Bg,
            topBar = {
                when {
                    fullScreen -> {}
                    current in TAB_ROUTES -> AppHeader(nav)
                    else -> BackRow(nav)
                }
            },
            bottomBar = {
                if (current in TAB_ROUTES) {
                    BottomBar(
                        nav = nav,
                        current = current,
                        promosOpen = promosOpen,
                        onPromos = { promosOpen = !promosOpen },
                        onMenu = { burgerOpen = true },
                    )
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                NavHost(nav, startDestination = Routes.LOBBY) {
                    composable(Routes.LOBBY) { LobbyScreen(nav) }
                    composable(Routes.CASHIER) { CashierScreen(onClose = { nav.popBackStack() }) }
                    composable(Routes.GAME) { back ->
                        GameScreen(extId = back.arguments?.getString("extId").orEmpty(), onClose = { nav.popBackStack() })
                    }
                    composable(Routes.PROFILE) { ProfileScreen(nav) }
                    composable(Routes.MISSIONS) { MissionsScreen() }
                    composable(Routes.TOURNAMENTS) { TournamentsScreen(nav) }
                    composable(Routes.GAMES) { MiniGamesScreen(nav) }
                    composable(Routes.INBOX) { InboxScreen() }
                    composable(Routes.LEVELS) { LevelsScreen() }
                    composable(Routes.VIP) { VipScreen() }
                    composable(Routes.AVATAR) { AvatarPickerScreen(onClose = { nav.popBackStack() }) }
                    composable(Routes.TOURNAMENT) { back ->
                        TournamentDetailScreen(
                            instanceId = back.arguments?.getString("instanceId")?.toLongOrNull() ?: 0,
                            onClose = { nav.popBackStack() },
                        )
                    }
                    composable(Routes.RAFFLE) { back ->
                        val raffleId = back.arguments?.getString("raffleId")?.toLongOrNull() ?: 0
                        RaffleDrawsScreen(
                            raffleId = raffleId,
                            onClose = { nav.popBackStack() },
                            onOpenDraw = { drawId -> nav.navigate(Routes.draw(raffleId, drawId)) },
                        )
                    }
                    composable(Routes.DRAW) { back ->
                        DrawDetailScreen(
                            raffleId = back.arguments?.getString("raffleId")?.toLongOrNull() ?: 0,
                            drawId = back.arguments?.getString("drawId")?.toLongOrNull() ?: 0,
                            onClose = { nav.popBackStack() },
                        )
                    }
                    composable(Routes.LEADERBOARD) { LeaderboardScreen() }
                    composable(Routes.JACKPOTS) { JackpotsScreen() }
                    composable(Routes.RAFFLES) { RafflesScreen(nav) }
                    composable(Routes.STORE) { StoreScreen() }
                    composable(Routes.WIDGET) { backStack ->
                        val dp = backStack.arguments?.getString("dp").orEmpty()
                        WidgetScreen(dp = URLDecoder.decode(dp, "UTF-8"), onClose = { nav.popBackStack() })
                    }
                }
            }
        }

        // drawn over everything, including the bar
        BurgerMenu(visible = burgerOpen, onClose = { burgerOpen = false }, nav = nav)
    }
}
