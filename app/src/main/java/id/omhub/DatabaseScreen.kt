package id.omphub

import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val PHPMYADMIN_URL = "http://127.0.0.1:8080/phpmyadmin/"

/** User-Agent Chrome desktop untuk Mode Desktop phpMyAdmin. */
private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

/** Terapkan Mode Desktop (UA desktop + wide viewport + pinch-to-zoom) atau kembalikan ke mobile. */
private fun applyPhpMyAdminMode(view: WebView, desktop: Boolean, defaultUa: String) {
    view.settings.apply {
        if (desktop) {
            userAgentString = DESKTOP_USER_AGENT
            useWideViewPort = true
            loadWithOverviewMode = true
        } else {
            userAgentString = defaultUa
            useWideViewPort = false
            loadWithOverviewMode = false
        }
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
    }
}

private const val PHPMYADMIN_STATE_FILE = "phpmyadmin_webview_state.bin"

private fun readPhpMyAdminState(context: android.content.Context): Bundle? {
    val file = File(context.filesDir, PHPMYADMIN_STATE_FILE)
    if (!file.exists()) return null
    return runCatching {
        val parcel = Parcel.obtain()
        try {
            parcel.unmarshall(file.readBytes(), 0, file.length().toInt())
            parcel.setDataPosition(0)
            parcel.readBundle(WebView::class.java.classLoader)
        } finally {
            parcel.recycle()
        }
    }.getOrNull()
}

private fun writePhpMyAdminState(context: android.content.Context, state: Bundle) {
    runCatching {
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(state)
            File(context.filesDir, PHPMYADMIN_STATE_FILE).writeBytes(parcel.marshall())
        } finally {
            parcel.recycle()
        }
    }
}

/**
 * Database sebagai Safari + Keychain: kartu kredensial berikon, lalu
 * phpMyAdmin di dalam jendela browser (chrome bar + address pill + Mode
 * Desktop). Semua fungsi lama tetap ada (power MariaDB, import/export SQL,
 * salin kredensial, simpan state WebView).
 */
@Composable
fun DatabaseScreen() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val dbState by Controller.dbState.collectAsStateWithLifecycle()
    val setup by Controller.setupStatus.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf("") }
    var credentials by remember { mutableStateOf<DbCredentials?>(null) }
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(dbState, setup.db) {
        credentials = withContext(Dispatchers.IO) { MariaDb.loadCredentials() }
        if (dbState == RunState.RUNNING && credentials == null) {
            credentials = withContext(Dispatchers.IO) { MariaDb.loadCredentials() }
        }
    }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                busy = true
                operation = "Mengimpor file SQL..."
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        check(MariaDb.isRunning()) { "MariaDB tidak sedang berjalan." }
                        val input = context.contentResolver.openInputStream(uri)
                            ?: error("File SQL tidak dapat dibaca.")
                        val temp = File(Env.tmpDir, "omphub-import-${System.currentTimeMillis()}.sql")
                        try {
                            input.use { source -> temp.outputStream().use { target -> source.copyTo(target) } }
                            MariaDb.importFrom(temp)
                        } finally {
                            temp.delete()
                        }
                    }
                }
                busy = false
                operation = ""
                result.fold(
                    onSuccess = { r ->
                        toast(context, if (r.ok) "Import SQL ke database samp berhasil." else "Import gagal: ${r.output.take(500)}")
                    },
                    onFailure = { e -> toast(context, "Import gagal: ${e.message ?: "kesalahan tidak diketahui"}") }
                )
            }
        }
    }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/sql")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                busy = true
                operation = "Mengekspor database SQL..."
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        check(MariaDb.isRunning()) { "MariaDB tidak sedang berjalan." }
                        val temp = File(Env.tmpDir, "omphub-export-${System.currentTimeMillis()}.sql")
                        try {
                            val dump = MariaDb.dumpTo(temp)
                            check(dump.ok && temp.exists() && temp.length() > 0L) {
                                "Export gagal: ${dump.output.take(500).ifBlank { "mariadb-dump tidak menghasilkan file." }}"
                            }
                            val output = context.contentResolver.openOutputStream(uri)
                                ?: error("Lokasi tujuan tidak dapat ditulis.")
                            output.use { target -> temp.inputStream().use { it.copyTo(target) } }
                        } finally {
                            temp.delete()
                        }
                    }
                }
                busy = false
                operation = ""
                result.fold(
                    onSuccess = { toast(context, "Export database samp berhasil.") },
                    onFailure = { e -> toast(context, "Export gagal: ${e.message ?: "kesalahan tidak diketahui"}") }
                )
            }
        }
    }

    val dbReady = setup.db && dbState == RunState.RUNNING
    var webView by remember { mutableStateOf<WebView?>(null) }
    val webHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
    // UA bawaan perangkat (untuk kembali ke tampilan mobile) + pilihan Mode Desktop yang tersimpan.
    val defaultUa = remember(context) { WebSettings.getDefaultUserAgent(context) }
    var desktopMode by remember { mutableStateOf(Prefs.phpMyAdminDesktopMode) }

    fun setDesktopMode(enabled: Boolean) {
        desktopMode = enabled
        Prefs.phpMyAdminDesktopMode = enabled
        webView?.let { view ->
            applyPhpMyAdminMode(view, enabled, defaultUa)
            view.reload()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header.
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 6 }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Database", style = MaterialTheme.typography.headlineMedium, color = Mac.Text)
                    Text(
                        if (dbReady) "phpMyAdmin berjalan di dalam OmpHub."
                        else if (!setup.db) "MariaDB/phpMyAdmin belum terpasang. Setup akan menyiapkannya."
                        else if (dbState == RunState.STARTING) "Menjalankan MariaDB dan phpMyAdmin..."
                        else "Menyiapkan koneksi database...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Mac.Secondary
                    )
                }
                OutlinedButton(
                    onClick = { webView?.reload() },
                    enabled = dbReady && !busy,
                    shape = RoundedCornerShape(20.dp)
                ) { Text("Refresh") }
            }
        }

        // Kartu koneksi gaya Keychain.
        StaggeredItem(1, Modifier.fillMaxWidth()) {
            credentials?.let { db ->
                fun copy(label: String, value: String) {
                    clipboardManager.setText(AnnotatedString(value))
                    android.widget.Toast.makeText(context, "$label disalin.", android.widget.Toast.LENGTH_SHORT).show()
                }
                FrostCard {
                    Text(
                        "KONEKSI DATABASE",
                        style = MaterialTheme.typography.labelMedium,
                        color = Mac.Blue
                    )
                    KeyRow(Icons.Default.Dns, Mac.Blue, "Hostname", "127.0.0.1") { copy("Hostname", "127.0.0.1") }
                    KeyRow(Icons.Default.Person, Mac.Green, "User", db.user) { copy("User", db.user) }
                    KeyRow(
                        Icons.Default.Lock, Mac.Yellow, "Password",
                        if (showPassword) db.password else "•".repeat(minOf(db.password.length, 12)),
                        extra = {
                            IconButton(onClick = { showPassword = !showPassword }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPassword) "Sembunyikan password" else "Tampilkan password",
                                    tint = Mac.Secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    ) { copy("Password", db.password) }
                    KeyRow(Icons.Default.Storage, Mac.Red, "Database", db.database) { copy("Database name", db.database) }
                    Text(
                        "Isi hostname dengan 127.0.0.1, jangan localhost, di gamemode atau plugin MySQL.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Mac.Secondary
                    )
                }
            }
        }

        // Tombol power + import/export.
        StaggeredItem(2, Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { scope.launch { Controller.startDb() } },
                        enabled = setup.db && dbState == RunState.STOPPED,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(if (dbState == RunState.STARTING) "Menyalakan..." else "Nyalakan MariaDB")
                    }
                    OutlinedButton(
                        onClick = { scope.launch { Controller.stopDb() } },
                        enabled = dbState == RunState.RUNNING,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Matikan MariaDB")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { importer.launch(arrayOf("application/sql", "application/x-sql", "text/plain", "*/*")) },
                        enabled = dbReady && !busy,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Import SQL")
                    }
                    OutlinedButton(
                        onClick = { exporter.launch("omphub-samp-backup.sql") },
                        enabled = dbReady && !busy,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Export SQL")
                    }
                }
                if (!setup.db) {
                    Text(
                        "MariaDB belum siap. Selesaikan persiapan awal aplikasi terlebih dahulu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Mac.Red
                    )
                }
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text(operation, style = MaterialTheme.typography.bodySmall, color = Mac.Text)
                    }
                }
            }
        }

        // Jendela browser Safari untuk phpMyAdmin.
        StaggeredItem(3, Modifier.fillMaxWidth()) {
            if (!dbReady) {
                Card(
                    modifier = Modifier.fillMaxWidth().height(webHeight),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Mac.Panel),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Mac.Border.copy(alpha = 0.5f))
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (!setup.db) "MariaDB/phpMyAdmin belum siap. Selesaikan persiapan awal aplikasi."
                            else if (dbState == RunState.STARTING) "MariaDB sedang dijalankan..."
                            else "Tekan Nyalakan MariaDB untuk membuka phpMyAdmin.",
                            modifier = Modifier.padding(20.dp),
                            color = Mac.Secondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Mac.Panel),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Mac.Border.copy(alpha = 0.5f))
                ) {
                    Column {
                        // Chrome bar Safari: traffic-light + address pill + reload.
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TrafficLights()
                            Box(
                                Modifier.weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Mac.PanelInner)
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "127.0.0.1:8080/phpmyadmin",
                                    color = Mac.Secondary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { webView?.reload() },
                                enabled = !busy,
                                modifier = Modifier.size(32.dp)
                            ) { Icon(Icons.Default.Refresh, "Muat ulang", tint = Mac.Blue, modifier = Modifier.size(18.dp)) }
                        }
                        // Baris toggle Mode Desktop.
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Mode Desktop", style = MaterialTheme.typography.bodyMedium, color = Mac.Text)
                                Text(
                                    if (desktopMode) "phpMyAdmin tampil seperti di PC (lebar penuh)."
                                    else "phpMyAdmin tampil versi mobile.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Mac.Secondary
                                )
                            }
                            Switch(checked = desktopMode, onCheckedChange = { setDesktopMode(it) })
                        }
                        HorizontalDivider(
                            color = Mac.Border.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 10.dp)
                        )
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(300))
                        ) {
                            Row(Modifier.fillMaxWidth()) {
                                AndroidView(
                                    modifier = Modifier.weight(1f).height(webHeight),
                                    factory = { ctx ->
                                        WebView(ctx).apply {
                                            settings.javaScriptEnabled = true
                                            settings.domStorageEnabled = true
                                            settings.databaseEnabled = true
                                            settings.loadsImagesAutomatically = true
                                            settings.javaScriptCanOpenWindowsAutomatically = true
                                            applyPhpMyAdminMode(this, desktopMode, defaultUa)
                                            webViewClient = WebViewClient()
                                            setOnTouchListener { v, _ -> v.parent?.requestDisallowInterceptTouchEvent(true); false }
                                            webChromeClient = WebChromeClient()
                                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                            webView = this
                                            val restored = readPhpMyAdminState(ctx)?.let { restoreState(it) }
                                            if (restored == null) loadUrl(PHPMYADMIN_URL)
                                        }
                                    },
                                    update = { view ->
                                        webView = view
                                        if (view.url == null) view.loadUrl(PHPMYADMIN_URL)
                                    },
                                    onRelease = { view ->
                                        runCatching {
                                            val state = Bundle()
                                            view.saveState(state)
                                            writePhpMyAdminState(context, state)
                                        }
                                        if (webView === view) webView = null
                                        view.stopLoading()
                                        view.destroy()
                                    }
                                )
                                Box(
                                    modifier = Modifier
                                        .width(30.dp)
                                        .height(webHeight)
                                        .background(Mac.PanelInner.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.UnfoldMore,
                                        contentDescription = "Geser di sini untuk scroll halaman",
                                        tint = Mac.Secondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Baris kredensial Keychain: icon tile + label + nilai monospace + copy. */
@Composable
private fun KeyRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    label: String,
    value: String,
    extra: @Composable (() -> Unit)? = null,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconTile(icon, tint)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Mac.Secondary,
            modifier = Modifier.width(84.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = Mac.Text,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        extra?.invoke()
        IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Salin $label", tint = Mac.Secondary, modifier = Modifier.size(18.dp))
        }
    }
}
