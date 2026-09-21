package ai.smartico.fakecasino

import ai.smartico.publicapi.Smartico
import ai.smartico.publicapi.api.claimRafflePrize
import ai.smartico.publicapi.api.getRaffleDrawRun
import ai.smartico.publicapi.api.getRaffleDrawRunsHistory
import ai.smartico.publicapi.api.getRaffles
import ai.smartico.publicapi.api.requestRaffleOptin
import ai.smartico.publicapi.types.RaffleDrawInstanceState
import ai.smartico.publicapi.types.RaffleDrawTypeExecution
import ai.smartico.publicapi.types.TRaffleDraw
import ai.smartico.publicapi.types.TRaffleDrawRun
import ai.smartico.publicapi.types.TRafflePrize
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Raffle draws + draw detail, ported from the RN demo (screens/raffles/).
 * A raffle owns several DRAWS (regular / recurring 🔁 / grand 🏆); each draw
 * owns prizes and past RUNS. Tickets are earned elsewhere (missions, spins) —
 * these screens show odds, unlock progress, run history and prize claiming.
 */

// TRaffleDraw.current_state / execution_type — taken from the SDK so they
// cannot drift out of sync with the server's numbering.
private val STATE_OPEN = RaffleDrawInstanceState.Open.toLong()
private val STATE_WINNER_SELECTION = RaffleDrawInstanceState.WinnerSelection.toLong()
private val STATE_EXECUTED = RaffleDrawInstanceState.Executed.toLong()
private val STATE_CANCELLED = RaffleDrawInstanceState.Cancelled.toLong()

/** A draw that will never run again: nothing to count down to. */
private fun TRaffleDraw.isTerminal() = current_state == STATE_EXECUTED || current_state == STATE_CANCELLED

/** k/m abbreviation, matching the web demo's formatNumber. */
fun formatNumber(n: Long?): String {
    val v = n ?: 0
    return when {
        v >= 10_000_000 -> "${v / 1_000_000}m"
        v >= 1_000_000 -> "${(v / 100_000) / 10.0}m"
        v >= 10_000 -> "${v / 1_000}k"
        v >= 1_000 -> "${(v / 100) / 10.0}k"
        else -> "$v"
    }
}

/** "1:Nk" win-chance ratio from the user's tickets vs the pool. */
private fun numbersToRatio(mine: Long, total: Long): String {
    if (mine <= 0) return "—"
    val r = total.toDouble() / mine
    fun strip(x: String) = x.removeSuffix(".0")
    return when {
        r >= 1_000_000 -> "1:" + strip("%.1f".format(r / 1_000_000)) + "m"
        r >= 1_000 -> "1:" + strip("%.1f".format(r / 1_000)) + "k"
        else -> "1:${Math.round(r)}"
    }
}

/** Relative time for a draw's next execution: FINISHED / in Xh Ym / date. */
private fun drawRelativeTime(ts: Long?, now: Long): String {
    val diff = ((ts ?: 0) - now) / 1000
    if (diff < 0) return "FINISHED"
    if (diff < 86_400) {
        val h = diff / 3600
        val m = (diff % 3600) / 60
        return if (h > 0) "in ${h}h ${m}m" else "in ${m}m"
    }
    val d = Date(ts ?: 0)
    val month = SimpleDateFormat("MMMM", Locale.ENGLISH).format(d).lowercase()
    val day = SimpleDateFormat("d", Locale.ENGLISH).format(d)
    val year = SimpleDateFormat("yyyy", Locale.ENGLISH).format(d)
    return "$day $month/$year"
}

private fun monthDay(ts: Long?) =
    if (ts == null || ts <= 0) "—" else SimpleDateFormat("MMM d", Locale.ENGLISH).format(Date(ts))

private fun dateTime(ts: Long?) =
    if (ts == null || ts <= 0) "—" else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ENGLISH).format(Date(ts))

// ------------------------------------------------------------ draws list ----

@Composable
fun RaffleDrawsScreen(raffleId: Long, onClose: () -> Unit, onOpenDraw: (Long) -> Unit) {
    var draws by remember { mutableStateOf<List<TRaffleDraw>>(emptyList()) }
    var name by remember { mutableStateOf("Raffle") }
    var tickets by remember { mutableStateOf("") }
    val now = rememberNow()
    val avatar by Sdk.avatar.collectAsState()
    val myAvatar = avatar ?: Sdk.avatarUrl()

    LaunchedEffect(raffleId) {
        val raffle = runCatching { Smartico.api.getRaffles().find { it.id == raffleId } }.getOrNull()
        name = stripHtml(raffle?.name)
        tickets = "Tickets ${raffle?.current_tickets_count ?: 0}/${raffle?.max_tickets_count ?: 0}"
        // active draws first (soonest execution first); executed pushed to the end
        draws = raffle?.draws.orEmpty().sortedWith(
            compareBy({ if (it.isTerminal()) 1 else 0 }, { it.execution_ts ?: 0 }),
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                name.ifEmpty { "Raffle" },
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("✕", color = Muted, fontSize = 20.sp, modifier = Modifier.clickable { onClose() })
        }
        Meta("$tickets · ${draws.size} draws")
        Spacer(Modifier.height(10.dp))

        draws.forEach { d ->
            val dim = d.current_state != STATE_OPEN
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Card)
                    .clickable { onOpenDraw(d.id ?: 0) },
            ) {
                // the draw's background art fills the whole row
                if (!d.background_image_url.isNullOrEmpty()) {
                    AsyncImage(
                        model = d.background_image_url_mobile?.takeIf { it.isNotEmpty() } ?: d.background_image_url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize().alpha(if (dim) 0.25f else 1f),
                    )
                }
                // 🏆 grand / 🔁 recurring marker
                val typeIcon = when (d.execution_type) {
                    RaffleDrawTypeExecution.Grand.toLong() -> "🏆"
                    RaffleDrawTypeExecution.Recurring.toLong() -> "🔁"
                    else -> null
                }
                typeIcon?.let {
                    Text(it, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 4.dp))
                }
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = d.icon_url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2A2B45))
                            .alpha(if (dim) 0.5f else 1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stripHtml(d.name),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            when {
                                d.current_state == STATE_CANCELLED -> "CANCELLED"
                                d.current_state == STATE_WINNER_SELECTION -> "Drawing…"
                                drawRelativeTime(d.execution_ts, now) == "FINISHED" -> "FINISHED"
                                else -> "NEXT DRAW: ${drawRelativeTime(d.execution_ts, now)}"
                            },
                            color = Accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        if (stripHtml(d.description).isNotEmpty()) {
                            Text(
                                stripHtml(d.description),
                                color = Color(0xFFA9AACB),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        TicketChip("🪐", null, formatNumber(d.total_tickets_count))
                        TicketChip("🎟️", myAvatar, formatNumber(d.my_tickets_count))
                    }
                }
            }
        }
    }
}

/** Ticket counter chip: the pool (🪐) or the player's own (their avatar). */
@Composable
private fun TicketChip(fallbackIcon: String, avatarUrl: String?, value: String) = Row(
    Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xCC0F1020)).padding(horizontal = 6.dp, vertical = 3.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    if (!avatarUrl.isNullOrEmpty()) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            modifier = Modifier.size(18.dp).clip(CircleShape).background(Color(0xFF2A2B45)),
        )
    } else {
        Text(fallbackIcon, fontSize = 12.sp)
    }
    Spacer(Modifier.width(4.dp))
    Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
}

// ------------------------------------------------------------ draw detail ---

@Composable
fun DrawDetailScreen(raffleId: Long, drawId: Long, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var draw by remember { mutableStateOf<TRaffleDraw?>(null) }
    var optedIn by remember { mutableStateOf(false) }
    var optMsg by remember { mutableStateOf<String?>(null) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    var historyOpen by remember { mutableStateOf(false) }

    // history state
    var runs by remember { mutableStateOf<List<TRaffleDrawRun>>(emptyList()) }
    var histLoading by remember { mutableStateOf(true) }
    var wonByMe by remember { mutableStateOf(false) }
    var openRun by remember { mutableStateOf<Long?>(null) }
    val runData = remember { mutableStateMapOf<Long, TRaffleDraw?>() } // null value = load failed
    val rowMsg = remember { mutableStateMapOf<Long, String>() }

    LaunchedEffect(raffleId, drawId) {
        draw = runCatching {
            Smartico.api.getRaffles().find { it.id == raffleId }?.draws?.find { it.id == drawId }
        }.getOrNull()
        optedIn = draw?.user_opted_in == true
        runs = runCatching {
            Smartico.api.getRaffleDrawRunsHistory(raffle_id = raffleId, draw_id = drawId)
                .sortedByDescending { it.execution_ts ?: 0 }
        }.getOrDefault(emptyList())
        histLoading = false
    }

    val d = draw
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stripHtml(d?.name).ifEmpty { "Draw" },
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2A2B45))
                    .clickable { historyOpen = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    "History" + if (histLoading) "" else " (${runs.size})",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text("✕", color = Muted, fontSize = 20.sp, modifier = Modifier.clickable { onClose() })
        }
        if (d == null) { Loading(); return@Column }

        val total = d.total_tickets_count ?: 0
        val my = d.my_tickets_count ?: 0
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Badge("🪐 ${formatNumber(total)} total", Muted)
            Badge("🎟️ ${formatNumber(my)} mine", Accent)
        }

        Spacer(Modifier.height(10.dp))
        when {
            d.requires_optin != true -> Meta("Automatic entry — no opt-in needed")
            optedIn -> Badge("You're in ✓", Good)
            else -> {
                ActionButton("Opt in to this draw", onResult = { optMsg = it }) {
                    val r = Smartico.api.requestRaffleOptin(
                        raffle_id = raffleId,
                        draw_id = drawId,
                        raffle_run_id = d.run_id ?: 0,
                    )
                    if (r.err_code == 0L) { optedIn = true; "Opted in ✓" }
                    else r.err_message ?: "Opt-in failed (${r.err_code})"
                }
                optMsg?.let { Spacer(Modifier.height(4.dp)); Meta(it) }
            }
        }

        Spacer(Modifier.height(12.dp))
        Section("Prizes (${d.prizes?.size ?: 0})")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            val prizes = d.prizes.orEmpty().sortedBy { it.priority ?: 0 }
            if (prizes.isEmpty()) Meta("No prizes in this draw.")
            prizes.forEach { p -> PrizeCard(p, total, my, expanded) }
            Spacer(Modifier.height(24.dp))
        }
    }

    // ---- history bottom sheet ----
    if (historyOpen) {
        androidx.activity.compose.BackHandler { historyOpen = false }
        Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable { historyOpen = false }) {
            Surface(
                color = Bg,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    // consume taps so touching the sheet doesn't close it
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {},
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Draw history (${runs.size})",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Text("✕", color = Muted, fontSize = 20.sp, modifier = Modifier.clickable { historyOpen = false })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Meta("Won by me")
                        Spacer(Modifier.width(6.dp))
                        Switch(
                            checked = wonByMe,
                            onCheckedChange = { wonByMe = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = Accent),
                        )
                    }
                    val shown = if (wonByMe) runs.filter { it.is_winner == true } else runs
                    when {
                        histLoading -> Loading()
                        shown.isEmpty() -> Meta("There are no records available.")
                        else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                            shown.forEach { run ->
                                val runId = run.run_id ?: 0
                                RunRow(
                                    run = run,
                                    open = openRun == runId,
                                    detail = runData[runId],
                                    loaded = runData.containsKey(runId),
                                    message = rowMsg[runId],
                                    onToggle = {
                                        openRun = if (openRun == runId) null else runId
                                        if (!runData.containsKey(runId)) {
                                            scope.launch {
                                                runData[runId] = runCatching {
                                                    Smartico.api.getRaffleDrawRun(raffle_id = raffleId, run_id = runId)
                                                }.getOrNull()
                                            }
                                        }
                                    },
                                    onClaim = {
                                        scope.launch {
                                            rowMsg[runId] = "Loading…"
                                            rowMsg[runId] = runCatching { claimRun(raffleId, run) }
                                                .getOrElse { "Error: ${it.message}" }
                                        }
                                    },
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * One prize: locked until the pool and the player have enough tickets; tapping
 * a locked prize expands the "how to unlock" progress bars.
 */
@Composable
private fun PrizeCard(p: TRafflePrize, total: Long, my: Long, expanded: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean>) {
    val minTotal = p.min_required_total_tickets ?: 0
    val minUser = p.min_required_tickets_for_user ?: 0
    val available = total >= minTotal && my >= minUser
    val canExpand = !available || minTotal > 0 || minUser > 0
    val key = p.id.orEmpty()
    val open = expanded[key] == true

    Surface(
        color = Card,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clickable(enabled = canExpand) { expanded[key] = !open },
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    AsyncImage(
                        model = p.image_url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2A2B45))
                            .alpha(if (available) 1f else 0.4f),
                    )
                    if (!available) Text("🔒", fontSize = 18.sp, modifier = Modifier.align(Alignment.Center))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stripHtml(p.name),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    when {
                        available && total > 0 && my > 0 ->
                            Text("You have a ${numbersToRatio(my, total)} chance to win", color = Good, fontSize = 11.sp)
                        !available ->
                            Text("Locked — tap for how to unlock", color = Muted, fontSize = 11.sp)
                    }
                }
                val mult = (p.prizes_per_run_actual ?: 0.0).toInt()
                if (mult > 1) {
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(Accent).padding(horizontal = 8.dp, vertical = 3.dp),
                    ) { Text("${mult}x", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
            }

            if (open) {
                Spacer(Modifier.height(10.dp))
                if (minTotal > total) {
                    Text(
                        "Help gather ${formatNumber(minTotal - total)} more tickets (all users) to unlock",
                        color = Color(0xFFFF8F8F),
                        fontSize = 11.sp,
                    )
                    UnlockProgress("🪐", total, minTotal)
                    Spacer(Modifier.height(8.dp))
                }
                if (minUser > 0) {
                    Text(
                        if (my >= minUser) "Your tickets in this draw"
                        else "Collect ${formatNumber(minUser - my)} more tickets personally to unlock",
                        color = if (my >= minUser) Color(0xFFC9C9E0) else Color(0xFFFF8F8F),
                        fontSize = 11.sp,
                    )
                    UnlockProgress("🎟️", my, minUser)
                }
                (p.add_one_prize_per_each_x_tickets ?: 0).takeIf { it > 0 }?.let {
                    Spacer(Modifier.height(6.dp))
                    Meta("Every ${formatNumber(it)} extra tickets in the draw adds one more prize.")
                }
            }
        }
    }
}

@Composable
private fun UnlockProgress(icon: String, have: Long, need: Long) = Row(
    Modifier.fillMaxWidth().padding(top = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(icon, fontSize = 14.sp)
    Spacer(Modifier.width(8.dp))
    Box(Modifier.weight(1f)) {
        val pct = if (need > 0) (have * 100.0 / need).coerceAtMost(100.0) else 100.0
        Progress(pct, completed = pct >= 100)
    }
    Spacer(Modifier.width(8.dp))
    Text(formatNumber(need), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
}

/** One past run: dates, Claim when a prize awaits, expandable winners list. */
@Composable
private fun RunRow(
    run: TRaffleDrawRun,
    open: Boolean,
    detail: TRaffleDraw?,
    loaded: Boolean,
    message: String?,
    onToggle: () -> Unit,
    onClaim: () -> Unit,
) = Surface(color = Card, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(onClick = onToggle)) {
                Text(
                    (if (run.is_winner == true) "🏆 " else "") + stripHtml(run.name),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Meta("${monthDay(run.ticket_start_ts)} – ${monthDay(run.execution_ts)}")
                Meta(dateTime(run.execution_ts))
            }
            if (run.has_unclaimed_prize == true) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Accent)
                        .clickable(onClick = onClaim)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text("Claim", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(8.dp))
            }
            Text(
                if (open) "▲ hide" else "▼ winners",
                color = Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onToggle).padding(vertical = 8.dp),
            )
        }
        message?.let { Spacer(Modifier.height(4.dp)); Meta(it) }
        if (open) {
            Spacer(Modifier.height(8.dp))
            when {
                !loaded -> Meta("loading…")
                detail == null -> Meta("Couldn't load winners.")
                else -> {
                    val lines = detail.prizes.orEmpty().flatMap { p -> p.winners.orEmpty().map { w -> w to p } }
                    if (lines.isEmpty()) {
                        Meta("No winners for this run.")
                    } else {
                        lines.forEach { (w, p) ->
                            Row(
                                Modifier.fillMaxWidth().padding(top = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AsyncImage(
                                    model = w.avatar_url,
                                    contentDescription = null,
                                    modifier = Modifier.size(26.dp).clip(CircleShape).background(Color(0xFF2A2B45)),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stripHtml(w.username).ifEmpty { "Player" },
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (!p.image_url.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = p.image_url,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    stripHtml(p.name),
                                    color = Color(0xFFC9C9E0),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Claim flow, as in RN: fetch the full run, find this user's unclaimed prize
 * (a winner row with raf_won_id and no claimed_date) and claim it; otherwise
 * report how many winners the run had.
 */
private suspend fun claimRun(raffleId: Long, run: TRaffleDrawRun): String {
    val full = Smartico.api.getRaffleDrawRun(raffle_id = raffleId, run_id = run.run_id ?: 0)
    val wonId = full.prizes.orEmpty()
        .flatMap { it.winners.orEmpty() }
        .firstOrNull { it.raf_won_id != null && it.claimed_date == null }
        ?.raf_won_id
    return if (run.has_unclaimed_prize == true && wonId != null) {
        val r = Smartico.api.claimRafflePrize(won_id = wonId)
        if ((r.errorCode ?: 0.0) == 0.0) "Claimed ✓" else r.errorMessage ?: "Claim failed (${r.errorCode})"
    } else {
        val n = full.prizes.orEmpty().sumOf { it.winners?.size ?: 0 }
        "$n winner(s) · nothing to claim"
    }
}
