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
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import android.os.Build
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(11, 13, 15)
        window.navigationBarColor = android.graphics.Color.rgb(15, 17, 20)
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

private val OmpColors = darkColorScheme(
    primary = Color(0xFF20C7D9),
    onPrimary = Color(0xFF061316),
    secondary = Color(0xFF9B70F5),
    background = Color(0xFF0B0D0F),
    surface = Color(0xFF15181C),
    surfaceVariant = Color(0xFF1D2228),
    onSurface = Color(0xFFE8EDF2),
    onSurfaceVariant = Color(0xFF929BA6),
    outline = Color(0xFF343B44)
)

@Composable
fun OmpRoot() {
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { Controller.autoSetupOnFirstLaunch() } }
    MaterialTheme(colorScheme = OmpColors) {
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
                    NavigationBar(containerColor = Color(0xFF0F1114), contentColor = Color(0xFF929BA6)) {
                        TABS.forEachIndexed { index, (label, icon) ->
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { tab = index },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF20C7D9),
                                    selectedTextColor = Color(0xFF20C7D9),
                                    indicatorColor = Color(0xFF18343A),
                                    unselectedIconColor = Color(0xFF737D88),
                                    unselectedTextColor = Color(0xFF737D88)
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
