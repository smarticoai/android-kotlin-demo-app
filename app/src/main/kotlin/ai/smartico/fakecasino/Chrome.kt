package ai.smartico.fakecasino

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * App chrome, ported from the RN demo (src/chrome/): persistent header on the
 * tab screens, a five-item bottom bar (Menu · VIP · Promos · Inbox · Profile)
 * and the burger drawer. The Lobby has no bar item — it is reached through the
 * logo or the burger, exactly like the original fake-casino.
 */
val ChromeBg = Color(0xFF15162A)
private val ChromeBorder = Color(0xFF2A2B45)

// ---------------------------------------------------------------- header ----

@Composable
fun AppHeader(nav: NavHostController) {
    val props by Sdk.props.collectAsState()
    val balance by Economy.balance.collectAsState()
    val avatar by Sdk.avatar.collectAsState()

    Row(
        Modifier.fillMaxWidth().background(ChromeBg).statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "🎰 Fakebet",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { nav.navigate(Routes.LOBBY) },
        )
        Spacer(Modifier.weight(1f))
        Stat("🟡 ${Sdk.prop("ach_points_balance") ?: "—"}")
        Spacer(Modifier.width(8.dp))
        Stat("€ %.2f".format(balance))
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(Accent)
                .clickable { nav.navigate(Routes.CASHIER) }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) { Text("Deposit", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(8.dp))
        val url = avatar ?: Sdk.avatarUrl()
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(ChromeBorder)
                .clickable { nav.navigate(Routes.PROFILE) },
            contentAlignment = Alignment.Center,
        ) {
            if (!url.isNullOrEmpty()) {
                AsyncImage(model = url, contentDescription = "Profile", modifier = Modifier.fillMaxSize())
            } else {
                Text("👤", fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun Stat(text: String) = Box(
    Modifier.clip(RoundedCornerShape(9.dp)).background(Card).padding(horizontal = 8.dp, vertical = 5.dp),
) { Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }

/** Minimal back row for pushed feature screens (they render their own titles). */
@Composable
fun BackRow(nav: NavHostController) = Row(
    Modifier.fillMaxWidth().background(ChromeBg).statusBarsPadding()
        .padding(horizontal = 14.dp, vertical = 10.dp),
) {
    Text(
        "‹ Back",
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clickable { nav.popBackStack() },
    )
}

// ------------------------------------------------------------ bottom bar ----

@Composable
fun BottomBar(
    nav: NavHostController,
    current: String?,
    promosOpen: Boolean,
    onPromos: () -> Unit,
    onMenu: () -> Unit,
) {
    val props by Sdk.props.collectAsState()
    val unread = Sdk.prop("core_inbox_unread_count")?.toDoubleOrNull()?.toInt() ?: 0

    Column {
        // Promos spread — fake-casino's popping circles, simplified like in RN
        AnimatedVisibility(
            visible = promosOpen,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            ) {
                SpreadButton("🏆", "Tournaments") { onPromos(); nav.navigate(Routes.TOURNAMENTS) }
                SpreadButton("🎯", "Missions") { onPromos(); nav.navigate(Routes.MISSIONS) }
                SpreadButton("🎡", "Mini-games") { onPromos(); nav.navigate(Routes.GAMES) }
            }
        }

        Row(
            Modifier.fillMaxWidth().background(ChromeBg).navigationBarsPadding().padding(vertical = 8.dp),
        ) {
            BarItem("☰", "Menu", false, Modifier.weight(1f)) { onMenu() }
            BarItem("👑", "VIP", current == Routes.VIP, Modifier.weight(1f)) { nav.navigate(Routes.VIP) }
            BarItem(if (promosOpen) "✕" else "🎁", "Promos", promosOpen, Modifier.weight(1f)) { onPromos() }
            BarItem("📬", "Inbox", current == Routes.INBOX, Modifier.weight(1f), badge = unread) {
                nav.navigate(Routes.INBOX)
            }
            BarItem("👤", "Profile", current == Routes.PROFILE, Modifier.weight(1f)) { nav.navigate(Routes.PROFILE) }
        }
    }
}

@Composable
private fun BarItem(
    icon: String,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    badge: Int = 0,
    onClick: () -> Unit,
) = Column(
    modifier.clickable(onClick = onClick),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Box {
        Text(icon, fontSize = 18.sp, color = if (active) Accent else Muted)
        if (badge > 0) {
            Box(
                Modifier
                    .offset(x = 12.dp, y = (-4).dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Accent)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(
                    if (badge > 99) "99+" else "$badge",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
    Text(
        label,
        color = if (active) Accent else Muted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun SpreadButton(icon: String, label: String, onClick: () -> Unit) = Column(
    Modifier
        .clip(RoundedCornerShape(16.dp))
        .background(Card)
        .clickable(onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 10.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Text(icon, fontSize = 22.sp)
    Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
}

// ---------------------------------------------------------------- burger ----

private data class MenuItem(
    val icon: String,
    val label: String,
    val route: String? = null,
    val disabled: Boolean = false,
)

// The RN demo disables Store (no native screen there); this demo has one.
private val MENU_ITEMS = listOf(
    MenuItem("🎰", "Casino", Routes.LOBBY),
    MenuItem("⚽", "Sports — coming soon", disabled = true),
    MenuItem("🎯", "Missions", Routes.MISSIONS),
    MenuItem("🏆", "Tournaments", Routes.TOURNAMENTS),
    MenuItem("🎟️", "Raffles", Routes.RAFFLES),
    MenuItem("💰", "Jackpots", Routes.JACKPOTS),
    MenuItem("🛍️", "Store", Routes.STORE),
    MenuItem("🎡", "Mini-games", Routes.GAMES),
    MenuItem("🥇", "Leaderboard", Routes.LEADERBOARD),
    MenuItem("👑", "VIP", Routes.VIP),
)

/** The fake-casino burger drawer: section links + reset progress + logout. */
@Composable
fun BurgerMenu(visible: Boolean, onClose: () -> Unit, nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmReset by remember { mutableStateOf(false) }

    // the system back closes the drawer, not the app
    BackHandler(enabled = visible) { onClose() }

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxSize().background(Color(0x99000000))
                .clickable(onClick = onClose), // tap outside closes
        )
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally { -it },
        exit = slideOutHorizontally { -it },
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.78f)
                .background(Bg)
                .statusBarsPadding()
                .padding(horizontal = 18.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "🎰 Fakebet",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text("✕", color = Muted, fontSize = 20.sp, modifier = Modifier.clickable(onClick = onClose))
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                MENU_ITEMS.forEach { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !item.disabled) {
                                onClose()
                                item.route?.let { nav.navigate(it) }
                            }
                            .padding(vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(item.icon, fontSize = 18.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            item.label,
                            color = if (item.disabled) Muted else Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                Text(
                    "♻️ Reset progress",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().clickable { confirmReset = true }.padding(vertical = 10.dp),
                )
                Text(
                    "Log out",
                    color = Color(0xFFFF8F8F),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().clickable {
                        onClose()
                        Sdk.logout(ctx)
                    }.padding(vertical = 10.dp),
                )
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = Card,
            title = { Text("Reset progress", color = Color.White) },
            text = {
                Text(
                    "Start over as a fresh player? Points, levels and missions will reset.",
                    color = Muted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    onClose()
                    scope.launch { Sdk.resetProgress(ctx) }
                }) { Text("Reset", color = Color(0xFFFF8F8F)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            },
        )
    }
}
