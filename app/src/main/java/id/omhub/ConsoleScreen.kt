package id.omphub

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Console sebagai jendela Terminal macOS: title bar traffic-light, segmented
 * control stdin/RCON, area log gelap bermonospace, dan baris prompt ">" di
 * bawah. Fungsi sama persis seperti sebelumnya (stdin + RCON + bersihkan).
 */
@Composable
fun ConsoleScreen() {
    val lines by Controller.gameLog.lines.collectAsStateWithLifecycle()
    val game by Controller.gameState.collectAsStateWithLifecycle()
    val st by Controller.setupStatus.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var mode by remember { mutableIntStateOf(0) } // 0 buat stdin, 1 buat RCON
    val pressSend = rememberPressSource()
    val pressClear = rememberPressSource()

    fun send() {
        val cmd = input.trim()
        if (cmd.isEmpty()) return
        input = ""
        scope.launch(Dispatchers.IO) {
            if (mode == 0) {
                Controller.sendStdin(cmd)
            } else {
                Controller.gameLog.add("rcon> $cmd")
                val out = Controller.rcon(cmd)
                if (out.isEmpty()) Controller.gameLog.add("[rcon] tidak ada balasan (RCON mati / password salah / server mati)")
                out.forEach { Controller.gameLog.add(it) }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        androidx.compose.animation.AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 6 }
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Mac.Panel),
                border = androidx.compose.foundation.BorderStroke(1.dp, Mac.Border.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    Modifier.fillMaxSize().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Title bar jendela Terminal.
                    WindowTitleBar("Terminal — ${st.engine.label}") {
                        IconButton(
                            onClick = { Controller.gameLog.clear() },
                            modifier = Modifier.pressScale(pressClear),
                            interactionSource = pressClear
                        ) { Icon(Icons.Default.Delete, "Bersihkan", tint = Mac.Secondary) }
                    }
                    // Segmented control stdin / RCON.
                    MacSegmented(
                        options = listOf("stdin", "RCON"),
                        selected = mode,
                        onSelect = { mode = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "${st.engine.label}: ${game.name.lowercase()}",
                        color = Mac.Secondary,
                        fontSize = 11.sp
                    )
                    // Area log gelap bermonospace.
                    Box(
                        Modifier.weight(1f).fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Mac.TerminalBg)
                            .padding(12.dp)
                    ) {
                        LogView(
                            lines,
                            Modifier.fillMaxSize(),
                            textColor = Color(0xFFD7D7D7)
                        )
                    }
                    // Baris prompt.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            ">",
                            color = Mac.Green,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text(if (mode == 0) "perintah konsol" else "perintah RCON") },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                        )
                        IconButton(
                            onClick = { send() },
                            enabled = input.isNotBlank(),
                            modifier = Modifier.pressScale(pressSend),
                            interactionSource = pressSend
                        ) { Icon(Icons.Default.Send, "Kirim", tint = Mac.Blue) }
                    }
                }
            }
        }
    }
}
