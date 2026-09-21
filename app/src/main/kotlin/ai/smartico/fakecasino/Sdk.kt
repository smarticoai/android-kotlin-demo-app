package ai.smartico.fakecasino

import ai.smartico.publicapi.Smartico
import ai.smartico.publicapi.transport.SmarticoOptions
import ai.smartico.publicapi.transport.SmarticoUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest

/**
 * Smartico wiring for the demo — ICE env4, the same label the React Native
 * demo uses.
 *
 * The label's hash salt is the literal string "null", so the identify hash can
 * be computed here. A production app NEVER does this: the operator's backend
 * computes it with the real (secret) salt and the app just forwards it.
 */
object Sdk {
    private const val TAG = "SmarticoDemo"

    const val LABEL_KEY = "a6e7ac26-c368-4892-9380-96e7ff82cf3e-4"
    const val BRAND_KEY = "f86271e6"

    /** Public properties (points, level, avatar…), kept live by props_change. */
    private val _props = MutableStateFlow<Map<String, JsonElement>>(emptyMap())
    val props: StateFlow<Map<String, JsonElement>> = _props

    /** Flips to true once the server has identified the user. */
    private val _identified = MutableStateFlow(false)
    val identified: StateFlow<Boolean> = _identified

    /** Bumped whenever the SDK's popup queue changes, so the UI can pump it. */
    private val _pendingPopups = MutableStateFlow(0)
    val pendingPopups: StateFlow<Int> = _pendingPopups

    @Volatile
    var extUserId: String = ""
        private set

    /**
     * The avatar just applied. setAvatar succeeds before the server pushes the
     * new public properties, so without this the UI keeps showing the old
     * picture until some later push happens to carry the new one.
     */
    private val _avatar = MutableStateFlow<String?>(null)
    val avatar: StateFlow<String?> = _avatar

    /** Current avatar URL: the freshly applied one, else what props report. */
    fun avatarUrl(): String? = _avatar.value ?: prop("avatar_url") ?: prop("avatar_id")

    /** Called after a successful setAvatar; also re-reads the server snapshot. */
    fun onAvatarApplied(url: String) {
        _avatar.value = url
        scope.launch {
            runCatching {
                delay(400) // give the server a moment to store it
                _props.value = _props.value + Smartico.getPublicProps()
            }
        }
    }

    /** Name from the social login — used until the server has a real one. */
    @Volatile
    var socialName: String? = null
        private set

    /** True once a player has been chosen on the login screen. */
    private val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn

    private var started = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun start(user: String) {
        if (user != extUserId) {
            Store.clear() // different player, different data
            _avatar.value = null
        }
        extUserId = user
        _loggedIn.value = true
        if (!started) {
            started = true
            // Subscriptions are registered once: the facade re-attaches them to
            // every new connection, so they survive a re-init.
            Smartico.on("identify") {
                _identified.value = true
                // The token binds to the identified user server-side, so it is
                // sent here rather than at startup. Repeat identifies (login,
                // then profile enrichment) are deduped inside Push.register.
                App.appContext?.let { ctx -> Push.register(ctx, extUserId) }
                scope.launch {
                    // The full property snapshot lives on the connection after
                    // identify; without merging it here the UI only ever saw
                    // whatever a later props push happened to carry — which is
                    // why the profile showed the raw ext id instead of the name.
                    runCatching { _props.value = _props.value + Smartico.getPublicProps() }
                }
                Store.refreshAll() // full snapshot for the identified user
            }
            Smartico.on("props_change") { msg ->
                (msg["props"] as? JsonObject)?.let { patch ->
                    _props.value = _props.value + patch
                }
            }
            // Every fresh engagement (popup / inbox / push) surfaces here.
            Smartico.on("engagement") { msg ->
                android.util.Log.i(
                    TAG,
                    "engagement activityType=${msg["activityType"] ?: msg["activity_type"]}" +
                        " uid=${msg["engagement_uid"]}",
                )
            }
            Smartico.onEngagementsChanged {
                val n = Smartico.pendingEngagements()
                android.util.Log.i(TAG, "popup queue = $n")
                _pendingPopups.value = n
            }
            // Server-initiated deep link: the SDK routes it, but only the app
            // can decide it should run — the same uid may also arrive as a
            // popup (cid 110), so it shares the SDK's engagement dedupe.
            Smartico.on("execute_deeplink") { msg ->
                val uid = (msg["engagement_uid"] as? JsonPrimitive)?.content.orEmpty()
                if (uid.isNotEmpty() && Smartico.isDuplicateEngagement(uid)) return@on
                val dp = ((msg["payload"] as? JsonObject)?.get("dp") ?: msg["dp"])
                    ?.let { (it as? JsonPrimitive)?.content }
                if (dp.isNullOrEmpty()) return@on
                android.util.Log.i(TAG, "execute_deeplink → $dp")
                Smartico.dp(dp)
            }

            // "Show this mini-game now" campaign push.
            Smartico.on("show_spin") { msg ->
                val id = (msg["saw_template_id"] as? JsonPrimitive)?.content ?: return@on
                Smartico.dp("dp:gf_saw&id=$id&standalone=true")
            }
            // the canonical "missions/badges changed" push — the only signal
            // needed for live progress (points also move, but that arrives as a
            // separate props push and would just double the fetch)
            Smartico.on("reload_achievements") {
                android.util.Log.i(TAG, "achievements changed (server push) → reloading")
                Store.refreshMissions()
                Store.refreshBadges()
            }
        }
        _identified.value = false
        Smartico.init(
            LABEL_KEY,
            SmarticoOptions(
                brandKey = BRAND_KEY,
                debug = true,
                getUser = { SmarticoUser(extUserId = extUserId, hash = demoHash(extUserId)) },
            ),
        )
    }

    /**
     * Seed the demo profile from the social login, the way the web fake-casino
     * does: a fresh user gets an auto-generated username derived from the id, so
     * replace it with the real first name. Best-effort — never blocks login.
     */
    /**
     * A server-generated username echoes the user's ext id (optionally behind a
     * "<label id>:" prefix). That is a placeholder, not a name — the UI should
     * show the social-login name instead, and the enrichment below replaces it.
     */
    fun isAutoUsername(username: String?, extUserId: String): Boolean {
        if (username.isNullOrEmpty()) return true
        val bare = username.substringAfter(':', username)
        return extUserId.contains(bare, ignoreCase = true) || bare.contains(extUserId, ignoreCase = true)
    }

    /** The name to show: real server username, else social name, else the id. */
    fun displayName(): String {
        val username = prop("public_username")
        if (!isAutoUsername(username, extUserId)) return username!!
        return socialName ?: username ?: extUserId
    }

    fun enrichProfile(user: Auth.SessionUser) {
        val first = user.profile?.firstName?.takeIf { it.isNotEmpty() }
            ?: user.profile?.lastName?.takeIf { it.isNotEmpty() }
            ?: user.email?.substringBefore("@")?.takeIf { it.isNotEmpty() }
            ?: return
        socialName = listOfNotNull(
            user.profile?.firstName?.takeIf { it.isNotEmpty() },
            user.profile?.lastName?.takeIf { it.isNotEmpty() },
        ).joinToString(" ").ifEmpty { first }
        scope.launch {
            runCatching {
                // wait for identify so public props exist
                repeat(50) { if (props.value.isNotEmpty()) return@repeat else delay(200) }
                // only overwrite the placeholder name, never a real one
                if (!isAutoUsername(prop("public_username"), user.user_ext_id)) return@runCatching
                Smartico.event("demo_personal_details_update", buildJsonObject {
                    put("firstName", JsonPrimitive(first))
                    put("currency", JsonPrimitive("EUR"))
                })
                user.profile?.lastName?.takeIf { it.isNotEmpty() }?.let {
                    Smartico.event("demo_personal_details_update", buildJsonObject { put("lastName", JsonPrimitive(it)) })
                }
                user.email?.takeIf { it.isNotEmpty() }?.let {
                    Smartico.event("demo_personal_details_update", buildJsonObject { put("playerEMail", JsonPrimitive(it)) })
                }
                delay(1200)
                if (extUserId == user.user_ext_id) start(user.user_ext_id) // re-identify so the name lands
            }
        }
    }

    /**
     * Reset demo progress. The backend clones the player under a fresh
     * ext_user_id (same login session), so points, missions and levels start
     * over; we then re-identify as that new user.
     */
    suspend fun resetProgress(ctx: android.content.Context): String {
        val user = Auth.resetProgress(ctx) ?: return "No session to reset"
        start(user.user_ext_id)
        enrichProfile(user) // the clone starts with a placeholder name too
        return "Progress reset ✓"
    }

    /** Drop the session and go back to the login screen. */
    fun logout(ctx: android.content.Context? = null) {
        ctx?.let {
            Auth.clearCookie(it)
            // forget the Google session, otherwise the next
            // login silently reuses the previous account
            scope.launch { Providers.signOut(it) }
        }
        Smartico.logout()
        _identified.value = false
        _loggedIn.value = false
        _props.value = emptyMap()
        _avatar.value = null
        Store.clear()
    }

    fun prop(key: String): String? = (props.value[key] as? JsonPrimitive)?.content

    /** md5("<user>:<salt>:<ts>") + ":" + ts, with the demo label's "null" salt. */
    fun demoHash(user: String): String {
        val ts = System.currentTimeMillis() / 1000 * 1000 + 24 * 3600 * 1000
        val md5 = MessageDigest.getInstance("MD5")
            .digest("$user:null:$ts".lowercase().toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "$md5:$ts"
    }
}
