package ai.smartico.fakecasino

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/**
 * Cashier, ported from the web fake-casino: quick amounts, cosmetic payment
 * methods, deposit fires `acc_deposit_approved` to the demo backend. Withdraw
 * is cosmetic there too — it fires nothing.
 */
@Composable
fun CashierScreen(onClose: () -> Unit) {
    val balance by Economy.balance.collectAsState()
    var amount by remember { mutableIntStateOf(50) }
    var method by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf<String?>(null) }
    val methods = listOf("💳 Master Card", "💳 Visa", "🏦 Via Bank", "💸 Wise")

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Cashier", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("✕", color = Muted, fontSize = 20.sp, modifier = Modifier.clickable { onClose() })
        }
        Meta("Current balance  €${"%.2f".format(balance)}")

        Card {
            Title("Amount")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(50, 100, 500, 1000).forEach { q ->
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1A1B30))
                            .border(1.dp, if (amount == q) Accent else Color(0xFF2A2B45), RoundedCornerShape(10.dp))
                            .clickable { amount = q }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("€$q", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("🎁 Get an extra 180% bonus on deposits of €500+", color = Color(0xFFFFD479), fontSize = 12.sp)
        }

        Card {
            Title("Payment method")
            Spacer(Modifier.height(8.dp))
            methods.forEachIndexed { i, m ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { method = i }
                        .padding(vertical = 6.dp),
                ) {
                    Text(if (method == i) "◉  $m" else "○  $m", color = Color.White, fontSize = 13.sp)
                }
            }
        }

        ActionButton("Deposit €$amount", onResult = { note = it }) {
            Economy.reportDeposit(amount)
            "Deposit sent — balance updates in a moment ✓"
        }
        note?.let { Meta(it) }
    }
}
