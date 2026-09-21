package ai.smartico.fakecasino

import ai.smartico.publicapi.Smartico
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.random.Random

/**
 * The demo's fake money, exactly as the web fake-casino does it.
 *
 * The client NEVER moves balances itself: money events are authored by the ICE
 * demo backend (SL_SERVER), which recomputes the wallet; the app just polls
 * `wallet-debit` with a zero amount to read the current balance back.
 */
object Economy {
    private const val SL_SERVER = "https://dvm0p9vsezqr2.cloudfront.net/social-login"
    private const val CURRENCY = "EUR"
    private const val POLL_MS = 2_000L

    /** Events the backend must author (they move money). Everything else goes over the socket. */
    private val SERVER_EVENTS = setOf(
        "acc_deposit_approved",
        "casino_bet_win",
        "sport_bet_open",
        "sport_bet_settled",
        "demo_fx_open_position",
        "demo_fx_close_position",
    )

    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _balance = MutableStateFlow(0.0)
    val balance: StateFlow<Double> = _balance
    private val _bonus = MutableStateFlow(0.0)
    val bonus: StateFlow<Double> = _bonus

    private var polling = false

    /** Start the 2s balance poll (no-op if already running). */
    fun startPolling() {
        if (polling) return
        polling = true
        scope.launch {
            while (true) {
                refresh()
                delay(POLL_MS)
            }
        }
    }

    /** One balance read: a zero-amount debit is the demo backend's "get balance". */
    suspend fun refresh() {
        val userId = Sdk.prop("user_id") ?: return // not identified yet
        val r = post(
            "wallet-debit",
            buildJsonObject {
                put("user_id", userId.toDoubleOrNull()?.toLong() ?: return@buildJsonObject)
                put("currency", CURRENCY)
                put("amount_real", 0)
                put("amount_bonus", 0)
            },
        ) ?: return
        (r["amount_real"] as? JsonPrimitive)?.doubleOrNull?.let { _balance.value = it }
        (r["amount_bonus"] as? JsonPrimitive)?.doubleOrNull?.let { _bonus.value = it }
    }

    /** A casino spin: bet and win are settled by the backend. */
    suspend fun reportSpin(gameExtId: String, bet: Int, win: Int) = submit(
        "casino_bet_win",
        buildJsonObject {
            put("casino_last_bet_id", randomId())
            put("casino_last_bet_dt", System.currentTimeMillis())
            put("casino_last_bet_game_name", gameExtId)
            put("casino_last_bet_amount", bet)
            put("casino_last_win_amount", win)
        },
    )

    /** A cashier deposit. */
    suspend fun reportDeposit(amount: Int) = submit(
        "acc_deposit_approved",
        buildJsonObject {
            put("acc_last_deposit_date", System.currentTimeMillis())
            put("acc_last_deposit_amount", amount)
            put("acc_last_transaction_id", randomId())
        },
    )

    /**
     * Route one event: money events go to the backend (which then moves the
     * wallet), everything else straight over the Smartico socket.
     */
    private suspend fun submit(eventName: String, payload: JsonObject) {
        if (eventName in SERVER_EVENTS) {
            val withUser = buildJsonObject {
                payload.forEach { (k, v) -> put(k, v) }
                Sdk.prop("user_id")?.toDoubleOrNull()?.toLong()?.let { put("user_id", it) }
            }
            post(
                "sendEventIce",
                buildJsonObject {
                    put("eventName", eventName)
                    put("payload", withUser)
                    put("user_ext_id", Sdk.extUserId)
                },
            )
            refresh() // the balance moved server-side — read it back now
        } else {
            Smartico.event(eventName, payload)
        }
    }

    private fun randomId() = Random.nextLong(1_000_000, 9_999_999).toString(36)

    private suspend fun post(method: String, body: JsonObject): JsonObject? =
        suspendCancellableCoroutine { cont ->
            // every call to this backend is scoped by label + brand, exactly
            // like the web demo's API.requestServer
            val payload = buildJsonObject {
                put("label_public_key", Sdk.LABEL_KEY)
                put("brand_public_key", Sdk.BRAND_KEY)
                body.forEach { (k, v) -> put(k, v) }
            }
            val req = Request.Builder()
                .url("$SL_SERVER/$method")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val call = http.newCall(req)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    android.util.Log.w("SmarticoDemo", "$method failed: ${e.message}")
                    if (cont.isActive) cont.resume(null)
                }

                override fun onResponse(call: okhttp3.Call, response: Response) {
                    val parsed = response.use { r ->
                        val text = r.body?.string().orEmpty()
                        if (!r.isSuccessful) android.util.Log.w("SmarticoDemo", "$method HTTP ${r.code}: $text")
                        else if (method != "wallet-debit") android.util.Log.i("SmarticoDemo", "$method → $text")
                        runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
                    }
                    if (cont.isActive) cont.resume(parsed)
                }
            })
        }
}
