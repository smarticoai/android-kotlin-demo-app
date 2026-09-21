package ai.smartico.fakecasino

import ai.smartico.publicapi.types.TAvatarCustomized
import ai.smartico.publicapi.types.TAvatarPrompt
import androidx.compose.foundation.border
import kotlinx.serialization.json.JsonPrimitive
import ai.smartico.publicapi.Smartico
import ai.smartico.publicapi.api.avatarsCustomize
import ai.smartico.publicapi.api.getAvatarPrompts
import ai.smartico.publicapi.api.getAvatarsCustomized
import ai.smartico.publicapi.api.getAvatarsList
import ai.smartico.publicapi.api.getBadges
import ai.smartico.publicapi.api.getCurrentLevel
import ai.smartico.publicapi.api.setAvatar
import ai.smartico.publicapi.types.TAvatarDefinition
import ai.smartico.publicapi.types.TMissionOrBadge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * Badges grid with a detail sheet, ported from the RN profile: unearned badges
 * render dimmed, and the operator's excluded custom section (1128) is hidden.
 */
@Composable
fun BadgesSection() {
    val badges by Store.badges.collectAsState()
    var selected by remember { mutableStateOf<TMissionOrBadge?>(null) }

    LaunchedEffect(Unit) { Store.refreshBadges() }
    if (badges.isEmpty()) return

    Card {
        Title("Badges (${badges.count { it.is_completed == true }}/${badges.size})")
        Spacer(Modifier.height(10.dp))
        badges.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { b ->
                    AsyncImage(
                        model = b.image,
                        contentDescription = stripHtml(b.name),
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1A1B30))
                            .alpha(if (b.is_completed == true) 1f else 0.28f)
                            .clickable { selected = b },
                    )
                }
            }
        }
    }

    selected?.let { b ->
        AlertDialog(
            onDismissRequest = { selected = null },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } },
            containerColor = Card,
            title = { Text(stripHtml(b.name), color = Color.White) },
            text = {
                Column {
                    AsyncImage(
                        model = b.image,
                        contentDescription = null,
                        modifier = Modifier.size(96.dp).alpha(if (b.is_completed == true) 1f else 0.35f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stripHtml(b.description), color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (b.is_completed == true) "✓ Earned" else "Not earned yet",
                        color = if (b.is_completed == true) Color(0xFF7CD39A) else Muted,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
        )
    }
}

/**
 * Avatar picker: the operator's catalog, the user's AI-generated ones, and the
 * prompt list that drives new generations.
 */
@Composable
fun AvatarPickerScreen(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var catalog by remember { mutableStateOf<List<TAvatarDefinition>>(emptyList()) }
    var customized by remember { mutableStateOf<List<TAvatarCustomized>>(emptyList()) }
    var prompts by remember { mutableStateOf<List<TAvatarPrompt>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var note by remember { mutableStateOf<String?>(null) }
    var genNote by remember { mutableStateOf<String?>(null) }
    var generating by remember { mutableStateOf(false) }
    var generated by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var promptId by remember { mutableStateOf<Long?>(null) }
    // Which avatar is active: is_in_use is not filled in on every label, so it
    // is seeded from the profile props and updated locally after setAvatar.
    var activeRealId by remember { mutableStateOf(Sdk.prop("avatar_real_id")?.toDoubleOrNull()?.toLong()) }
    val appliedAvatar by Sdk.avatar.collectAsState()

    LaunchedEffect(reload) {
        loading = true
        catalog = runCatching {
            Smartico.api.getAvatarsList()
                // hidden-until-earned avatars stay out of the grid entirely
                .filter { it.hide_until_achieved != true || it.is_given == true }
                .sortedBy { it.priority ?: 0 }
        }.getOrDefault(emptyList())
        customized = runCatching {
            Smartico.api.getAvatarsCustomized().sortedByDescending { it.dt_created ?: 0 }
        }.getOrDefault(emptyList())
        prompts = runCatching { Smartico.api.getAvatarPrompts() }.getOrDefault(emptyList())
        if (promptId == null) promptId = prompts.firstOrNull()?.prompt_id
        loading = false
    }

    fun apply(url: String, realId: Long) {
        scope.launch {
            val r = runCatching {
                Smartico.api.setAvatar(avatar_url = url, avatar_real_id = realId)
            }.getOrNull()
            note = when {
                r == null -> "Failed to apply"
                r.err_code == 0L -> {
                    activeRealId = realId
                    Sdk.onAvatarApplied(url) // show it immediately, everywhere
                    "Applied ✓"
                }
                else -> r.err_message ?: "Failed to apply (code ${r.err_code})"
            }
            reload++
        }
    }

    // What the AI styles a new avatar from: the active one, else the first free
    // one, else whatever is first in the catalog.
    val base = catalog.firstOrNull { it.avatar_real_id == activeRealId }
        ?: catalog.firstOrNull { it.avatar_source_type_id == 0L }
        ?: catalog.firstOrNull()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Avatar", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("✕", color = Muted, fontSize = 20.sp, modifier = Modifier.clickable { onClose() })
        }
        Spacer(Modifier.height(12.dp))
        if (loading) {
            Loading()
            return@Column
        }
        note?.let { Meta(it); Spacer(Modifier.height(8.dp)) }

        Card {
            Title("Catalog (${catalog.size})")
            Meta("🔒 avatars unlock through levels and missions")
            Spacer(Modifier.height(8.dp))
            catalog.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { a ->
                        // Locked = not granted to this user and not a free
                        // (source type 0) avatar. Shown dimmed with a padlock
                        // instead of looking selectable and failing on tap.
                        val locked = a.is_given != true && a.avatar_source_type_id != 0L
                        val active = a.avatar_real_id == activeRealId || a.is_in_use == true
                        Box(
                            Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1A1B30))
                                .then(if (active) Modifier.border(2.dp, Accent, RoundedCornerShape(10.dp)) else Modifier)
                                .clickable(enabled = !locked) {
                                    // avatar_url is the absolute CDN link; `url`
                                    // is the raw relative path the server does
                                    // not accept here
                                    apply(a.avatar_url.orEmpty(), a.avatar_real_id ?: 0)
                                },
                        ) {
                            AsyncImage(
                                model = a.avatar_url,
                                contentDescription = stripHtml(a.description),
                                modifier = Modifier.fillMaxSize().alpha(if (locked) 0.3f else 1f),
                            )
                            if (locked) {
                                Text("🔒", fontSize = 16.sp, modifier = Modifier.align(Alignment.Center))
                            }
                            if (active) {
                                Text(
                                    "✓",
                                    color = Accent,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Card {
            Title("Customize with AI")
            if (base == null) {
                Meta("No avatar to style yet.")
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = appliedAvatar ?: base.avatar_url,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Meta("Styling your current avatar")
                }
                Spacer(Modifier.height(8.dp))
                if (prompts.isEmpty()) {
                    Meta("No AI styles configured on this label.")
                } else {
                    prompts.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { p ->
                                val on = p.prompt_id == promptId
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(if (on) Accent else Color(0xFF1A1B30))
                                        .clickable { promptId = p.prompt_id }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                ) {
                                    Text(stripHtml(p.name), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        if ((p.cost_value ?: 0.0) > 0) {
                                            "${currencyIcon(p.cost_currency_type_id)}${(p.cost_value ?: 0.0).toInt()}"
                                        } else {
                                            "free"
                                        },
                                        color = if (on) Color.White else Gold,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    ActionButton(if (generating) "Generating… (5–20s)" else "Generate") {
                        val userId = Sdk.prop("user_id")?.toDoubleOrNull()?.toLong()
                        val pid = promptId
                        if (userId == null || pid == null) return@ActionButton "Not ready yet"
                        generating = true
                        generated = null
                        genNote = null
                        val r = runCatching {
                            Smartico.api.avatarsCustomize(
                                userId = userId,
                                promptId = pid,
                                avatarUrl = base.avatar_url.orEmpty(),
                                avatarRealId = base.avatar_real_id ?: 0,
                            )
                        }.getOrNull()
                        generating = false
                        // Success is a cdn_url — this endpoint reports failures
                        // through errCode/errMessage, which is why a plain
                        // "err_code == 0" check printed "Failed (null)".
                        val url = r?.cdn_url
                        val code = (r?.errCode as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()
                        genNote = when {
                            !url.isNullOrEmpty() -> {
                                generated = url to (base.avatar_real_id ?: 0)
                                reload++
                                "Generated ✓ — apply it below"
                            }
                            code == 12001 -> "Monthly limit reached for your custom avatars."
                            code == 12002 -> "Custom avatars unavailable right now — try later."
                            else -> r?.errMessage ?: "Generation failed (maybe not enough balance)."
                        }
                        genNote.orEmpty()
                    }
                    genNote?.let { Spacer(Modifier.height(6.dp)); Meta(it) }
                    generated?.let { (url, realId) ->
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)),
                            )
                            Spacer(Modifier.width(10.dp))
                            ActionButton("Apply this") { apply(url, realId); "" }
                        }
                    }
                }
            }
        }

        if (customized.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Card {
                Title("Your AI avatars (${customized.size})")
                Spacer(Modifier.height(8.dp))
                customized.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { c ->
                            AsyncImage(
                                model = c.url,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1A1B30))
                                    .clickable { apply(c.url.orEmpty(), c.avatar_real_id ?: 0) },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** cost_currency_type_id → icon (0 points, 1 gems, 2 diamonds). */
private fun currencyIcon(id: Long?): String = when (id) {
    1L -> "💎"
    2L -> "🔷"
    else -> "🪙"
}

@Composable
fun NameDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(Sdk.prop("public_username").orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Card,
        title = { Text("Change display name", color = Color.White) },
        text = {
            Column {
                OutlinedTextField(value = draft, onValueChange = { draft = it }, singleLine = true)
                Meta("3–20 characters")
                error?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (draft.length !in 3..20) {
                    error = "Name must be 3–20 characters"
                    return@TextButton
                }
                scope.launch {
                    runCatching { Smartico.changeUsername(draft) }
                        .onSuccess { onDismiss() }
                        .onFailure { error = it.message }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The VIP ladder with the user's position, from getLevels + getCurrentLevel. */
@Composable
fun VipScreen() {
    var progress by remember { mutableStateOf<Double?>(null) }
    var currentName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { Smartico.api.getCurrentLevel() }.getOrNull()?.let {
            progress = it.progress
            currentName = stripHtml(it.name)
        }
    }
    Column(Modifier.fillMaxSize()) {
        currentName?.let {
            Column(Modifier.padding(16.dp)) {
                Card {
                    Title("Current level: $it")
                    Spacer(Modifier.height(8.dp))
                    Progress(progress ?: 0.0)
                    Meta("${progress?.toInt() ?: 0}% to the next level")
                }
            }
        }
        LevelsScreen()
    }
}
