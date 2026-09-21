package ai.smartico.fakecasino

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Login: Google only.
 *
 * The app never invents a user id — Google returns an id_token, the demo
 * backend verifies it and answers with the Smartico `ext_user_id` plus a
 * session token we keep, so the next launch restores the session.
 */
@Composable
fun LoginScreen(onSession: (Auth.SessionUser) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🎰", fontSize = 56.sp)
        Spacer(Modifier.height(8.dp))
        Text("Fakebet", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Meta("Smartico Kotlin SDK demo · ICE env4")
        Spacer(Modifier.height(28.dp))

        BigButton(if (busy) "Signing in…" else "Continue with Google", enabled = !busy) {
            error = null
            val activity = ctx as? ComponentActivity ?: return@BigButton
            scope.launch {
                busy = true
                error = runCatching {
                    onSession(Auth.checkProviderToken(ctx, Providers.signInGoogle(activity)))
                }.exceptionOrNull()?.let { it.message ?: it.toString() }
                busy = false
            }
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Color(0xFFFF6B6B), fontSize = 12.sp)
        }
    }
}

@Composable
private fun BigButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Accent)
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) Color.White else Muted,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
    }
}
