package ai.smartico.fakecasino

import ai.smartico.publicapi.Smartico
import ai.smartico.publicapi.api.getBadges
import ai.smartico.publicapi.api.getMissions
import ai.smartico.publicapi.api.getTournamentsList
import ai.smartico.publicapi.types.TMissionOrBadge
import ai.smartico.publicapi.types.TTournament
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Collections

/**
 * The demo's data mirror for achievement-driven lists, built the same way as
 * the RN demo's store.
 *
 * ONE place fetches; every screen reads the same flow. That matters because
 * missions show up in four places at once (tab, lobby carousel, in-game panel,
 * badges): with per-screen loads a single server push meant four identical
 * requests, and each screen dropped its list to a spinner while refetching.
 * Here a push costs one request and the previous data stays on screen until
 * the new list lands.
 */
object Store {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Guards against stacking identical requests (RN: the `inFlight` set). */
    private val inFlight = Collections.synchronizedSet(mutableSetOf<String>())

    private val _missions = MutableStateFlow<List<TMissionOrBadge>>(emptyList())
    val missions: StateFlow<List<TMissionOrBadge>> = _missions

    private val _badges = MutableStateFlow<List<TMissionOrBadge>>(emptyList())
    val badges: StateFlow<List<TMissionOrBadge>> = _badges

    private val _tournaments = MutableStateFlow<List<TTournament>>(emptyList())
    val tournaments: StateFlow<List<TTournament>> = _tournaments

    /** Flips once the first load lands, so screens can tell empty from loading. */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    fun refreshAll() {
        refreshMissions()
        refreshBadges()
        refreshTournaments()
    }

    fun refreshMissions() = load("missions") {
        _missions.value = Smartico.api.getMissions()
        _loaded.value = true
    }

    fun refreshBadges() = load("badges") {
        // 1128 is the demo label's hidden section
        _badges.value = Smartico.api.getBadges().filter { it.custom_section_id != 1128L }
    }

    fun refreshTournaments() = load("tournaments") {
        // Tournaments flagged only_in_custom_section belong to that section's own
        // view — keeping them here would also skew the per-tab counters.
        _tournaments.value = Smartico.api.getTournamentsList().filter { it.only_in_custom_section != true }
    }

    /** User boundary: a different player must not see the old lists. */
    fun clear() {
        _missions.value = emptyList()
        _badges.value = emptyList()
        _tournaments.value = emptyList()
        _loaded.value = false
    }

    private fun load(key: String, block: suspend () -> Unit) {
        if (!inFlight.add(key)) return
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                android.util.Log.w("SmarticoDemo", "$key refresh failed: ${e.message}")
            } finally {
                inFlight.remove(key)
            }
        }
    }
}
