package id.omphub

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun DashboardScreen() {
    val ctx = LocalContext.current
    val game by Controller.gameState.collectAsStateWithLifecycle()
    val db by Controller.dbState.collectAsStateWithLifecycle()
    val st by Controller.setupStatus.collectAsStateWithLifecycle()
    val perf by PerfMonitor.snapshot.collectAsStateWithLifecycle()
    var queryMs by remember { mutableStateOf<Long?>(null) }
    var info by remember { mutableStateOf<Samp.Info?>(null) }
    var players by remember { mutableStateOf(emptyList<Samp.Player>()) }

    LaunchedEffect(Unit) {
        PerfMonitor.ensureStarted()
        withContext(Dispatchers.IO) { Controller.refreshStatus() }
    }
    LaunchedEffect(game) {
        while (game == RunState.RUNNING) {
            val result = withContext(Dispatchers.IO) {
                val started = System.nanoTime()
                val serverInfo = Samp.info(Prefs.port)
                val elapsed = (System.nanoTime() - started) / 1_000_000
                Triple(serverInfo, Samp.players(Prefs.port), if (serverInfo != null) elapsed else null)
            }
            info = result.first
            players = result.second
            queryMs = result.third
            delay(3000)
        }
        info = null
        players = emptyList()
        queryMs = null
    }

    val running = game == RunState.RUNNING
    val usedRam = (perf.memTotalKb - perf.memAvailKb).coerceAtLeast(0)
    val ramPct = if (perf.memTotalKb > 0) usedRam * 100f / perf.memTotalKb else 0f
    val diskUsed = (perf.diskTotalB - perf.diskFreeB).coerceAtLeast(0)
    val diskPct = if (perf.diskTotalB > 0) diskUsed * 100f / perf.diskTotalB else 0f
    val cpuPct = (perf.game?.cpuPct ?: 0f).coerceIn(0f, 100f)

    Column(
        Modifier.fillMaxSize().background(Mac.Background).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TrafficLights()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("OmpHub", color = Mac.Text, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("MONITORING OVERVIEW", color = Mac.Secondary, fontSize = 10.sp, letterSpacing = 1.5.sp)
            }
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Mac.Panel),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.NotificationsNone, contentDescription = "Notifikasi", tint = Mac.Secondary)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Resource usage", color = Mac.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("LIVE • 2s", color = Mac.Green, fontSize = 10.sp, letterSpacing = 1.sp)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Mac.Panel)
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ResourceGauge("CPU", cpuPct, Mac.Blue, if (running) "Server" else "Idle")
                    ResourceGauge("RAM", ramPct, Mac.Green, if (perf.memTotalKb > 0) fmtMem(usedRam) else "—")
                    ResourceGauge("DISK", diskPct, Mac.Yellow, if (perf.diskTotalB > 0) fmtBytes(diskUsed) else "—")
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Mac.PanelInner).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (running) Mac.Green else Mac.Secondary))
                    Spacer(Modifier.width(8.dp))
                    Text(if (running) "All systems operational" else "Server is currently offline", color = Mac.Text, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Text(queryMs?.let { "$it ms" } ?: "—", color = Mac.Blue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Mac.Panel)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("SERVER STATUS", color = Mac.Secondary, fontSize = 10.sp, letterSpacing = 1.3.sp)
                        Text(if (running) "Server berjalan" else "Server berhenti", color = if (running) Mac.Green else Mac.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(st.engine.label, color = Mac.Blue, fontSize = 10.sp, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Mac.Blue.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 6.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { ServerService.send(ctx, ServerService.ACTION_START) },
                        enabled = st.ready && game == RunState.STOPPED,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Mac.Blue, contentColor = Color.White),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text("Start", fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = { ServerService.send(ctx, ServerService.ACTION_STOP) },
                        enabled = game != RunState.STOPPED || db != RunState.STOPPED,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Text("Stop")
                    }
                    OutlinedButton(
                        onClick = { ServerService.send(ctx, ServerService.ACTION_RESTART) },
                        enabled = running,
                        shape = RoundedCornerShape(20.dp)
                    ) { Icon(Icons.Default.Refresh, contentDescription = "Restart") }
                }
                if (!st.ready) {
                    Text("Mesin server belum siap. Buka tab Setup untuk mengunduh open.mp atau SA-MP.", color = Mac.Secondary, fontSize = 11.sp)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SmallStat("PLAYERS ONLINE", if (running && info != null) "${info!!.players}/${info!!.maxPlayers}" else "—", Modifier.weight(1f), Mac.Blue)
            SmallStat("QUERY PING", queryMs?.let { "$it ms" } ?: "—", Modifier.weight(1f), Mac.Green)
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Mac.Panel)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text("Server information", color = Mac.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                val serverInfo = info
                Text(if (running && serverInfo != null) serverInfo.name else if (running) "Menunggu respons query UDP…" else "Server belum aktif", color = Mac.Text, fontSize = 13.sp)
                InfoLine("Engine", st.engine.label)
                InfoLine("UDP port", Prefs.port.toString())
                InfoLine("Gamemode", serverInfo?.mode ?: "—")
                InfoLine("Language", serverInfo?.language ?: "—")
                val ips = localIps()
                if (ips.isNotEmpty()) InfoLine("LAN address", ips.joinToString { "$it:${Prefs.port}" })
                InfoLine("Local", "127.0.0.1:${Prefs.port}")
            }
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Mac.Panel)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Device performance", color = Mac.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                PerfContent(perf, queryMs)
            }
        }

        if (running) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Mac.Panel)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Online players (${players.size})", color = Mac.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    if (players.isEmpty()) Text("Belum ada pemain online.", color = Mac.Secondary, fontSize = 12.sp)
                    players.forEach { player ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(player.name, Modifier.weight(1f), color = Mac.Text, fontSize = 13.sp)
                            Text("${player.score} • ${player.ping} ms", color = Mac.Secondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ResourceGauge(label: String, value: Float, color: Color, detail: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawArc(Mac.Track, -90f, 360f, false, style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round))
                drawArc(color, -90f, (value.coerceIn(0f, 100f) / 100f) * 360f, false, style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round))
            }
            Text("${value.toInt()}%", color = Mac.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Text(label, color = Mac.Text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        Text(detail, color = Mac.Secondary, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun SmallStat(label: String, value: String, modifier: Modifier, accent: Color) {
    Card(modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Mac.Panel)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, color = Mac.Secondary, fontSize = 9.sp, letterSpacing = 1.sp)
            Text(value, color = accent, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Mac.Secondary, fontSize = 12.sp)
        Text(value, color = Mac.Text, fontSize = 12.sp)
    }
}
