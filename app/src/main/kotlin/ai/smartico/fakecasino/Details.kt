package ai.smartico.fakecasino

import androidx.compose.foundation.horizontalScroll
import coil.compose.AsyncImage
import ai.smartico.publicapi.types.TTournamentPlayer
import ai.smartico.publicapi.types.TTournamentPrize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.ui.layout.ContentScale
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import ai.smartico.publicapi.api.requestMissionOptIn
import ai.smartico.publicapi.types.TMissionOrBadge
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Surface
import androidx.compose.ui.text.style.TextOverflow
import ai.smartico.publicapi.Smartico
import ai.smartico.publicapi.api.claimRafflePrize
import ai.smartico.publicapi.api.getRaffleDrawRunsHistory
import ai.smartico.publicapi.api.getRaffles
import ai.smartico.publicapi.api.getMissions
import ai.smartico.publicapi.api.getRelatedItemsForGame
import ai.smartico.publicapi.api.getTournamentsList
import ai.smartico.publicapi.api.getTournamentInstanceInfo
import ai.smartico.publicapi.api.registerInTournament
import ai.smartico.publicapi.api.requestRaffleOptin
import ai.smartico.publicapi.types.TRaffleDraw
import ai.smartico.publicapi.types.TTournamentDetailed
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private fun JsonObject?.str(key: String): String? = (this?.get(key) as? JsonPrimitive)?.content

/**
 * Tournament detail with the three tabs the web app has: leaderboard, prizes
 * and the description.
 */
@Composable
fun TournamentDetailScreen(instanceId: Long, onClose: () -> Unit) {
    var detail by remember { mutableStateOf<TTournamentDetailed?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(1) }
    var note by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(instanceId, reload) {
        runCatching { Smartico.api.getTournamentInstanceInfo(instanceId) }
            .onSuccess { detail = it; error = null }
            .onFailure { e ->
                // Campaign links often carry tournament_id where an instance_id
                // is expected — resolve it through the list and retry.
                val match = Store.tournaments.value
                    .firstOrNull { it.tournament_id == instanceId && it.instance_id != instanceId }
                if (match?.instance_id != null) {
                    runCatching { Smartico.api.getTournamentInstanceInfo(match.instance_id!!) }
                        .onSuccess { detail = it }
                        .onFailure { error = "Tournament not found or no longer active." }
                } else {
                    error = "Tournament not found or no longer active."
                }
            }
    }

    val t = detail
    val now = rememberNow(active = t?.is_in_progress == true || t?.is_upcoming == true)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stripHtml(t?.name).ifEmpty { "Tournament" },
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            tournamentStatus(t?.is_cancelled, t?.is_finished, t?.is_in_progress, t?.is_upcoming)
                ?.let { (label, tone) -> Badge(label, tone); Spacer(Modifier.width(8.dp)) }
            Text("✕", color = Muted, fontSize = 20.sp, modifier = Modifier.clickable { onClose() })
        }
        error?.let { Meta(it); return@Column }
        if (t == null) { Loading(); return@Column }

        val target = if (t.is_in_progress == true) t.end_time else t.start_time
        if ((t.is_in_progress == true || t.is_upcoming == true) && target != null) {
            Spacer(Modifier.height(10.dp))
            Section(tournamentTimerLabel(t))
            Text(
                countdown(target ?: 0L, now) ?: "—",
                color = Accent,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Meta(dateRange(t.start_time, t.end_time))

        Spacer(Modifier.height(10.dp))
        Card {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoCell("Prize", stripHtml(t.prize_pool_short).ifEmpty { "No prize" }, Modifier.weight(1f))
                InfoCell("Registered", "${t.registration_count ?: 0}/${t.players_max_count ?: "∞"}", Modifier.weight(1f))
                InfoCell("Buy in", buyIn(t), Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(10.dp))
        if (t.is_user_registered == true) {
            Meta("Registered ✓")
        } else if (t.is_can_register == true) {
            ActionButton("Join Tournament", onResult = { note = it; reload++ }) {
                val r = Smartico.api.registerInTournament(instanceId)
                if (r.err_code == 0L) "Registered ✓" else registerError(r.err_code ?: -1L, t.segment_dont_match_message)
            }
        } else {
            Meta("Registration closed")
        }
        note?.let { Spacer(Modifier.height(6.dp)); Meta(it) }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Leaderboard", "Prizes", "More Info").forEachIndexed { i, label ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (tab == i) Accent else Card)
                        .clickable { tab = i; page = 1 }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) { Text(label, color = if (tab == i) Color.White else Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(12.dp))

        when (tab) {
            0 -> LeaderboardTab(t.players.orEmpty(), page) { page = it }
            1 -> PrizesTab(t.prizes.orEmpty(), page) { page = it }
            else -> Meta(stripHtml(t.description).ifEmpty { "No description." })
        }

        val games = t.related_games.orEmpty()
        if (games.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Section("Eligible games (${games.size})")
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (g in games) {
                    val meta = g.game_public_meta as? JsonObject
                    Column(Modifier.width(72.dp)) {
                        Thumb(meta?.get("image")?.jsonPrimitive?.contentOrNull, 72)
                        Text(
                            meta?.get("name")?.jsonPrimitive?.contentOrNull ?: "Game",
                            color = Muted,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** registerInTournament err_code → a sentence a player can act on. */
private fun registerError(code: Long, segmentMessage: String?): String = when (code) {
    30002L -> "Not enough balance to register."
    30003L -> "Registration is not open right now."
    30004L -> "You are already registered."
    30005L -> segmentMessage ?: "You don't match the conditions for this tournament."
    30008L -> "Tournament is full."
    else -> "Registration failed (code $code)."
}

@Composable
private fun InfoCell(label: String, value: String, modifier: Modifier = Modifier) = Column(modifier) {
    Section(label)
    Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

private const val PER_PAGE = 8

/** Top three on a podium, everyone else in a ranked table — as in the RN demo. */
@Composable
private fun LeaderboardTab(players: List<TTournamentPlayer>, page: Int, onPage: (Int) -> Unit) {
    if (players.isEmpty()) {
        Meta("No players registered.")
        return
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        PodiumCell(players.getOrNull(1), 2, Modifier.weight(1f))
        PodiumCell(players.getOrNull(0), 1, Modifier.weight(1f), big = true)
        PodiumCell(players.getOrNull(2), 3, Modifier.weight(1f))
    }

    val rest = players.drop(3)
    if (rest.isEmpty()) return
    val pages = maxOf(1, (rest.size + PER_PAGE - 1) / PER_PAGE)
    val slice = rest.drop((page - 1) * PER_PAGE).take(PER_PAGE)
    // the player always sees their own row, even when it is on another page
    val me = players.firstOrNull { it.is_me == true }
    val appendMe = if (me != null && players.indexOf(me) >= 3 && slice.none { it.is_me == true }) me else null

    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text("RANK", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp))
        Text("PLAYER", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text("SCORE", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
    for (p in slice) PlayerRow(p)
    appendMe?.let { PlayerRow(it) }
    if (pages > 1) Pager(page, pages, onPage)
}

@Composable
private fun PodiumCell(p: TTournamentPlayer?, place: Int, modifier: Modifier = Modifier, big: Boolean = false) {
    if (p == null) {
        Spacer(modifier)
        return
    }
    val size = if (big) 64 else 48
    Column(modifier.padding(bottom = if (big) 12.dp else 0.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            PlayerAvatar(p.avatar_url, size)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(Accent)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) { Text("$place", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        }
        Text(
            stripHtml(p.public_username).ifEmpty { p.user_ext_id.orEmpty() },
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text("${(p.scores ?: 0.0).toInt()}", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PlayerRow(p: TTournamentPlayer) = Row(
    Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(if (p.is_me == true) Accent.copy(alpha = 0.18f) else Color.Transparent)
        .padding(vertical = 5.dp, horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text("#${p.position ?: 0}", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
    PlayerAvatar(p.avatar_url, 28)
    Spacer(Modifier.width(8.dp))
    Text(
        stripHtml(p.public_username).ifEmpty { p.user_ext_id.orEmpty() } + if (p.is_me == true) " (you)" else "",
        color = Color.White,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
    Text("${(p.scores ?: 0.0).toInt()}", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun PlayerAvatar(url: String?, size: Int) {
    if (!url.isNullOrEmpty()) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size.dp).clip(CircleShape).background(Color(0xFF2A2B45)),
        )
    } else {
        Box(
            Modifier.size(size.dp).clip(CircleShape).background(Color(0xFF2A2B45)),
            contentAlignment = Alignment.Center,
        ) { Text("👤", fontSize = (size * 0.4).sp) }
    }
}

@Composable
private fun PrizesTab(prizes: List<TTournamentPrize>, page: Int, onPage: (Int) -> Unit) {
    if (prizes.isEmpty()) {
        Meta("No prizes added.")
        return
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        PrizePodium(prizes.getOrNull(1), 2, Modifier.weight(1f))
        PrizePodium(prizes.getOrNull(0), 1, Modifier.weight(1f))
        PrizePodium(prizes.getOrNull(2), 3, Modifier.weight(1f))
    }
    val rest = prizes.drop(3)
    if (rest.isEmpty()) return
    val pages = maxOf(1, (rest.size + PER_PAGE - 1) / PER_PAGE)
    Spacer(Modifier.height(10.dp))
    for (pr in rest.drop((page - 1) * PER_PAGE).take(PER_PAGE)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(prizeRank(pr), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(70.dp))
            Text(
                stripHtml(pr.name),
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(prizeValue(pr), color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
    if (pages > 1) Pager(page, pages, onPage)
}

@Composable
private fun PrizePodium(pr: TTournamentPrize?, place: Int, modifier: Modifier = Modifier) {
    if (pr == null) {
        Spacer(modifier)
        return
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Thumb(pr.image_url, 56)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(Accent)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) { Text("$place", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        }
        Text(
            stripHtml(pr.name),
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun prizeRank(pr: TTournamentPrize): String =
    if (pr.place_from == pr.place_to) "#${(pr.place_from ?: 0.0).toInt()}"
    else "#${(pr.place_from ?: 0.0).toInt()}–${(pr.place_to ?: 0.0).toInt()}"

private fun prizeValue(pr: TTournamentPrize): String =
    if (pr.type == "POINTS_ADD") "${(pr.points ?: 0)} pts" else "1 free spin"

@Composable
private fun Pager(page: Int, pages: Int, onPage: (Int) -> Unit) = Row(
    Modifier.fillMaxWidth().padding(top = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
) {
    for (n in 1..pages) {
        Box(
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (n == page) Accent else Card)
                .clickable(enabled = n != page) { onPage(n) }
                .padding(horizontal = 9.dp, vertical = 4.dp),
        ) { Text("$n", color = if (n == page) Color.White else Muted, fontSize = 11.sp) }
    }
}


private fun buyIn(t: TTournamentDetailed): String = when {
    (t.registration_cost_points ?: 0) > 0 -> "${t.registration_cost_points} pts"
    (t.registration_cost_gems ?: 0.0) > 0 -> "${t.registration_cost_gems?.toInt()} gems"
    (t.registration_cost_diamonds ?: 0.0) > 0 -> "${t.registration_cost_diamonds?.toInt()} 💎"
    else -> "Free"
}

/** Raffle → its draws, with opt-in and prize claim, plus the run history. */

/**
 * In-game side panel: the missions and tournaments tied to THIS slot, so the
 * player can watch progress while spinning (the web app's GamePageSidebar).
 */
@Composable
fun GameSidePanel(extId: String, onClose: () -> Unit) {
    // Which items belong to this game is fixed per game; the progress inside
    // them is not, so the ids are fetched once and the live numbers come from
    // the store — a spin updates this panel while it stays open.
    var achIds by remember(extId) { mutableStateOf(emptySet<Long>()) }
    var tourIds by remember(extId) { mutableStateOf(emptySet<Long>()) }
    var loaded by remember(extId) { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf<String?>(null) }
    val allMissions by Store.missions.collectAsState()
    val allTournaments by Store.tournaments.collectAsState()

    LaunchedEffect(extId) {
        val related = runCatching { Smartico.api.getRelatedItemsForGame(extId) }.getOrNull()
        achIds = related?.achievements.orEmpty().mapNotNull { it.ach_id }.toSet()
        tourIds = related?.tournaments.orEmpty().mapNotNull { it.tournamentId?.toLong() }.toSet()
        Store.refreshTournaments()
        loaded = true
    }

    val missions = allMissions.filter { it.id in achIds }
    val tournaments = allTournaments.filter { it.tournament_id in tourIds }

    Column(
        Modifier
            .fillMaxWidth(0.78f)
            // leaves the bet/spin row usable while the panel is open
            .fillMaxHeight(0.72f)
            .background(Color(0xF012132A))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PanelTab("🎯 Missions", tab == 0, Modifier.weight(1f)) { tab = 0 }
            Spacer(Modifier.width(6.dp))
            PanelTab("🏆 Tournaments", tab == 1, Modifier.weight(1f)) { tab = 1 }
            Spacer(Modifier.width(6.dp))
            Text("✕", color = Muted, fontSize = 17.sp, modifier = Modifier.clickable { onClose() })
        }
        Spacer(Modifier.height(10.dp))

        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (!loaded) {
                Meta("loading…")
            } else if (tab == 0) {
                if (missions.isEmpty()) {
                    Meta("No missions related to this game.")
                } else {
                    for (m in missions) {
                        PanelCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Thumb(m.image, 40)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stripHtml(m.name),
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    missionStatus(m)?.let { (label, tone) ->
                                        Spacer(Modifier.height(3.dp))
                                        Badge(label, tone)
                                    }
                                }
                            }
                            val progress = m.progress ?: 0.0
                            Spacer(Modifier.height(8.dp))
                            Progress(progress, completed = m.is_completed == true)
                            Text("${progress.toInt()}%", color = Muted, fontSize = 10.sp)

                            m.tasks.orEmpty().forEach { t ->
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val done = t.is_completed == true
                                    Text(if (done) "✔" else "○", color = if (done) Good else Muted, fontSize = 11.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        stripHtml(t.name),
                                        color = if (done) Muted else Color.White,
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    t.execution_count_expected?.let { exp ->
                                        Text(
                                            "${(t.execution_count_actual ?: 0.0).toInt()}/${exp.toInt()}",
                                            color = Muted,
                                            fontSize = 10.sp,
                                        )
                                    }
                                    if ((t.points_reward ?: 0) > 0) {
                                        Spacer(Modifier.width(6.dp))
                                        Text("${t.points_reward}p", color = Gold, fontSize = 10.sp)
                                    }
                                }
                            }

                            if (m.is_requires_optin == true && m.is_opted_in != true && m.is_locked != true) {
                                Spacer(Modifier.height(6.dp))
                                ActionButton("Opt-in", onResult = { note = it; Store.refreshMissions() }) {
                                    val r = Smartico.api.requestMissionOptIn(m.id ?: 0)
                                    if (r.err_code == 0L) "Opted in ✓" else r.err_message ?: "Failed (${r.err_code})"
                                }
                            }
                        }
                    }
                    note?.let { Meta(it) }
                }
            } else {
                if (tournaments.isEmpty()) {
                    Meta("No tournaments related to this game.")
                } else {
                    for (t in tournaments) {
                        PanelCard {
                            Text(
                                stripHtml(t.name),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            Meta(
                                (stripHtml(t.prize_pool_short).ifEmpty { "No prize" }) +
                                    " · ${t.registration_count ?: 0} joined" +
                                    if (t.is_user_registered == true) " · you ✓" else "",
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Status pill for a tournament, matching the RN demo's tournamentStatus(). */
fun tournamentStatus(
    isCancelled: Boolean?,
    isFinished: Boolean?,
    isInProgress: Boolean?,
    isUpcoming: Boolean?,
): Pair<String, Color>? = when {
    isCancelled == true -> "Cancelled" to Muted
    isFinished == true -> "Finished" to Muted
    isInProgress == true -> "Started" to Good
    isUpcoming == true -> "Gathering" to Accent
    else -> null
}

/** What the countdown above a tournament is counting towards. */
fun tournamentTimerLabel(t: TTournamentDetailed): String = when {
    t.is_in_progress == true -> "Finishing in"
    t.is_upcoming == true -> "Starts in"
    t.is_finished == true -> "Finished"
    t.is_cancelled == true -> "Cancelled"
    else -> ""
}

/** Status pill shared by the missions list and the in-game panel. */
fun missionStatus(m: TMissionOrBadge): Pair<String, Color>? = when {
    m.is_locked == true -> "Locked" to Muted
    m.is_completed == true -> "Completed" to Good
    m.is_opted_in == true -> "In Progress" to Accent
    m.is_requires_optin == true -> "Opt-in" to Accent
    else -> null
}

@Composable
private fun PanelTab(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) = Box(
    modifier
        .clip(RoundedCornerShape(9.dp))
        .background(if (on) Accent else Card)
        .clickable { onClick() }
        .padding(vertical = 7.dp),
    contentAlignment = Alignment.Center,
) {
    Text(label, color = if (on) Color.White else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun PanelCard(content: @Composable ColumnScope.() -> Unit) = Surface(
    color = Card,
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
) {
    Column(Modifier.padding(10.dp), content = content)
}

