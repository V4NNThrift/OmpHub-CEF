package id.omphub

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.asImageBitmap
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
    val playerCount = info?.players ?: 0
    val playerMax = info?.maxPlayers ?: 0
    val playerPct = if (playerMax > 0) playerCount * 100f / playerMax else 0f

    // Nilai widget bergerak halus (bukan loncat tiap 3 detik).
    val cpuAnim by animateFloatAsState(cpuPct, macSpringFloat(), label = "cpu")
    val ramAnim by animateFloatAsState(ramPct, macSpringFloat(), label = "ram")
    val diskAnim by animateFloatAsState(diskPct, macSpringFloat(), label = "disk")
    val playerAnim by animateFloatAsState(playerPct, macSpringFloat(), label = "players")

    // Sumber interaksi untuk efek tekan tombol.
    val pressPower = rememberPressSource()
    val pressRestart = rememberPressSource()
    val pressQr = rememberPressSource()
    val pressShare = rememberPressSource()
    val pressCopy = rememberPressSource()
    val pressCopyTs = rememberPressSource()

    // Tangkap sekali supaya tidak ada `!!` pada state yang bisa berubah.
    val infoSnapshot = info
    val lanAddr = primaryLanAddress()
    val serverName = if (running && infoSnapshot != null) infoSnapshot.name
        else if (running) "Menunggu query…"
        else "Server belum aktif"

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 0 — Hero "machine": unit server gaya Mac mini + tombol power besar.
        StaggeredItem(0, Modifier.fillMaxWidth()) {
            FrostCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Unit Mac mini: bodi + lampu power.
                    Box(
                        Modifier.size(width = 96.dp, height = 30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Mac.PanelInner),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            Modifier.padding(start = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (running) PulsingDot(Mac.Green)
                            else Box(Modifier.size(7.dp).clip(CircleShape).background(Mac.Secondary))
                            Box(
                                Modifier.width(34.dp).height(3.dp)
                                    .clip(RoundedCornerShape(2.dp)).background(Mac.Border)
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        st.engine.label,
                        color = Mac.Blue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp))
                            .background(Mac.Blue.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(serverName, color = Mac.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    AnimatedContent(
                        targetState = running,
                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                        label = "serverStatus"
                    ) { isRunning ->
                        Text(
                            if (isRunning) {
                                "Berjalan" + if (Controller.gameStartedAt > 0)
                                    " • Uptime " + fmtUptime(SystemClock.elapsedRealtime() - Controller.gameStartedAt)
                                else ""
                            } else "Berhenti",
                            color = if (isRunning) Mac.Green else Mac.Secondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Tombol power bulat besar: Start saat berhenti, Stop saat jalan.
                    IconButton(
                        onClick = {
                            if (running) ServerService.send(ctx, ServerService.ACTION_STOP)
                            else ServerService.send(ctx, ServerService.ACTION_START)
                        },
                        enabled = (game == RunState.STOPPED && st.ready) ||
                            game != RunState.STOPPED || db != RunState.STOPPED,
                        modifier = Modifier.size(56.dp).pressScale(pressPower),
                        interactionSource = pressPower
                    ) {
                        Box(
                            Modifier.size(56.dp).clip(CircleShape)
                                .background(if (running) Mac.Red else Mac.Blue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = if (running) "Stop" else "Start",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { ServerService.send(ctx, ServerService.ACTION_RESTART) },
                                enabled = running,
                                modifier = Modifier.pressScale(pressRestart),
                                interactionSource = pressRestart,
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Restart", fontSize = 12.sp)
                            }
                            if (lanAddr != null) {
                                OutlinedButton(
                                    onClick = { showQr = lanAddr },
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.pressScale(pressQr),
                                    interactionSource = pressQr
                                ) {
                                    Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("QR", fontSize = 12.sp)
                                }
                            }
                        }
                        if (lanAddr != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { shareServerAddress(ctx, lanAddr) },
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.pressScale(pressShare),
                                    interactionSource = pressShare
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("Bagikan", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { copyText(ctx, "Alamat server", lanAddr) },
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.pressScale(pressCopy),
                                    interactionSource = pressCopy
                                ) { Text("Salin", fontSize = 12.sp) }
                            }
                        }
                    }
                }
                if (!st.ready) {
                    Text(
                        "Mesin server belum siap. Buka Setup untuk mengunduh open.mp atau SA-MP.",
                        color = Mac.Secondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // 1 — Widget grid 2 kolom: CPU / RAM / Disk / Pemain.
        StaggeredItem(1, Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatWidget(
                        "CPU", "${cpuAnim.toInt()}%", cpuAnim, Mac.Blue,
                        if (running) "Server" else "Idle", Modifier.weight(1f)
                    )
                    StatWidget(
                        "RAM", "${ramAnim.toInt()}%", ramAnim, Mac.Green,
                        if (perf.memTotalKb > 0) fmtMem(usedRam) else "—", Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatWidget(
                        "DISK", "${diskAnim.toInt()}%", diskAnim, Mac.Yellow,
                        if (perf.diskTotalB > 0) fmtBytes(diskUsed) else "—", Modifier.weight(1f)
                    )
                    StatWidget(
                        "PEMAIN", if (running && infoSnapshot != null) "$playerCount/$playerMax" else "—",
                        playerAnim, Mac.Blue,
                        if (running) "online" else "offline", Modifier.weight(1f)
                    )
                }
            }
        }

        // 2 — About This Mac: info sistem + alamat + status CEF.
        StaggeredItem(2, Modifier.fillMaxWidth()) {
            FrostCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("About This Mac", color = Mac.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        "QUERY ${queryMs?.let { "$it ms" } ?: "—"}",
                        color = Mac.Blue, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp))
                            .background(Mac.Blue.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                InfoLine("Engine", st.engine.label)
                InfoLine("UDP port", Prefs.port.toString())
                if (running && Controller.gameStartedAt > 0) {
                    InfoLine("Uptime", fmtUptime(SystemClock.elapsedRealtime() - Controller.gameStartedAt))
                }
                InfoLine("Gamemode", infoSnapshot?.mode ?: "—")
                InfoLine("Language", infoSnapshot?.language ?: "—")
                val ips = localIps()
                if (ips.isNotEmpty()) InfoLine("LAN address", ips.joinToString { "$it:${Prefs.port}" })
                val tsIp = tailscaleIp()
                if (tsIp != null) {
                    val tsAddr = "$tsIp:${Prefs.port}"
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Tailscale", Modifier.weight(1f), color = Mac.Secondary, fontSize = 12.sp)
                        Text(tsAddr, color = Mac.Text, fontSize = 12.sp)
                        TextButton(
                            onClick = { copyText(ctx, "Alamat Tailscale", tsAddr) },
                            modifier = Modifier.pressScale(pressCopyTs),
                            interactionSource = pressCopyTs
                        ) { Text("Salin", color = Mac.Blue, fontSize = 12.sp) }
                    }
                    Text("Teman satu Tailnet bisa join dari mana saja.", color = Mac.Secondary, fontSize = 11.sp)
                }
                InfoLine("Local", "127.0.0.1:${Prefs.port}")
            }
        }

        // 3 — Status CEF (hanya open.mp).
        if (st.engine == ServerType.OMP) {
            StaggeredItem(3, Modifier.fillMaxWidth()) {
                CefStatusCard(running, gameLines)
            }
        }

        // 4 — Jendela pemain online.
        if (running) {
            StaggeredItem(4, Modifier.fillMaxWidth()) {
                FrostCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Pemain Online", color = Mac.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(
                            "${players.size}",
                            color = Mac.Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(20.dp))
                                .background(Mac.Blue.copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                    if (players.isEmpty()) {
                        Text("Belum ada pemain online.", color = Mac.Secondary, fontSize = 12.sp)
                    }
                    players.forEach { player ->
                        key(player.id) {
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 3 },
                                exit = fadeOut(tween(150))
                            ) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    // Avatar lingkaran dengan inisial.
                                    Box(
                                        Modifier.size(36.dp).clip(CircleShape)
                                            .background(avatarColor(player.id)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            player.name.firstOrNull()?.uppercase() ?: "?",
                                            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(player.name, color = Mac.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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

        // 5 — Performa perangkat.
        StaggeredItem(5, Modifier.fillMaxWidth()) {
            FrostCard {
                Text("Performa Perangkat", color = Mac.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                PerfContent(perf, queryMs)
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

/** Warna avatar dari id pemain (putar di antara 4 warna aksen macOS). */
private fun avatarColor(id: Int): Color = when ((id % 4 + 4) % 4) {
    0 -> Color(0xFF0A84FF)
    1 -> Color(0xFF30D158)
    2 -> Color(0xFFFF9F0A)
    else -> Color(0xFFFF453A)
}

/** Widget kecil frosted: label + angka besar + mini bar + detail. */
@Composable
private fun StatWidget(
    label: String,
    valueText: String,
    pct: Float,
    color: Color,
    detail: String,
    modifier: Modifier = Modifier
) {
    FrostCard(modifier) {
        Text(label, color = Mac.Secondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        Text(valueText, color = Mac.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        LinearProgressIndicator(
            progress = { (pct / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = color,
            trackColor = Mac.Track
        )
        Text(detail, color = Mac.Secondary, fontSize = 11.sp, maxLines = 1)
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
    FrostCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CEF", color = Mac.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (running && cefSeen) PulsingDot(Mac.Green)
        }
        AnimatedContent(
            targetState = status,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
            label = "cefStatus"
        ) { s ->
            Text(s, color = if (running && cefSeen) Mac.Green else Mac.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        InfoLine("fivem.pak", "${fileCount?.let { "$it file" } ?: "—"} • ${humanSize(pak.length())}")
        InfoLine("Pack terakhir", fmtPakTime(pak.lastModified()))
    }
}

private fun fmtPakTime(ms: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale("id")).format(Date(ms))
