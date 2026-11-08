package id.omphub

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val game by Controller.gameState.collectAsStateWithLifecycle()
    val db by Controller.dbState.collectAsStateWithLifecycle()
    val st by Controller.setupStatus.collectAsStateWithLifecycle()
    val perf by PerfMonitor.snapshot.collectAsStateWithLifecycle()
    val gameLines by Controller.gameLog.lines.collectAsStateWithLifecycle()
    var queryMs by remember { mutableStateOf<Long?>(null) }
    var info by remember { mutableStateOf<Samp.Info?>(null) }
    var players by remember { mutableStateOf(emptyList<Samp.DetailedPlayer>()) }
    var showQr by remember { mutableStateOf<String?>(null) }
    var confirmAction by remember { mutableStateOf<Pair<String, Samp.DetailedPlayer>?>(null) }

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
                val detailed = Samp.playersDetailed(Prefs.port)
                val list = if (detailed.isNotEmpty()) detailed
                    else Samp.players(Prefs.port).map { Samp.DetailedPlayer(-1, it.name, it.score, it.ping) }
                Triple(serverInfo, list, if (serverInfo != null) elapsed else null)
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

    // Nilai gauge bergerak halus (bukan loncat tiap 2 detik).
    val cpuAnim by animateFloatAsState(cpuPct, macSpringFloat(), label = "cpu")
    val ramAnim by animateFloatAsState(ramPct, macSpringFloat(), label = "ram")
    val diskAnim by animateFloatAsState(diskPct, macSpringFloat(), label = "disk")

    // Sumber interaksi untuk efek tekan tombol.
    val pressStart = rememberPressSource()
    val pressStop = rememberPressSource()
    val pressRestart = rememberPressSource()
    val pressQr = rememberPressSource()
    val pressShare = rememberPressSource()
    val pressCopy = rememberPressSource()

    // Tangkap sekali supaya tidak ada `!!` pada state yang bisa berubah.
    val infoSnapshot = info

    Column(
        Modifier.fillMaxSize().background(Mac.Background).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        StaggeredItem(0, Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrafficLights()
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("OmpHub", color = Mac.Text, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                    Text("MONITORING OVERVIEW", color = Mac.Secondary, fontSize = 10.sp, letterSpacing = 1.5.sp)
                }
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Mac.Panel),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.NotificationsNone, contentDescription = "Notifikasi", tint = Mac.Secondary)
                }
            }
        }

        StaggeredItem(1, Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Resource usage", color = Mac.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("LIVE • 2s", color = Mac.Green, fontSize = 10.sp, letterSpacing = 1.sp)
            }
        }

        StaggeredItem(2, Modifier.fillMaxWidth()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Mac.Panel)
            ) {
                Column(Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ResourceGauge("CPU", cpuAnim, Mac.Blue, if (running) "Server" else "Idle")
                        ResourceGauge("RAM", ramAnim, Mac.Green, if (perf.memTotalKb > 0) fmtMem(usedRam) else "—")
                        ResourceGauge("DISK", diskAnim, Mac.Yellow, if (perf.diskTotalB > 0) fmtBytes(diskUsed) else "—")
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Mac.PanelInner).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (running) PulsingDot(Mac.Green)
                        else Box(Modifier.size(7.dp).clip(CircleShape).background(Mac.Secondary))
                        Spacer(Modifier.width(8.dp))
                        Text(if (running) "All systems operational" else "Server is currently offline", color = Mac.Text, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Text(queryMs?.let { "$it ms" } ?: "—", color = Mac.Blue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        StaggeredItem(3, Modifier.fillMaxWidth()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Mac.Panel)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("SERVER STATUS", color = Mac.Secondary, fontSize = 10.sp, letterSpacing = 1.3.sp)
                            AnimatedContent(
                                targetState = running,
                                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                                label = "serverStatus"
                            ) { isRunning ->
                                Text(
                                    if (isRunning) "Server berjalan" else "Server berhenti",
                                    color = if (isRunning) Mac.Green else Mac.Text,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Text(st.engine.label, color = Mac.Blue, fontSize = 10.sp, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Mac.Blue.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { ServerService.send(ctx, ServerService.ACTION_START) },
                            enabled = st.ready && game == RunState.STOPPED,
                            modifier = Modifier.weight(1f).pressScale(pressStart),
                            interactionSource = pressStart,
                            colors = ButtonDefaults.buttonColors(containerColor = Mac.Blue, contentColor = Color.White),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text("Start", fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = { ServerService.send(ctx, ServerService.ACTION_STOP) },
                            enabled = game != RunState.STOPPED || db != RunState.STOPPED,
                            modifier = Modifier.weight(1f).pressScale(pressStop),
                            interactionSource = pressStop,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Text("Stop")
                        }
                        OutlinedButton(
                            onClick = { ServerService.send(ctx, ServerService.ACTION_RESTART) },
                            enabled = running,
                            modifier = Modifier.pressScale(pressRestart),
                            interactionSource = pressRestart,
                            shape = RoundedCornerShape(20.dp)
                        ) { Icon(Icons.Default.Refresh, contentDescription = "Restart") }
                    }
                    if (!st.ready) {
                        Text("Mesin server belum siap. Buka tab Setup untuk mengunduh open.mp atau SA-MP.", color = Mac.Secondary, fontSize = 11.sp)
                    }
                }
            }
        }

        StaggeredItem(4, Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SmallStat("PLAYERS ONLINE", if (running && infoSnapshot != null) "${infoSnapshot.players}/${infoSnapshot.maxPlayers}" else "—", Modifier.weight(1f), Mac.Blue)
                SmallStat("QUERY PING", queryMs?.let { "$it ms" } ?: "—", Modifier.weight(1f), Mac.Green)
            }
        }

        StaggeredItem(5, Modifier.fillMaxWidth()) {
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
                    val lanAddr = primaryLanAddress()
                    if (lanAddr != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { showQr = lanAddr },
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.weight(1f).pressScale(pressQr),
                                interactionSource = pressQr
                            ) { Icon(Icons.Default.QrCode, contentDescription = null); Text("QR") }
                            OutlinedButton(
                                onClick = { shareServerAddress(ctx, lanAddr) },
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.weight(1f).pressScale(pressShare),
                                interactionSource = pressShare
                            ) { Icon(Icons.Default.Share, contentDescription = null); Text("Bagikan") }
                            OutlinedButton(
                                onClick = { copyText(ctx, "Alamat server", lanAddr) },
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.weight(1f).pressScale(pressCopy),
                                interactionSource = pressCopy
                            ) { Text("Salin") }
                        }
                    }
                }
            }
        }

        if (st.engine == ServerType.OMP) {
            StaggeredItem(6, Modifier.fillMaxWidth()) {
                CefStatusCard(running, gameLines)
            }
        }

        StaggeredItem(7, Modifier.fillMaxWidth()) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Mac.Panel)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Device performance", color = Mac.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    PerfContent(perf, queryMs)
                }
            }
        }

        if (running) {
            StaggeredItem(8, Modifier.fillMaxWidth()) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Mac.Panel)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Online players (${players.size})", color = Mac.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        if (players.isEmpty()) Text("Belum ada pemain online.", color = Mac.Secondary, fontSize = 12.sp)
                        players.forEach { player ->
                            key(player.id) {
                                AnimatedVisibility(
                                    visible = true,
                                    enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 3 },
                                    exit = fadeOut(tween(150))
                                ) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(player.name, color = Mac.Text, fontSize = 13.sp)
                                            Text("${player.score} • ${player.ping} ms", color = Mac.Secondary, fontSize = 11.sp)
                                        }
                                        if (player.id >= 0) {
                                            TextButton(onClick = { confirmAction = "kick" to player }) {
                                                Text("Kick", color = Mac.Blue, fontSize = 12.sp)
                                            }
                                            TextButton(onClick = { confirmAction = "ban" to player }) {
                                                Text("Ban", color = Mac.Red, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    // Dialog QR alamat server.
    showQr?.let { addr ->
        AnimatedAlertDialog(
            onDismissRequest = { showQr = null },
            title = { Text("Scan untuk join") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val bmp = remember(addr) { qrBitmap(addr, 512) }
                    if (bmp != null) {
                        Image(
                            bmp.asImageBitmap(),
                            contentDescription = "QR alamat server",
                            modifier = Modifier.size(230.dp).clip(RoundedCornerShape(12.dp))
                                .background(Color.White).padding(8.dp)
                        )
                    } else {
                        Text("Gagal membuat QR. Coba lagi.", color = Mac.Red, fontSize = 13.sp)
                    }
                    Text(addr, color = Mac.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Satu WiFi yang sama dengan HP ini.", color = Mac.Secondary, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { shareServerAddress(ctx, addr) }) { Text("Bagikan") }
            },
            dismissButton = {
                TextButton(onClick = { showQr = null }) { Text("Tutup") }
            }
        )
    }

    // Dialog konfirmasi kick / ban (via RCON).
    confirmAction?.let { (action, player) ->
        AnimatedAlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text("${action.replaceFirstChar { it.uppercase() }} ${player.name}?") },
            text = {
                Text(
                    if (action == "ban") "Pemain akan di-ban dari server (masuk bans.json)."
                    else "Pemain akan dikeluarkan dari server."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmAction = null
                    scope.launch(Dispatchers.IO) {
                        val out = Controller.rcon("$action ${player.id}")
                        val reply = out.firstOrNull()?.take(120)
                        toast(ctx, "Perintah $action dikirim ke ${player.name}" + (reply?.let { ": $it" } ?: ""))
                    }
                }) {
                    Text(
                        action.replaceFirstChar { it.uppercase() },
                        color = if (action == "ban") Mac.Red else Mac.Blue
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) { Text("Batal") }
            }
        )
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

/**
 * Status CEF: port 7060 (dari log server), ukuran + jumlah file fivem.pak
 * (dari fivem.pak.manifest), dan waktu pack terakhir (timestamp file pak).
 * Hanya tampil kalau server open.mp dan file pak ada.
 */
@Composable
private fun CefStatusCard(running: Boolean, gameLines: List<String>) {
    val pak = File(Env.serverDir, "scriptfiles/cef/fivem.pak")
    if (!pak.exists()) return
    val manifest = File(Env.serverDir, "scriptfiles/cef/fivem.pak.manifest")
    var fileCount by remember { mutableStateOf<Int?>(null) }
    var cefSeen by remember { mutableStateOf(false) }
    LaunchedEffect(pak.lastModified()) {
        fileCount = withContext(Dispatchers.IO) {
            runCatching { JSONObject(manifest.readText()).getJSONObject("files").length() }.getOrNull()
        }
    }
    LaunchedEffect(running) { if (!running) cefSeen = false }
    LaunchedEffect(gameLines) {
        if (gameLines.any { it.contains("Network Server started successfully on port 7060") }) cefSeen = true
    }
    val status = when {
        !running -> "Server berhenti"
        cefSeen -> "CEF OK • port 7060"
        else -> "Menunggu CEF…"
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Mac.Panel)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text("CEF", color = Mac.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            AnimatedContent(
                targetState = status,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label = "cefStatus"
            ) { s ->
                Text(s, color = if (running && cefSeen) Mac.Green else Mac.Text, fontSize = 13.sp)
            }
            InfoLine("fivem.pak", "${fileCount?.let { "$it file" } ?: "—"} • ${humanSize(pak.length())}")
            InfoLine("Pack terakhir", fmtPakTime(pak.lastModified()))
        }
    }
}

private fun fmtPakTime(ms: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale("id")).format(Date(ms))
