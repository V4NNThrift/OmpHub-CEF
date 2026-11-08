package id.omphub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ConsoleScreen() {
    val lines by Controller.gameLog.lines.collectAsStateWithLifecycle()
    val game by Controller.gameState.collectAsStateWithLifecycle()
    val st by Controller.setupStatus.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var mode by remember { mutableIntStateOf(0) } // 0 buat stdin, 1 buat RCON

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

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("stdin") })
            FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("RCON") })
            Text("${st.engine.label}: ${game.name.lowercase()}", modifier = Modifier.weight(1f))
            IconButton(onClick = { Controller.gameLog.clear() }) { Icon(Icons.Default.Delete, "Bersihkan") }
        }
        LogView(lines, Modifier.weight(1f).fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(if (mode == 0) "perintah konsol" else "perintah RCON") }
            )
            IconButton(onClick = { send() }) { Icon(Icons.Default.Send, "Kirim") }
        }
    }
}
