package id.omphub

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    "Server" to Icons.Default.Dns,
    "Console" to Icons.Default.Terminal,
    "Files" to Icons.Default.Folder,
    "Database" to Icons.Default.Storage,
    "Setup" to Icons.Default.Settings
)

/** Menu bar ala macOS: traffic-light kiri, nama di tengah, pill status kanan. */
@Composable
private fun MenuBar(game: RunState) {
    val statusText = when (game) {
        RunState.RUNNING -> "Running"
        RunState.STARTING -> "Starting…"
        RunState.STOPPING -> "Stopping…"
        RunState.STOPPED -> "Stopped"
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrafficLights()
        Text(
            "OmpHub",
            Modifier.weight(1f),
            textAlign = TextAlign.Center,
            color = Mac.Text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            Modifier.clip(RoundedCornerShape(20.dp)).background(Mac.Panel.copy(alpha = 0.85f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (game == RunState.RUNNING) PulsingDot(Mac.Green)
            else Box(Modifier.size(7.dp).clip(CircleShape).background(Mac.Secondary))
            Text(statusText, color = Mac.Text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Dock mengambang ala macOS: kapsul frosted-glass di bawah layar, 5 ikon.
 * Ikon aktif membesar (spring) + dot biru di bawahnya.
 */
@Composable
private fun Dock(tab: Int, onTab: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(26.dp))
            .background(Mac.Panel.copy(alpha = 0.88f))
            .border(BorderStroke(1.dp, Mac.Border.copy(alpha = 0.6f)), RoundedCornerShape(26.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TABS.forEachIndexed { index, (label, icon) ->
            val selected = tab == index
            val iconSize by animateDpAsState(
                if (selected) 30.dp else 24.dp,
                spring(dampingRatio = 0.7f, stiffness = 500f),
                label = "dockIcon"
            )
            val press = rememberPressSource()
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = { onTab(index) },
                    modifier = Modifier.size(46.dp).pressScale(press),
                    interactionSource = press
                ) {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = if (selected) Mac.Blue else Mac.Secondary,
                        modifier = Modifier.size(iconSize)
                    )
                }
                Box(
                    Modifier.size(4.dp).clip(CircleShape)
                        .background(if (selected) Mac.Blue else Color.Transparent)
                )
            }
        }
    }
}

@Composable
fun OmpRoot() {
    val ctx = LocalContext.current
    // Dikoleksi supaya seluruh tree recompose saat tema berubah (Mac.* membaca Prefs.themeDark).
    val dark by ThemeState.dark.collectAsState()
    val updateState by Updater.state.collectAsStateWithLifecycle()
    val game by Controller.gameState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { Controller.autoSetupOnFirstLaunch() }
        Updater.check(ctx, auto = true)
    }

    // Notifikasi event (pemain join/leave, server mati) butuh izin runtime di Android 13+.
    // Tanpa izin, notifikasi event diam-diam tidak tampil — minta sekali saat aplikasi dibuka.
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Warna status bar / nav bar mengikuti tema aktif.
    val window = (LocalContext.current as? Activity)?.window
    SideEffect {
        window?.let {
            it.statusBarColor = Mac.Background.toArgb()
            it.navigationBarColor = Mac.Background.toArgb()
        }
    }

    // Latar belakang ikut beranimasi saat tema berganti (bukan pindah mendadak).
    val bg by animateColorAsState(Mac.Background, tween(300), label = "bg")
    val glow by animateColorAsState(Mac.GlowTop, tween(300), label = "glow")

    MaterialTheme(colorScheme = ompColors(), shapes = OmpShapes, typography = OmpTypography) {
        Surface(Modifier.fillMaxSize(), color = bg) {
            val gate by Controller.bootGate.collectAsState()
            if (gate != BootGate.DONE) {
                BootScreen()
                return@Surface
            }
            var tab by rememberSaveable { mutableIntStateOf(0) }
            // Ganti tema = crossfade halus seluruh UI (bukan snap).
            Crossfade(targetState = dark, animationSpec = tween(350), label = "theme") {
                Box(
                    Modifier.fillMaxSize()
                        .background(Brush.verticalGradient(listOf(glow, bg)))
                ) {
                    Column(Modifier.fillMaxSize()) {
                        MenuBar(game)
                        val setupBusy by Controller.setupBusy.collectAsState()
                        if (setupBusy) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(
                                "Menyiapkan komponen server…",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Konten diberi padding bawah supaya tidak tertutup Dock.
                        Box(Modifier.weight(1f).padding(bottom = 88.dp)) {
                            // Pindah tab = fade + scale halus (ala macOS).
                            AnimatedContent(
                                targetState = tab,
                                transitionSpec = {
                                    (fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                                        scaleIn(tween(220, easing = FastOutSlowInEasing), initialScale = 0.96f)) togetherWith
                                        (fadeOut(tween(160, easing = FastOutSlowInEasing)) +
                                            scaleOut(tween(160, easing = FastOutSlowInEasing), targetScale = 0.97f))
                                },
                                label = "tabSwitch"
                            ) { t ->
                                when (t) {
                                    0 -> DashboardScreen()
                                    1 -> ConsoleScreen()
                                    2 -> FilesScreen()
                                    3 -> DatabaseScreen()
                                    else -> SetupScreen()
                                }
                            }
                        }
                    }
                    Dock(
                        tab = tab,
                        onTab = { tab = it },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
                    )
                }
            }
        }

        // Dialog update (hanya muncul kalau ada versi baru; error cek otomatis diam-diam).
        val avail = updateState as? UpdateCheck.Available
        if (avail != null) {
            AnimatedAlertDialog(
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
            AnimatedAlertDialog(
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
                }
            )
        }
    }
}
