package ai.smartico.fakecasino

import ai.smartico.publicapi.types.TTournament
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import ai.smartico.publicapi.types.TMiniGameTemplate
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import ai.smartico.publicapi.types.TMissionOrBadge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import ai.smartico.publicapi.Smartico
import ai.smartico.publicapi.api.buyStoreItem
import ai.smartico.publicapi.api.claimBonus
import ai.smartico.publicapi.api.getBonuses
import ai.smartico.publicapi.api.getCurrentLevel
import ai.smartico.publicapi.api.getInboxMessageBody
import ai.smartico.publicapi.api.getInboxMessages
import ai.smartico.publicapi.api.getLeaderBoard
import ai.smartico.publicapi.api.getLevels
import ai.smartico.publicapi.api.getMiniGames
import ai.smartico.publicapi.api.getMissions
import ai.smartico.publicapi.api.getRaffles
import ai.smartico.publicapi.api.getStoreItems
import ai.smartico.publicapi.api.getTournamentsList
import ai.smartico.publicapi.api.jackpotGet
import ai.smartico.publicapi.api.jackpotOptIn
import ai.smartico.publicapi.api.markInboxMessageAsRead
import ai.smartico.publicapi.api.registerInTournament
import ai.smartico.publicapi.api.requestMissionClaimReward
import ai.smartico.publicapi.api.requestMissionOptIn
import ai.smartico.publicapi.types.LeaderBoardPeriodType
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

@Composable
fun ProfileScreen(nav: NavHostController) {
    val props by Sdk.props.collectAsState()
    val identified by Sdk.identified.collectAsState()
    var editName by remember { mutableStateOf(false) }

    if (editName) NameDialog(onDismiss = { editName = false })

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Profile", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val avatar by Sdk.avatar.collectAsState()
                Thumb(avatar ?: Sdk.avatarUrl(), 56)
                Spacer(Modifier.width(12.dp))
                Column {
                    Row {
                        Title(Sdk.displayName())
                        Text("  ✎", color = Accent, modifier = Modifier.clickable { editName = true })
                    }
                    Meta(if (identified) "ICE · connected ✓" else "connecting…")
                    Meta(Sdk.extUserId)
                    Text("Change avatar ›", color = Accent, fontSize = 12.sp, modifier = Modifier.clickable { nav.navigate(Routes.AVATAR) })
                }
            }
        }
        Card {
            Title("Balances")
            Spacer(Modifier.height(6.dp))
            Meta("🟡 Points: ${Sdk.prop("ach_points_balance") ?: "—"}   ⭐ ${Sdk.prop("ach_level_current") ?: "—"}")
            Meta("💎 Gems: ${Sdk.prop("ach_gems_balance") ?: "—"}   🔷 Diamonds: ${Sdk.prop("ach_diamonds_balance") ?: "—"}")
            Meta("✉️ Unread inbox: ${Sdk.prop("core_inbox_unread_count") ?: "—"}  ·  props: ${props.size}")
        }
        Card {
            Title("More")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("VIP") { nav.navigate(Routes.VIP); "" }
                ActionButton("Leaderboard") { nav.navigate(Routes.LEADERBOARD); "" }
                ActionButton("Store") { nav.navigate(Routes.STORE); "" }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Jackpots") { nav.navigate(Routes.JACKPOTS); "" }
                ActionButton("Raffles") { nav.navigate(Routes.RAFFLES); "" }
            }
        }
        BadgesSection()
    }
}

@Composable
fun MissionsScreen() {
    // The store owns the list: server pushes refresh it, so progress moves
    // while this screen is open and the visible list is never dropped for a
    // spinner mid-refresh.
    val missions by Store.missions.collectAsState()
    val loaded by Store.loaded.collectAsState()
    LaunchedEffect(Unit) { Store.refreshMissions() }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            "Missions",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        when {
            missions.isEmpty() && !loaded -> Loading()
            missions.isEmpty() -> Text("Nothing here yet.", color = Muted)
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(missions.size, key = { missions[it].id ?: it }) { i ->
                    MissionCard(missions[i]) { Store.refreshMissions() }
                }
            }
        }
    }
}

/**
 * One mission. Collapsed it shows identity + progress; tapping expands the
 * tasks, related games, unlock hint and live timer — the same card the RN demo
 * renders.
 */
@Composable
private fun MissionCard(m: TMissionOrBadge, onChanged: () -> Unit) {
    var open by remember(m.id) { mutableStateOf(false) }
    var note by remember(m.id) { mutableStateOf<String?>(null) }

    val locked = m.is_locked == true
    val completed = m.is_completed == true
    val status = missionStatus(m)
    val canOptin = m.is_requires_optin == true && m.is_opted_in != true && !locked
    val canClaim = completed && m.requires_prize_claim == true &&
        m.prize_claimed_date_ts == null && m.ach_completed_id != null

    Surface(
        color = Card,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable { open = !open },
    ) {
        Column(Modifier.padding(14.dp)) {
            Row {
                Thumb(m.image)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stripHtml(m.name),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        status?.let { (label, tone) ->
                            Spacer(Modifier.width(6.dp))
                            Badge(label, tone)
                        }
                    }
                    if (stripHtml(m.sub_header).isNotEmpty()) Meta(stripHtml(m.sub_header))
                    if (stripHtml(m.description).isNotEmpty()) {
                        Text(
                            stripHtml(m.description),
                            color = Muted,
                            fontSize = 12.sp,
                            maxLines = if (open) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (stripHtml(m.reward).isNotEmpty()) {
                        Text("Reward: ${stripHtml(m.reward)}", color = Gold, fontSize = 12.sp)
                    }
                }
            }

            // always drawn, including at 0% — an empty bar still tells the
            // player this mission is measured
            val progress = m.progress ?: 0.0
            run {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Meta("Progress")
                    Text(
                        "${progress.toInt()}%",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Progress(progress, completed = completed)
            }

            if (open) {
                val tasks = m.tasks.orEmpty()
                if (tasks.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Section("Tasks")
                    for (t in tasks) {
                        val done = t.is_completed == true
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (done) "✔" else "○", color = if (done) Good else Muted, fontSize = 13.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stripHtml(t.name),
                                color = if (done) Muted else Color.White,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            t.execution_count_expected?.let { exp ->
                                Spacer(Modifier.width(8.dp))
                                Meta("${(t.execution_count_actual ?: 0.0).toInt()}/${exp.toInt()}")
                            }
                            if ((t.points_reward ?: 0) > 0) {
                                Spacer(Modifier.width(8.dp))
                                Text("${t.points_reward} pts", color = Gold, fontSize = 11.sp)
                            }
                        }
                    }
                }

                val games = m.related_games.orEmpty()
                    .mapNotNull { (it.game_public_meta as? JsonObject)?.get("image")?.jsonPrimitive?.contentOrNull }
                if (games.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Section("Related games")
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) { for (img in games) Thumb(img, size = 52) }
                }

                if (locked && stripHtml(m.unlock_mission_description).isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Section("How do I unlock this?")
                    Meta(stripHtml(m.unlock_mission_description))
                }

                val limit = m.time_limit_ms ?: 0
                if (limit > 0 && m.dt_start != null) {
                    // ticks only while this card is open
                    var now by remember { mutableStateOf(System.currentTimeMillis()) }
                    LaunchedEffect(m.id) {
                        while (true) { now = System.currentTimeMillis(); delay(1000) }
                    }
                    countdown(m.dt_start!! + limit, now)?.let {
                        Spacer(Modifier.height(8.dp))
                        Meta("Mission duration: $it")
                    }
                }
            }

            if (canOptin || canClaim || (m.cta_action != null && m.cta_text != null)) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canOptin) {
                        ActionButton("Opt-in", onResult = { note = it; onChanged() }) {
                            val r = Smartico.api.requestMissionOptIn(m.id ?: 0)
                            if (r.err_code == 0L) "Opted in ✓" else r.err_message ?: "Failed (${r.err_code})"
                        }
                    } else if (m.cta_action != null && m.cta_text != null) {
                        ActionButton(stripHtml(m.cta_text)) { Smartico.dp(m.cta_action!!); "" }
                    }
                    if (canClaim) {
                        ActionButton(m.claim_button_title ?: "Claim reward", onResult = { note = it; onChanged() }) {
                            val r = Smartico.api.requestMissionClaimReward(m.id ?: 0, m.ach_completed_id!!)
                            if (r.err_code == 0L) "Claimed ✓" else r.err_message ?: "Failed (${r.err_code})"
                        }
                    }
                }
            }
            note?.takeIf { it.isNotEmpty() }?.let { Spacer(Modifier.height(6.dp)); Meta(it) }
        }
    }
}

@Composable
fun TournamentsScreen(nav: NavHostController) {
    // Tiles, like the RN demo: artwork carries the tournament, the badge carries
    // its state, and the line underneath counts down while it is live.
    val tournaments by Store.tournaments.collectAsState()
    val loaded by Store.loaded.collectAsState()
    LaunchedEffect(Unit) { Store.refreshTournaments() }
    val now = rememberNow()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text(
            "Tournaments",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        when {
            tournaments.isEmpty() && !loaded -> Loading()
            tournaments.isEmpty() -> Text("Nothing here yet.", color = Muted)
            else -> tournaments.chunked(3).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { t ->
                        TournamentTile(t, now, Modifier.weight(1f)) {
                            nav.navigate(Routes.tournament(t.instance_id ?: 0))
                        }
                    }
                    // keeps the last row's tiles the same width as a full one
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TournamentTile(t: TTournament, now: Long, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val status = tournamentStatus(t.is_cancelled, t.is_finished, t.is_in_progress, t.is_upcoming)
    val live = t.is_in_progress == true || t.is_upcoming == true
    // a running tournament counts down to its end, a gathering one to its start
    val target = if (t.is_in_progress == true) t.end_time else t.start_time

    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Card)
            .clickable { onClick() },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color(0xFF2A2B45))) {
            AsyncImage(
                model = t.image1,
                contentDescription = stripHtml(t.name),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            status?.let { (label, tone) ->
                Box(Modifier.align(Alignment.TopStart).padding(5.dp)) { Badge(label, tone) }
            }
            if (t.is_user_registered == true) {
                Text(
                    "✓",
                    color = Good,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
                )
            }
        }
        Column(Modifier.padding(8.dp)) {
            Text(
                stripHtml(t.name),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (live && target != null) countdownShort(target, now) else status?.first ?: "—",
                color = if (live) Accent else Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}


@Composable
fun MiniGamesScreen(nav: NavHostController) {
    val props by Sdk.props.collectAsState()
    val points = (props["ach_points_balance"] as? JsonPrimitive)?.doubleOrNull ?: 0.0

    ApiList(
        "Mini-games",
        // MatchX and quizzes are separate experiences, not SAW mini-games —
        // the widget renders them differently, so they stay out of this list.
        load = { Smartico.api.getMiniGames().filterNot { it.saw_game_type in EXCLUDED_MINIGAMES } },
    ) { g ->
        Card {
            // Custom mini-games ship their art as promo_image and leave
            // `thumbnail` empty — that's why those cards had no picture. The
            // art is a wide banner, so it goes above the text, not in a square.
            val art = g.promo_image?.takeIf { it.isNotEmpty() } ?: g.thumbnail
            if (!art.isNullOrEmpty()) {
                AsyncImage(
                    model = art,
                    contentDescription = stripHtml(g.name),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.height(10.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stripHtml(g.name),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                g.saw_game_type?.let {
                    Spacer(Modifier.width(6.dp))
                    Badge(it, Accent)
                }
            }
            if (stripHtml(g.promo_text).isNotEmpty()) Meta(stripHtml(g.promo_text))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    miniGameCost(g),
                    color = Gold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (canPlayMiniGame(g, points)) {
                    ActionButton("Play") {
                        nav.navigate(Routes.widget(miniGameDp(g)))
                        ""
                    }
                } else {
                    Meta("Not enough balance")
                }
            }
        }
    }
}

/** Separate experiences the demo does not list (RN parity). */
private val EXCLUDED_MINIGAMES = setOf("matchx", "quiz")

/** Lootbox templates open their custom section, not the SAW game. */
private val LOOTBOX_MINIGAMES = setOf("lootbox_weekdays", "lootbox_calendar_days")

private fun miniGameDp(g: TMiniGameTemplate): String =
    if (g.saw_game_type in LOOTBOX_MINIGAMES) {
        "dp:gf_section&id=${g.custom_section_id}&standalone=true"
    } else {
        "dp:gf_saw&id=${g.id}&standalone=true"
    }

private fun miniGameCost(g: TMiniGameTemplate): String = when (g.saw_buyin_type) {
    "points" -> "🟡 ${g.buyin_cost_points ?: 0} points"
    "gems" -> "💎 ${(g.buyin_cost_gems ?: 0.0).toInt()} gems"
    "diamonds" -> "🔷 ${(g.buyin_cost_diamonds ?: 0.0).toInt()} diamonds"
    "spins" -> "🎟️ ${g.spin_count ?: 0} attempts"
    else -> "Free"
}

private fun canPlayMiniGame(g: TMiniGameTemplate, points: Double): Boolean = when (g.saw_buyin_type) {
    "spins" -> (g.spin_count ?: 0) > 0
    "points" -> (g.buyin_cost_points ?: 0) <= points
    else -> true
}



@Composable
fun LevelsScreen() {
    ApiList("Levels", load = { Smartico.api.getLevels().sortedBy { it.required_points ?: 0 } }) { l ->
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumb(l.image, 44)
                Spacer(Modifier.width(12.dp))
                Column {
                    Title("#${l.ordinal_position} ${stripHtml(l.name)}")
                    Meta("${l.required_points} pts")
                }
            }
        }
    }
}

@Composable
fun LeaderboardScreen() {
    ApiList(
        "Leaderboard",
        load = { Smartico.api.getLeaderBoard(LeaderBoardPeriodType.DAILY.toLong())?.users.orEmpty() },
    ) { u ->
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#${u.position}", color = Accent, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Thumb(u.avatar_url, 32)
                Spacer(Modifier.width(10.dp))
                Column {
                    Title(u.public_username ?: "player" + (if (u.is_me == true) "  (you)" else ""))
                    Meta("${u.points} pts")
                }
            }
        }
    }
}

@Composable
fun JackpotsScreen() {
    var reload by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf<String?>(null) }
    ApiList("Jackpots", load = { Smartico.api.jackpotGet() }, reloadKey = reload) { j ->
        Card {
            Title(stripHtml(j.jp_public_meta?.name))
            Meta("pot ${j.pot?.current_pot_amount_user_currency ?: 0} ${j.user_currency ?: ""} · ${j.registration_count ?: 0} players")
            if (j.is_opted_in != true) {
                Spacer(Modifier.height(6.dp))
                ActionButton("Join", onResult = { note = it; reload++ }) {
                    val r = Smartico.api.jackpotOptIn(j.jp_template_id ?: 0)
                    if (r.errCode == 0L) "Joined ✓" else r.errMsg ?: "err ${r.errCode}"
                }
            } else {
                Meta("you're in ✓")
            }
            note?.let { Meta(it) }
        }
    }
}

@Composable
fun RafflesScreen(nav: NavHostController) {
    ApiList("Raffles", load = { Smartico.api.getRaffles() }) { r ->
        Card {
            Column(Modifier.fillMaxWidth().clickable { nav.navigate(Routes.raffle(r.id ?: 0)) }) {
                // the raffle banner is a wide strip (890:193), like in RN
                val banner = r.image_url_mobile?.takeIf { it.isNotEmpty() } ?: r.image_url
                if (!banner.isNullOrEmpty()) {
                    AsyncImage(
                        model = banner,
                        contentDescription = stripHtml(r.name),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(890f / 193f)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Title(stripHtml(r.name))
                if (stripHtml(r.description).isNotEmpty()) {
                    Text(
                        stripHtml(r.description),
                        color = Muted,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Badge("${r.draws?.size ?: 0} draws", Accent)
                    Spacer(Modifier.width(8.dp))
                    Meta("Tickets: ${r.current_tickets_count ?: 0}/${r.max_tickets_count ?: 0}")
                    Spacer(Modifier.weight(1f))
                    Text("Draws ›", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun StoreScreen() {
    var reload by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf<String?>(null) }
    ApiList("Store", load = { Smartico.api.getStoreItems() }, reloadKey = reload) { item ->
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumb(item.image, 48)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Title(stripHtml(item.name))
                    Meta("${item.price?.toInt() ?: 0} ${item.purchase_type} · ${item.type}")
                    if (item.can_buy == true) {
                        Spacer(Modifier.height(6.dp))
                        ActionButton("Buy", onResult = { note = it; reload++ }) {
                            val r = Smartico.api.buyStoreItem(item.id ?: 0)
                            if (r.err_code == 0L) "Bought ✓" else r.err_message ?: "err ${r.err_code}"
                        }
                    }
                    note?.let { Meta(it) }
                }
            }
        }
    }
}
