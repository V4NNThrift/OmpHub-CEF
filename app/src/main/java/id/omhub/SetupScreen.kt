package id.omphub

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Setup sebagai System Settings macOS: section ber-header dengan baris
 * berikon (kotak rounded berwarna) + kontrol di kanan. Semua fungsi lama
 * tetap ada (mesin server, tema, otomatisasi, update, backup, URL, log).
 */
@Composable
fun SetupScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st by Controller.setupStatus.collectAsStateWithLifecycle()
    val busy by Controller.setupBusy.collectAsStateWithLifecycle()
    val log by Controller.setupLog.lines.collectAsStateWithLifecycle()
    val game by Controller.gameState.collectAsStateWithLifecycle()
    var sampVer by remember { mutableStateOf(Prefs.sampVersion) }
    var openMpUrl by remember { mutableStateOf(Prefs.urlOpenMp) }
    var sampUrl by remember { mutableStateOf(Prefs.urlSamp) }
    var sampDlUrl by remember { mutableStateOf(Prefs.urlSampDl) }
    var backups by remember { mutableStateOf(Controller.listBackups()) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var restoreTarget by remember { mutableStateOf<File?>(null) }
    val upd by Updater.state.collectAsStateWithLifecycle()
    val darkTheme by ThemeState.dark.collectAsStateWithLifecycle()
    var watchdog by remember { mutableStateOf(Prefs.watchdogEnabled) }
    var bootAuto by remember { mutableStateOf(Prefs.bootAutoStart) }
    var notifyEv by remember { mutableStateOf(Prefs.notifyEvents) }
    val pressInstall = rememberPressSource()
    val pressCheckUpd = rememberPressSource()
    val pressBackup = rememberPressSource()

    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val tmp = File(Env.tmpDir, "restore-picked.zip")
                runCatching {
                    ctx.contentResolver.openInputStream(uri)?.use { i ->
                        tmp.outputStream().use { o -> i.copyTo(o) }
                    } ?: error("Tidak bisa membaca file")
                }.onSuccess {
                    withContext(Dispatchers.Main) { restoreTarget = tmp }
                }.onFailure {
                    toast(ctx, "Gagal membaca file: ${it.message}")
                }
            }
        }
    }

    LaunchedEffect(Unit) { Controller.refreshStatus() }
    // Daftar backup + status dibaca di IO supaya UI tidak nge-freeze.
    LaunchedEffect(busy, refreshTick) {
        withContext(Dispatchers.IO) { Controller.refreshStatus(); backups = Controller.listBackups() }
    }

    val isSamp = st.engine == ServerType.SAMP
    val serverName = if (isSamp) "SA-MP ${sampVer.label}" else "open.mp + CEF 7058"
    val installedVersion = remember(st, sampVer, busy) {
        if (isSamp) SampInstaller.installedVersion() else null
    }
    val installed = st.serverInstalled
    val readyToInstall = st.binaries && st.gameRootfs && !busy && game == RunState.STOPPED

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StaggeredItem(0, Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Pengaturan", style = MaterialTheme.typography.headlineMedium, color = Mac.Text)
                Text(
                    "Atur mesin server dan URL unduhan open.mp + CEF 7058 atau SA-MP.",
                    style = MaterialTheme.typography.bodyMedium, color = Mac.Secondary
                )
            }
        }

        // Section: Server.
        SectionHeader("Server")
        StaggeredItem(1, Modifier.fillMaxWidth()) {
            FrostCard {
                MacSegmented(
                    options = ServerType.entries.map { it.label },
                    selected = ServerType.entries.indexOf(st.engine),
                    onSelect = { i -> Prefs.serverType = ServerType.entries[i]; Controller.refreshStatus() },
                    enabled = game == RunState.STOPPED,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isSamp) {
                    Text("Versi SA-MP", style = MaterialTheme.typography.titleSmall, color = Mac.Text)
                    MacSegmented(
                        options = SampVersion.entries.map { it.label },
                        selected = SampVersion.entries.indexOf(sampVer),
                        onSelect = { i -> sampVer = SampVersion.entries[i]; Prefs.sampVersion = sampVer },
                        enabled = game == RunState.STOPPED,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Mac.PanelInner
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("STATUS INSTALASI", style = MaterialTheme.typography.labelSmall, color = Mac.Secondary)
                        Text(
                            when {
                                !st.binaries -> "Komponen runtime belum tersedia di APK"
                                !st.gameRootfs -> "Sistem dasar server belum disiapkan"
                                isSamp && installedVersion == null -> "SA-MP belum diunduh"
                                isSamp && installedVersion != sampVer -> "Terpasang SA-MP ${installedVersion?.label}; versi pilihan berbeda"
                                installed -> "$serverName siap digunakan"
                                else -> "$serverName belum diunduh"
                            },
                            style = MaterialTheme.typography.bodyMedium, color = Mac.Text
                        )
                    }
                }
                Button(
                    onClick = { Controller.installServer() },
                    enabled = readyToInstall,
                    modifier = Modifier.fillMaxWidth().pressScale(pressInstall),
                    interactionSource = pressInstall,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                    Spacer(Modifier.padding(start = 4.dp))
                    Text(if (installed) "Update $serverName" else "Unduh $serverName")
                }
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(22.dp))
                        Text("Sedang menyiapkan server…", style = MaterialTheme.typography.bodySmall, color = Mac.Text)
                    }
                }
                if (game != RunState.STOPPED) {
                    Text("Hentikan server sebelum mengganti atau memperbarui mesin server.", color = Mac.Red, style = MaterialTheme.typography.bodySmall)
                }
                if (!st.binaries || !st.gameRootfs) {
                    Text("Runtime dan sistem dasar disiapkan otomatis saat pertama kali aplikasi dijalankan. Pastikan proses persiapan awal sudah selesai.", style = MaterialTheme.typography.bodySmall, color = Mac.Secondary)
                }
            }
        }

        // Section: Otomatisasi.
        SectionHeader("Otomatisasi")
        StaggeredItem(2, Modifier.fillMaxWidth()) {
            FrostCard {
                SettingRow(
                    Icons.Default.Refresh, Mac.Blue,
                    "Restart otomatis (watchdog)",
                    "Server dinyalakan lagi kalau mati sendiri. Maks 5x dalam 10 menit."
                ) {
                    Switch(checked = watchdog, onCheckedChange = { watchdog = it; Prefs.watchdogEnabled = it })
                }
                HorizontalDivider(color = Mac.Border.copy(alpha = 0.4f))
                SettingRow(
                    Icons.Default.PowerSettingsNew, Mac.Green,
                    "Start otomatis saat HP nyala",
                    "Server jalan sendiri setelah HP selesai restart."
                ) {
                    Switch(checked = bootAuto, onCheckedChange = { bootAuto = it; Prefs.bootAutoStart = it })
                }
                HorizontalDivider(color = Mac.Border.copy(alpha = 0.4f))
                SettingRow(
                    Icons.Default.Notifications, Mac.Yellow,
                    "Notifikasi pemain & server",
                    "Beri tahu saat pemain join/leave atau server mati mendadak."
                ) {
                    Switch(checked = notifyEv, onCheckedChange = { notifyEv = it; Prefs.notifyEvents = it })
                }
            }
        }

        // Section: Tampilan.
        SectionHeader("Tampilan")
        StaggeredItem(3, Modifier.fillMaxWidth()) {
            FrostCard {
                SettingRow(
                    Icons.Default.Palette, Mac.Blue,
                    "Tema",
                    "Tampilan macOS, pilih gelap atau terang."
                ) {
                    MacSegmented(
                        options = listOf("Gelap", "Terang"),
                        selected = if (darkTheme) 0 else 1,
                        onSelect = { ThemeState.setDark(it == 0) },
                        modifier = Modifier.width(150.dp)
                    )
                }
            }
        }

        // Section: Update.
        SectionHeader("Update")
        StaggeredItem(4, Modifier.fillMaxWidth()) {
            FrostCard {
                Text("Update aplikasi", style = MaterialTheme.typography.titleMedium, color = Mac.Text)
                Text("Versi terpasang: ${Updater.currentVersion(ctx)}", style = MaterialTheme.typography.bodySmall, color = Mac.Secondary)
                Text(
                    when (upd) {
                        is UpdateCheck.Idle -> "Belum pernah memeriksa update."
                        is UpdateCheck.Checking -> "Memeriksa update…"
                        is UpdateCheck.UpToDate -> "Sudah versi terbaru."
                        is UpdateCheck.Available -> "Update tersedia: v${(upd as UpdateCheck.Available).version}."
                        is UpdateCheck.Downloading -> "Mengunduh update… ${(upd as UpdateCheck.Downloading).progress}%"
                        is UpdateCheck.ReadyToInstall -> "Unduhan selesai. Lanjutkan install."
                        is UpdateCheck.Error -> "Gagal memeriksa: ${(upd as UpdateCheck.Error).message}"
                    },
                    style = MaterialTheme.typography.bodySmall, color = Mac.Text
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { Updater.check(ctx) },
                        modifier = Modifier.pressScale(pressCheckUpd),
                        interactionSource = pressCheckUpd,
                        shape = RoundedCornerShape(20.dp)
                    ) { Text("Periksa update") }
                    if (upd is UpdateCheck.Available) {
                        Button(onClick = { Updater.downloadAndInstall(ctx) }, shape = RoundedCornerShape(20.dp)) { Text("Unduh & Install") }
                    }
                    if (upd is UpdateCheck.ReadyToInstall) {
                        Button(onClick = { Updater.installApk(ctx, (upd as UpdateCheck.ReadyToInstall).file) }, shape = RoundedCornerShape(20.dp)) { Text("Install") }
                    }
                }
                if (upd is UpdateCheck.Checking) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        Text("Memeriksa update…", style = MaterialTheme.typography.bodySmall, color = Mac.Text)
                    }
                }
            }
        }

        // Section: Backup.
        SectionHeader("Backup")
        StaggeredItem(5, Modifier.fillMaxWidth()) {
            FrostCard {
                Text("Backup", style = MaterialTheme.typography.titleMedium, color = Mac.Text)
                Text("Simpan salinan folder server dan database agar bisa dipulihkan kapan saja.", style = MaterialTheme.typography.bodySmall, color = Mac.Secondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                runCatching { Controller.createBackup() }
                                    .onSuccess { toast(ctx, "Backup berhasil: ${it.name} (${humanSize(it.length())})") }
                                    .onFailure { toast(ctx, "Backup gagal: ${it.message}") }
                                refreshTick++
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.pressScale(pressBackup),
                        interactionSource = pressBackup,
                        shape = RoundedCornerShape(20.dp)
                    ) { Text("Buat backup sekarang") }
                    OutlinedButton(
                        onClick = { restorePicker.launch(arrayOf("application/zip")) },
                        enabled = game == RunState.STOPPED && !busy,
                        shape = RoundedCornerShape(20.dp)
                    ) { Text("Pilih file backup…") }
                }
                if (backups.isEmpty()) Text("Belum ada backup.", color = Mac.Secondary)
                backups.forEach { file ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(file.name, style = MaterialTheme.typography.bodyMedium, color = Mac.Text)
                            Text(humanSize(file.length()), style = MaterialTheme.typography.bodySmall, color = Mac.Secondary)
                        }
                        OutlinedButton(
                            onClick = { restoreTarget = file },
                            enabled = game == RunState.STOPPED && !busy,
                            shape = RoundedCornerShape(20.dp)
                        ) { Text("Restore") }
                        IconButton(onClick = {
                            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/zip"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            ctx.startActivity(Intent.createChooser(intent, "Bagikan backup"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Bagikan backup", tint = Mac.Blue)
                        }
                        IconButton(onClick = { file.delete(); refreshTick++ }) {
                            Icon(Icons.Default.Delete, contentDescription = "Hapus backup", tint = Mac.Red)
                        }
                    }
                }
                Text("Lokasi: ${Env.backupDir.absolutePath}", style = MaterialTheme.typography.bodySmall, color = Mac.Secondary)
            }
        }

        // Section: URL unduhan.
        SectionHeader("URL unduhan")
        StaggeredItem(6, Modifier.fillMaxWidth()) {
            FrostCard {
                Text("URL unduhan", style = MaterialTheme.typography.titleMedium, color = Mac.Text)
                Text("Masukkan direct URL arsip untuk mesin open.mp dan SA-MP saja.", style = MaterialTheme.typography.bodySmall, color = Mac.Secondary)
                OutlinedTextField(
                    value = openMpUrl,
                    onValueChange = { openMpUrl = it; Prefs.urlOpenMp = it.trim() },
                    label = { Text("open.mp + CEF 7058 Linux x86 (.tar.gz)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sampUrl,
                    onValueChange = { sampUrl = it; Prefs.urlSamp = it.trim() },
                    label = { Text("SA-MP 0.3.7 R2 Linux (.tar.gz)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sampDlUrl,
                    onValueChange = { sampDlUrl = it; Prefs.urlSampDl = it.trim() },
                    label = { Text("SA-MP 0.3DL R1 Linux (.tar.gz)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = {
                    openMpUrl = Prefs.DEF_OPENMP
                    sampUrl = Prefs.DEF_SAMP
                    sampDlUrl = Prefs.DEF_SAMP_DL
                    Prefs.urlOpenMp = openMpUrl
                    Prefs.urlSamp = sampUrl
                    Prefs.urlSampDl = sampDlUrl
                }, shape = RoundedCornerShape(20.dp)) { Text("Reset URL ke default") }
            }
        }

        // Section: Aktivitas unduhan.
        SectionHeader("Aktivitas unduhan")
        StaggeredItem(7, Modifier.fillMaxWidth()) {
            FrostCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Aktivitas unduhan", style = MaterialTheme.typography.titleMedium, color = Mac.Text, modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = { Controller.setupLog.clear() },
                        enabled = !busy,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Bersihkan")
                    }
                }
                if (log.isEmpty()) {
                    Text("Belum ada aktivitas. Pilih mesin server lalu tekan Unduh.", style = MaterialTheme.typography.bodySmall, color = Mac.Secondary)
                } else {
                    LogView(log, Modifier.fillMaxWidth().height(230.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    // Konfirmasi sebelum restore: folder server akan ditimpa isi backup.
    restoreTarget?.let { file ->
        AnimatedAlertDialog(
            onDismissRequest = { restoreTarget = null },
            title = { Text("Pulihkan ${file.name}?") },
            text = { Text("Folder server akan ditimpa dengan isi backup ini. Pastikan server sudah berhenti.") },
            confirmButton = {
                TextButton(onClick = {
                    restoreTarget = null
                    scope.launch(Dispatchers.IO) {
                        runCatching { Controller.restoreBackup(file) }
                            .onSuccess { toast(ctx, it) }
                            .onFailure { toast(ctx, "Restore gagal: ${it.message}") }
                        refreshTick++
                    }
                }) { Text("Pulihkan") }
            },
            dismissButton = {
                TextButton(onClick = { restoreTarget = null }) { Text("Batal") }
            }
        )
    }
}
