package ai.smartico.fakecasino

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Google login against the demo backend, ported from the RN demo.
 *
 * Flow: Google hands us an id_token, the backend verifies
 * it and answers with the Smartico `user_ext_id` plus a `cookie_token` we store
 * to restore the session on the next launch. The app never invents user ids —
 * the backend owns that mapping.
 */
object Auth {
    private const val TAG = "SmarticoDemo"

    private const val SL_SERVER = "https://dvm0p9vsezqr2.cloudfront.net/social-login"
    private const val PREFS = "smartico_demo"
    private const val COOKIE_KEY = "sm_cookie_token"

    /** Provider id the backend expects for Google. */
    const val PROVIDER_GGL = 2

    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val JSON_TYPE = "application/json".toMediaType()

    @Serializable
    data class SocialProfile(
        val firstName: String = "",
        val lastName: String = "",
        val name: String = "",
        val picture: String = "",
    )

    /** What a provider sign-in produces, ready to POST to checkProviderToken. */
    @Serializable
    data class ProviderResult(
        val provider_id: Int,
        val provider_user_id: String,
        val provider_token: String,
        val email: String = "",
        val profile: SocialProfile = SocialProfile(),
    )

    @Serializable
    data class SessionUser(
        val user_ext_id: String,
        val email: String? = null,
        val profile: SocialProfile? = null,
    )

    @Serializable
    private data class ProviderTokenResponse(
        val valid: Boolean? = null,
        val cookie_token: String? = null,
        val user_ext_id: String? = null,
        val err_message: String? = null,
    )

    @Serializable
    private data class CookieTokenResponse(
        val user_ext_id: String? = null,
        val email: String? = null,
        // the backend writes the profile with JSON.stringify, so it usually
        // comes back as a STRING — but not always. Keep it raw and unpack below.
        val profile: JsonElement? = null,
    )

    /**
     * POST to the social-login server. Every call carries the label/brand keys.
     * A native app has no browser cookie jar, so the session token travels in
     * the body and we persist it ourselves.
     */
    private suspend fun request(method: String, body: String): String = withContext(Dispatchers.IO) {
        val payload = """{"label_public_key":"${Sdk.LABEL_KEY}","brand_public_key":"${Sdk.BRAND_KEY}",""" +
            body.removePrefix("{")
        val res = http.newCall(
            Request.Builder()
                .url("$SL_SERVER/$method")
                .post(payload.toRequestBody(JSON_TYPE))
                .build(),
        ).execute()
        res.use {
            if (!it.isSuccessful) throw RuntimeException("$method failed: HTTP ${it.code}")
            it.body?.string().orEmpty()
        }
    }

    /** Exchange a provider token for a Smartico user. */
    suspend fun checkProviderToken(ctx: Context, p: ProviderResult): SessionUser {
        val body = json.encodeToString(ProviderResult.serializer(), p)
        val r = json.decodeFromString(ProviderTokenResponse.serializer(), request("checkProviderToken", body))
        val ext = r.user_ext_id
        val cookie = r.cookie_token
        if (ext.isNullOrEmpty() || cookie.isNullOrEmpty()) {
            throw RuntimeException(r.err_message ?: "Login rejected by server")
        }
        android.util.Log.i(TAG, "login ok: user=$ext, session token stored")
        saveCookie(ctx, cookie)
        return SessionUser(ext, p.email, p.profile)
    }

    /** Restore a previous session (called on launch). Null when there is none. */
    suspend fun restore(ctx: Context): SessionUser? {
        val cookie = cookie(ctx) ?: return null
        return try {
            val raw = request("checkCookieToken", """{"cookie_token":"$cookie"}""")
            val r = json.decodeFromString(CookieTokenResponse.serializer(), raw)
            val ext = r.user_ext_id?.takeIf { it.isNotEmpty() }
            if (ext == null) {
                android.util.Log.i(TAG, "session restore: server returned no user ($raw)")
                return null
            }
            SessionUser(ext, r.email, parseProfile(r.profile))
        } catch (e: Exception) {
            android.util.Log.w(TAG, "session restore failed: ${e.message}")
            null
        }
    }

    /** The profile arrives either as a JSON string or as an object. */
    private fun parseProfile(raw: JsonElement?): SocialProfile? = when {
        raw == null || raw is JsonNull -> null
        raw is JsonPrimitive && raw.isString ->
            runCatching { json.decodeFromString(SocialProfile.serializer(), raw.content) }.getOrNull()
        else -> runCatching { json.decodeFromJsonElement(SocialProfile.serializer(), raw) }.getOrNull()
    }

    /** Reset demo progress: the backend re-guids the user behind the same session. */
    suspend fun resetProgress(ctx: Context): SessionUser? {
        val cookie = cookie(ctx) ?: return null
        request("duplicateUser", """{"cookie_token":"$cookie"}""")
        return restore(ctx)
    }

    fun cookie(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(COOKIE_KEY, null)

    private fun saveCookie(ctx: Context, token: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(COOKIE_KEY, token).apply()
    }

    fun clearCookie(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(COOKIE_KEY).apply()
    }
}
