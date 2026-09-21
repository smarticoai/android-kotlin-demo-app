package ai.smartico.fakecasino

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The fake slot, ported from the web fake-casino GamePage.
 *
 * There is no reel engine: the "game" is a play-once sprite, and which sprite
 * plays is decided by a tiny state machine so the visuals continue from the
 * previous outcome. The win is DETERMINISTIC — win = bet × multiplier (0 means
 * a loss), so the player literally dials their own outcome. Each spin reports
 * `casino_bet_win` to the demo backend, which settles the wallet.
 */
private enum class Fsm { STATIC, LOSS_TO_LOSS, LOSS_TO_WIN, WIN_TO_LOSS, WIN_TO_WIN }

private fun Fsm.sprite(game: CasinoGame): String = when (this) {
    Fsm.STATIC -> game.staticImg
    Fsm.LOSS_TO_LOSS -> game.lossToLoss
    Fsm.LOSS_TO_WIN -> game.lossToWin
    Fsm.WIN_TO_LOSS -> game.winToLoss
    Fsm.WIN_TO_WIN -> game.winToWin
}

private fun Fsm.next(win: Boolean): Fsm {
    val wasWin = this == Fsm.LOSS_TO_WIN || this == Fsm.WIN_TO_WIN
    return when {
        wasWin && win -> Fsm.WIN_TO_WIN
        wasWin && !win -> Fsm.WIN_TO_LOSS
        !wasWin && win -> Fsm.LOSS_TO_WIN
        else -> Fsm.LOSS_TO_LOSS
    }
}

@Composable
fun GameScreen(extId: String, onClose: () -> Unit) {
    val game = remember(extId) { casinoGames.find { it.extId == extId } }
    if (game == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Game not found", color = Muted) }
        return
    }

    val scope = rememberCoroutineScope()
    val balance by Economy.balance.collectAsState()
    var bet by remember { mutableIntStateOf(10) }
    var mult by remember { mutableIntStateOf(3) }
    var fsm by remember { mutableStateOf(Fsm.STATIC) }
    var spinning by remember { mutableStateOf(false) }
    // expo-image loops animated files with no stop control and so does Coil —
    // after the spin window we cut back to the static frame
    var animating by remember { mutableStateOf(false) }
    var winShown by remember { mutableStateOf<Int?>(null) }
    var spinSeq by remember { mutableIntStateOf(0) }

    val winScale by animateFloatAsState(if (winShown != null) 1f else 0.4f, label = "win")

    var panelOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // The four transition clips are ~1MB each. Without this the first spin
        // spends most of its 2s window downloading and the reels only twitch at
        // the end; warming the disk cache on open makes every spin start at once.
        val ctx = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(game.extId) {
            for (path in listOf(game.lossToLoss, game.lossToWin, game.winToLoss, game.winToWin)) {
                ctx.imageLoader.enqueue(
                    ImageRequest.Builder(ctx).data(gameAsset(path)).build(),
                )
            }
        }

        // key() rebuilds the image node per spin and the disabled memory cache
        // forces a fresh decode, so the play-once clip actually plays again
        // instead of re-showing its final frame.
        key(spinSeq, animating) {
            AsyncImage(
                model = ImageRequest.Builder(ctx)
                    .data(gameAsset(if (animating) fsm.sprite(game) else game.staticImg))
                    .memoryCachePolicy(if (animating) CachePolicy.DISABLED else CachePolicy.ENABLED)
                    .build(),
                contentDescription = game.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // top bar
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("‹ Back", color = Color.White, modifier = Modifier.clickable { onClose() })
            Text(game.name, color = Color.White, fontWeight = FontWeight.Bold)
            Text("€ %.2f".format(balance), color = Color.White)
        }

        winShown?.let { amount ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (amount > 0) "WIN € $amount" else "NO WIN",
                    color = if (amount > 0) Color(0xFFFFD479) else Muted,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.scale(winScale),
                )
            }
        }

        if (!panelOpen) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    .background(Card)
                    .clickable { panelOpen = true }
                    .padding(horizontal = 10.dp, vertical = 16.dp),
            ) { Text("🎯", fontSize = 18.sp) }
        } else {
            Box(Modifier.align(Alignment.TopEnd)) {
                GameSidePanel(extId = extId, onClose = { panelOpen = false })
            }
        }

        // controls
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Stepper("MULT", "×$mult", { mult = (mult - 1).coerceAtLeast(0) }, { mult = (mult + 1).coerceAtMost(5) })
            Box(
                Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(if (spinning) Muted else Accent)
                    .clickable(enabled = !spinning && bet > 0) {
                        spinning = true
                        winShown = null
                        val win = bet * mult
                        fsm = fsm.next(win > 0)
                        spinSeq++
                        animating = true
                        scope.launch {
                            // the backend settles the money; the poll shows it
                            Economy.reportSpin(game.extId, bet, win)
                        }
                        scope.launch {
                            delay(2000)
                            animating = false
                            winShown = win
                            spinning = false
                            delay(2000)
                            winShown = null
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Text(if (spinning) "…" else "SPIN", color = Color.White, fontWeight = FontWeight.Bold) }
            Stepper("BET", "€$bet", { bet = (bet - 10).coerceAtLeast(0) }, { bet = (bet + 10).coerceAtMost(500) })
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepBtn("−", onMinus)
            Spacer(Modifier.width(8.dp))
            Text(value, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            StepBtn("+", onPlus)
        }
    }
}

@Composable
private fun StepBtn(text: String, onClick: () -> Unit) = Box(
    Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Card).clickable { onClick() },
    contentAlignment = Alignment.Center,
) { Text(text, color = Color.White, fontSize = 18.sp) }
