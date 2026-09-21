package ai.smartico.fakecasino

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.text.style.TextOverflow
import ai.smartico.publicapi.Smartico
import ai.smartico.publicapi.api.getMissions
import ai.smartico.publicapi.api.getRelatedItemsForGame
import ai.smartico.publicapi.api.getTournamentsList
import ai.smartico.publicapi.types.TMissionOrBadge
import ai.smartico.publicapi.types.TTournament
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage

/**
 * Casino lobby, mirroring the web fake-casino: a promo strip, the Top games
 * row, live missions and tournaments carousels, then the full game grid.
 */
@Composable
fun LobbyScreen(nav: NavHostController) {
    val missions by Store.missions.collectAsState()
    val tournaments by Store.tournaments.collectAsState()
    LaunchedEffect(Unit) {
        Store.refreshMissions()
        Store.refreshTournaments()
    }
    val top = remember { casinoGames.filter { "Top" in it.categories } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        SectionTitle("Promotions")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val promos = listOf(
                Triple("🎡", "Mini-games", Routes.GAMES),
                Triple("💰", "Jackpots", Routes.JACKPOTS),
                Triple("🏆", "Tournaments", Routes.TOURNAMENTS),
                Triple("🎟️", "Raffles", Routes.RAFFLES),
            )
            items(promos.size) { i ->
                val (icon, label, route) = promos[i]
                Box(
                    Modifier
                        .size(140.dp, 76.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Card)
                        .clickable { nav.navigate(route) },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(icon, fontSize = 22.sp)
                        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        SectionTitle("Top games")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(top.size) { i -> GameTile(top[i], 120) { nav.navigate(Routes.game(top[i].extId)) } }
        }

        if (missions.isNotEmpty()) {
            SectionHeader("Missions") { nav.navigate(Routes.MISSIONS) }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // locked missions are teasers, not something to jump into
                val visible = missions.filter { it.is_locked != true }.take(8)
                items(visible.size) { i ->
                    val m = visible[i]
                    LobbyCard(onClick = { nav.navigate(Routes.MISSIONS) }) {
                        CardArt(m.image)
                        Spacer(Modifier.height(6.dp))
                        CardName(stripHtml(m.name))
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${(m.progress ?: 0.0).toInt()}%" +
                                (m.tasks?.size?.let { n -> if (n > 0) "  ·  $n task${if (n > 1) "s" else ""}" else "" } ?: ""),
                            color = Muted,
                            fontSize = 10.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Progress(m.progress ?: 0.0, completed = m.is_completed == true)
                    }
                }
            }
        }

        if (tournaments.isNotEmpty()) {
            SectionHeader("Tournaments") { nav.navigate(Routes.TOURNAMENTS) }
            val now = rememberNow()
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(tournaments.size.coerceAtMost(8)) { i ->
                    val t = tournaments[i]
                    val status = tournamentStatus(t.is_cancelled, t.is_finished, t.is_in_progress, t.is_upcoming)
                    val live = t.is_in_progress == true || t.is_upcoming == true
                    val target = if (t.is_in_progress == true) t.end_time else t.start_time
                    LobbyCard(onClick = { nav.navigate(Routes.tournament(t.instance_id ?: 0)) }) {
                        Box {
                            CardArt(t.image1)
                            status?.let { (label, tone) ->
                                Box(Modifier.align(Alignment.TopStart).padding(4.dp)) { Badge(label, tone) }
                            }
                            if (t.is_user_registered == true) {
                                Text(
                                    "✓",
                                    color = Good,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        CardName(stripHtml(t.name))
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stripHtml(t.prize_pool_short).ifEmpty { "No prize" } + "  ·  ${t.registration_count ?: 0} joined",
                            color = Muted,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (live && target != null) countdownShort(target, now) else status?.first ?: "—",
                            color = if (live) Accent else Muted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        SectionTitle("All games")
        // a simple 3-column grid built from rows (the list is fixed and small)
        casinoGames.chunked(3).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { g ->
                    Box(Modifier.weight(1f)) { GameTile(g, null) { nav.navigate(Routes.game(g.extId)) } }
                }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) {} }
            }
        }
    }
}

/** Points / wallet / deposit strip, like the web app's header. */

/** Section title with a "View all ›" affordance, like the RN lobby. */
@Composable
private fun SectionHeader(text: String, onViewAll: () -> Unit) = Row(
    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    Text(
        "View all ›",
        color = Accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clickable { onViewAll() },
    )
}

/** One carousel card: fixed width so the row scrolls in even steps. */
@Composable
private fun LobbyCard(onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier
        .width(160.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(Card)
        .clickable { onClick() }
        .padding(8.dp),
    content = content,
)

@Composable
private fun CardArt(url: String?) = Box(
    Modifier.fillMaxWidth().height(70.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2B45)),
) {
    if (!url.isNullOrEmpty()) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun CardName(text: String) = Text(
    text,
    color = Color.White,
    fontSize = 12.sp,
    fontWeight = FontWeight.Bold,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
)

@Composable
private fun SectionTitle(text: String) = Text(
    text,
    color = Color.White,
    fontSize = 17.sp,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
)

@Composable
private fun GameTile(game: CasinoGame, size: Int?, onClick: () -> Unit) {
    val mod = if (size != null) Modifier.size(size.dp) else Modifier.fillMaxWidth().aspectRatio(1f)
    Column {
        AsyncImage(
            model = gameAsset(game.thumbnail),
            contentDescription = game.name,
            contentScale = ContentScale.Crop,
            modifier = mod.clip(RoundedCornerShape(12.dp)).background(Card).clickable { onClick() },
        )
        Spacer(Modifier.height(4.dp))
        Text(game.name, color = Color.White, fontSize = 11.sp, maxLines = 1)
    }
}
