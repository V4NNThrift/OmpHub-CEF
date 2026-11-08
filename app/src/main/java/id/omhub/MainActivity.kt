package id.omphub

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
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import android.os.Build
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { Controller.autoSetupOnFirstLaunch() } }
    MaterialTheme(colorScheme = OmpColors, shapes = OmpShapes) {
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
    }
}
