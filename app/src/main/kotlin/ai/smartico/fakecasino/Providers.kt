package ai.smartico.fakecasino

import android.app.Activity
import android.content.Context
import android.util.Base64
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Google sign-in.
 *
 * Google is registered against this app's id AND the SHA-1 of its signing key,
 * which is why the demo deliberately reuses the RN demo's app id and debug
 * keystore (see app/build.gradle.kts).
 */
object Providers {
    // The backend verifies the Google id_token's `aud` against this web client
    // id, so it must be the WEB client — not the Android one.
    private const val GOOGLE_WEB_CLIENT_ID =
        "259966498344-spl923rp3lgokku9km7jckaj5s8ndin8.apps.googleusercontent.com"

    /**
     * Google sign-in through Credential Manager.
     *
     * The legacy GoogleSignIn intent flow is deprecated and on current Play
     * Services its account picker comes up as an empty, hung screen — this is
     * the API Google points to instead, and it draws its own account sheet.
     */
    suspend fun signInGoogle(activity: Activity): Auth.ProviderResult {
        // "Sign in with Google" (not the silent variant): always shows the
        // account chooser, so logging out really means the next login can pick
        // a different account.
        val option = GetSignInWithGoogleOption.Builder(GOOGLE_WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val response = CredentialManager.create(activity).getCredential(activity, request)

        val credential = response.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw RuntimeException("Unexpected credential: ${credential.type}")
        }
        val google = GoogleIdTokenCredential.createFrom(credential.data)

        return Auth.ProviderResult(
            provider_id = Auth.PROVIDER_GGL,
            // the backend keys users on Google's numeric account id, which only
            // exists inside the token — the credential itself exposes the email
            provider_user_id = subjectOf(google.idToken),
            provider_token = google.idToken,
            email = google.id,
            profile = Auth.SocialProfile(
                firstName = google.givenName.orEmpty(),
                lastName = google.familyName.orEmpty(),
                name = google.displayName.orEmpty(),
                picture = google.profilePictureUri?.toString().orEmpty(),
            ),
        )
    }

    /** `sub` claim of a JWT — the stable per-account id. */
    private fun subjectOf(idToken: String): String {
        val parts = idToken.split(".")
        if (parts.size < 2) return ""
        val payload = String(
            Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP),
        )
        return Regex("\"sub\"\\s*:\\s*\"([^\"]+)\"").find(payload)?.groupValues?.get(1).orEmpty()
    }

    /**
     * Forget the Google session on logout, so the next login shows the account
     * chooser instead of silently reusing the previous account.
     */
    suspend fun signOut(ctx: Context) {
        runCatching { CredentialManager.create(ctx).clearCredentialState(ClearCredentialStateRequest()) }
    }
}
