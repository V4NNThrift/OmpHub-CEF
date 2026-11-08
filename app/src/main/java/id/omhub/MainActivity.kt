package id.omphub

import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(28, 28, 30)
        window.navigationBarColor = android.graphics.Color.rgb(28, 28, 30)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        setContent { OmpRoot() }
    }
}

private val TABS: List<Pair<String, ImageVector>> = listOf(
    "Server" to Icons.Default.Dashboard,
    "Console" to Icons.Default.Terminal,
    "Files" to Icons.Default.Folder,
    "Database" to Icons.Default.Storage,
    "Setup" to Icons.Default.Settings
)

@Composable
fun OmpRoot() {
    val ctx = LocalContext.current
    // Dikoleksi supaya seluruh tree recompose saat tema berubah (Mac.* membaca Prefs.themeDark).
    @Suppress("UNUSED_VARIABLE")
    val dark by ThemeState.dark.collectAsState()
    val updateState by Updater.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { Controller.autoSetupOnFirstLaunch() }
        Updater.check(ctx, auto = true)
    }

    // Warna status bar / nav bar mengikuti tema aktif.
    val window = (LocalContext.current as? Activity)?.window
    SideEffect {
        window?.let {
            it.statusBarColor = Mac.Background.toArgb()
            it.navigationBarColor = Mac.Background.toArgb()
        }
    }

    MaterialTheme(colorScheme = ompColors(), shapes = OmpShapes) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val gate by Controller.bootGate.collectAsState()
            if (gate != BootGate.DONE) {
                BootScreen()
                return@Surface
            }
            var tab by rememberSaveable { mutableIntStateOf(0) }
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    NavigationBar(containerColor = Mac.Panel, contentColor = Mac.Secondary) {
                        TABS.forEachIndexed { index, (label, icon) ->
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { tab = index },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Mac.Blue,
                                    selectedTextColor = Mac.Blue,
                                    indicatorColor = Mac.Blue.copy(alpha = 0.18f),
                                    unselectedIconColor = Mac.Secondary,
                                    unselectedTextColor = Mac.Secondary
                                )
                            )
                        }
                    }
                }
            ) { padding ->
                val setupBusy by Controller.setupBusy.collectAsState()
                Column(Modifier.padding(padding).fillMaxSize()) {
                    if (setupBusy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            "Menyiapkan komponen server…",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        when (tab) {
                            0 -> DashboardScreen()
                            1 -> ConsoleScreen()
                            2 -> FilesScreen()
                            3 -> DatabaseScreen()
                            else -> SetupScreen()
                        }
                    }
                }
            }
        }

        // Dialog update (hanya muncul kalau ada versi baru; error cek otomatis diam-diam).
        val avail = updateState as? UpdateCheck.Available
        if (avail != null) {
            AlertDialog(
                onDismissRequest = { Updater.reset() },
                title = { Text("Update tersedia: v${avail.version}") },
                text = { Text("OmpHub-CEF v${avail.version} sudah rilis (${humanSize(avail.size)}). Unduh dan install sekarang?") },
                confirmButton = {
                    TextButton(onClick = { Updater.downloadAndInstall(ctx) }) { Text("Unduh & Install") }
                },
                dismissButton = {
                    TextButton(onClick = { Updater.reset() }) { Text("Nanti") }
                }
            )
        }
        val downloading = updateState as? UpdateCheck.Downloading
        if (downloading != null) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Mengunduh update…") },
                text = {
                    Column {
                        LinearProgressIndicator(
                            progress = { downloading.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("${downloading.progress}%", modifier = Modifier.padding(top = 8.dp))
                    }
                },
                confirmButton = {},
                dismissButton = {}
            )
        }
    }
}
