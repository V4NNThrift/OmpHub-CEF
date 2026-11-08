package id.omphub

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.IconButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.net.Uri
import android.os.Parcel
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val PHPMYADMIN_URL = "http://127.0.0.1:8080/phpmyadmin/"

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Database", style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (dbReady) "phpMyAdmin berjalan di dalam OmpHub."
                    else if (!setup.db) "MariaDB/phpMyAdmin belum terpasang. Setup akan menyiapkannya."
                    else if (dbState == RunState.STARTING) "Menjalankan MariaDB dan phpMyAdmin..."
                    else "Menyiapkan koneksi database...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = { webView?.reload() },
                enabled = dbReady && !busy
            ) { Text("Refresh") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.material3.Button(
                onClick = {
                    scope.launch { Controller.startDb() }
                },
                enabled = setup.db && dbState == RunState.STOPPED,
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(if (dbState == RunState.STARTING) "Menyalakan..." else "Nyalakan MariaDB")
            }
            OutlinedButton(
                onClick = {
                    scope.launch { Controller.stopDb() }
                },
                enabled = dbState == RunState.RUNNING,
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text("Matikan MariaDB")
            }
        }

        if (!setup.db) {
            Text(
                "MariaDB belum siap. Selesaikan persiapan awal aplikasi terlebih dahulu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        credentials?.let { db ->
            fun copy(label: String, value: String) {
                clipboardManager.setText(AnnotatedString(value))
                android.widget.Toast.makeText(context, "$label disalin.", android.widget.Toast.LENGTH_SHORT).show()
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Text(
                        "KONEKSI DATABASE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    DbInfoRow("Hostname", "127.0.0.1") { copy("Hostname", "127.0.0.1") }
                    DbInfoRow("User", db.user) { copy("User", db.user) }
                    DbInfoRow(
                        "Password",
                        if (showPassword) db.password else "•".repeat(minOf(db.password.length, 12)),
                        extra = {
                            IconButton(onClick = { showPassword = !showPassword }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPassword) "Sembunyikan password" else "Tampilkan password",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    ) { copy("Password", db.password) }
                    DbInfoRow("Database Name", db.database) { copy("Database name", db.database) }
                    Text(
                        "Isi hostname dengan 127.0.0.1, jangan localhost, di gamemode atau plugin MySQL.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
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
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null)
                Text(" Import SQL")
            }
            OutlinedButton(
                onClick = { exporter.launch("omphub-samp-backup.sql") },
                enabled = dbReady && !busy,
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null)
                Text(" Export SQL")
            }
        }

        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text(operation, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (!dbReady) {
            Card(
                modifier = Modifier.fillMaxWidth().height(webHeight),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (!setup.db) "MariaDB/phpMyAdmin belum siap. Selesaikan persiapan awal aplikasi."
                        else if (dbState == RunState.STARTING) "MariaDB sedang dijalankan..."
                        else "Tekan Nyalakan MariaDB untuk membuka phpMyAdmin.",
                        modifier = Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AndroidView(
                modifier = Modifier.weight(1f).height(webHeight),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.loadsImagesAutomatically = true
                        settings.javaScriptCanOpenWindowsAutomatically = true
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
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.UnfoldMore,
                    contentDescription = "Geser di sini untuk scroll halaman",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


@Composable
private fun DbInfoRow(
    label: String,
    value: String,
    extra: @Composable (() -> Unit)? = null,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        extra?.invoke()
        IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Salin $label", modifier = Modifier.size(18.dp))
        }
    }
}
