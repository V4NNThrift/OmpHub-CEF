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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import java.io.File

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
    LaunchedEffect(busy, refreshTick) { Controller.refreshStatus(); backups = Controller.listBackups() }

    val isSamp = st.engine == ServerType.SAMP
    val serverName = if (isSamp) "SA-MP ${sampVer.label}" else "open.mp + CEF 7058"
    val installedVersion = remember(st, sampVer, busy) {
        if (isSamp) SampInstaller.installedVersion() else null
    }
    val installed = st.serverInstalled
    val readyToInstall = st.binaries && st.gameRootfs && !busy && game == RunState.STOPPED

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Setup", style = MaterialTheme.typography.headlineMedium)
            Text("Atur mesin server dan URL unduhan open.mp + CEF 7058 atau SA-MP.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("SETUP MESIN SERVER", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ServerType.entries.forEach { type ->
                        val selected = st.engine == type
                        if (selected) {
                            Button(onClick = { Prefs.serverType = type; Controller.refreshStatus() }, enabled = game == RunState.STOPPED, modifier = Modifier.weight(1f)) {
                                Text(type.label)
                            }
                        } else {
                            OutlinedButton(onClick = { Prefs.serverType = type; Controller.refreshStatus() }, enabled = game == RunState.STOPPED, modifier = Modifier.weight(1f)) {
                                Text(type.label)
                            }
                        }
                    }
                }
                if (isSamp) {
                    Text("Versi SA-MP", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SampVersion.entries.forEach { version ->
                            if (sampVer == version) {
                                Button(onClick = { sampVer = version; Prefs.sampVersion = version }, enabled = game == RunState.STOPPED) { Text(version.label) }
                            } else {
                                OutlinedButton(onClick = { sampVer = version; Prefs.sampVersion = version }, enabled = game == RunState.STOPPED) { Text(version.label) }
                            }
                        }
                    }
                }
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("STATUS INSTALASI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            when {
                                !st.binaries -> "Komponen runtime belum tersedia di APK"
                                !st.gameRootfs -> "Sistem dasar server belum disiapkan"
                                isSamp && installedVersion == null -> "SA-MP belum diunduh"
                                isSamp && installedVersion != sampVer -> "Terpasang SA-MP ${installedVersion?.label}; versi pilihan berbeda"
                                installed -> "$serverName siap digunakan"
                                else -> "$serverName belum diunduh"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Button(
                    onClick = { Controller.installServer() },
                    enabled = readyToInstall,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.material3.Icon(Icons.Default.CloudDownload, contentDescription = null)
                    Spacer(Modifier.padding(start = 4.dp))
                    Text(if (installed) "Update $serverName" else "Unduh $serverName")
                }
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(22.dp))
                        Text("Sedang menyiapkan server…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (game != RunState.STOPPED) {
                    Text("Hentikan server sebelum mengganti atau memperbarui mesin server.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (!st.binaries || !st.gameRootfs) {
                    Text("Runtime dan sistem dasar disiapkan otomatis saat pertama kali aplikasi dijalankan. Pastikan proses persiapan awal sudah selesai.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tema", style = MaterialTheme.typography.titleMedium)
                Text("Tampilan macOS, pilih gelap atau terang.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    if (darkTheme) {
                        Button(onClick = { ThemeState.setDark(true) }, modifier = Modifier.weight(1f)) { Text("Gelap") }
                    } else {
                        OutlinedButton(onClick = { ThemeState.setDark(true) }, modifier = Modifier.weight(1f)) { Text("Gelap") }
                    }
                    if (!darkTheme) {
                        Button(onClick = { ThemeState.setDark(false) }, modifier = Modifier.weight(1f)) { Text("Terang") }
                    } else {
                        OutlinedButton(onClick = { ThemeState.setDark(false) }, modifier = Modifier.weight(1f)) { Text("Terang") }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Update aplikasi", style = MaterialTheme.typography.titleMedium)
                Text("Versi terpasang: ${Updater.currentVersion(ctx)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { Updater.check(ctx) }) { Text("Periksa update") }
                    if (upd is UpdateCheck.Available) {
                        Button(onClick = { Updater.downloadAndInstall(ctx) }) { Text("Unduh & Install") }
                    }
                    if (upd is UpdateCheck.ReadyToInstall) {
                        Button(onClick = { Updater.installApk(ctx, (upd as UpdateCheck.ReadyToInstall).file) }) { Text("Install") }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Backup", style = MaterialTheme.typography.titleMedium)
                Text("Simpan salinan folder server dan database agar bisa dipulihkan kapan saja.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        enabled = !busy
                    ) { Text("Buat backup sekarang") }
                    OutlinedButton(
                        onClick = { restorePicker.launch(arrayOf("application/zip")) },
                        enabled = game == RunState.STOPPED && !busy
                    ) { Text("Pilih file backup…") }
                }
                if (backups.isEmpty()) Text("Belum ada backup.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                backups.forEach { file ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(file.name, style = MaterialTheme.typography.bodyMedium)
                            Text(humanSize(file.length()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(
                            onClick = { restoreTarget = file },
                            enabled = game == RunState.STOPPED && !busy
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
                            Icon(Icons.Default.Share, contentDescription = "Bagikan backup")
                        }
                        IconButton(onClick = { file.delete(); refreshTick++ }) {
                            Icon(Icons.Default.Delete, contentDescription = "Hapus backup")
                        }
                    }
                }
                Text("Lokasi: ${Env.backupDir.absolutePath}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("URL unduhan", style = MaterialTheme.typography.titleMedium)
                Text("Masukkan direct URL arsip untuk mesin open.mp dan SA-MP saja.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                }) { Text("Reset URL ke default") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Aktivitas unduhan", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { Controller.setupLog.clear() }, enabled = !busy) {
                        androidx.compose.material3.Icon(Icons.Default.Refresh, contentDescription = null)
                        Text(" Bersihkan")
                    }
                }
                if (log.isEmpty()) {
                    Text("Belum ada aktivitas. Pilih mesin server lalu tekan Unduh.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LogView(log, Modifier.fillMaxWidth().height(230.dp))
                }
            }
        }
    }

    // Konfirmasi sebelum restore: folder server akan ditimpa isi backup.
    restoreTarget?.let { file ->
        AlertDialog(
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
