package id.omphub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Inter (variable font, OFL) — terasa seperti SF Pro macOS. */
val InterFontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold),
    Font(R.font.inter, FontWeight.ExtraBold)
)

/** Tipografi app-wide: semua style default Material 3 dengan font Inter. */
val OmpTypography: Typography
    get() {
        val base = Typography()
        fun TextStyle.inter() = copy(fontFamily = InterFontFamily)
        return Typography(
            displayLarge = base.displayLarge.inter(),
            displayMedium = base.displayMedium.inter(),
            displaySmall = base.displaySmall.inter(),
            headlineLarge = base.headlineLarge.inter(),
            headlineMedium = base.headlineMedium.inter(),
            headlineSmall = base.headlineSmall.inter(),
            titleLarge = base.titleLarge.inter(),
            titleMedium = base.titleMedium.inter(),
            titleSmall = base.titleSmall.inter(),
            bodyLarge = base.bodyLarge.inter(),
            bodyMedium = base.bodyMedium.inter(),
            bodySmall = base.bodySmall.inter(),
            labelLarge = base.labelLarge.inter(),
            labelMedium = base.labelMedium.inter(),
            labelSmall = base.labelSmall.inter()
        )
    }

/**
 * Palet macOS (ala Big Sur / Sequoia), gelap + terang.
 * Satu-satunya tempat warna UI didefinisikan — semua screen memakai objek ini
 * (atau MaterialTheme.colorScheme yang dibangun dari sini) supaya konsisten.
 * Nilai warna mengikuti [Prefs.themeDark]; ganti tema lewat [ThemeState].
 */
object Mac {
    val Background get() = if (Prefs.themeDark) Color(0xFF1C1C1E) else Color(0xFFF5F5F7)
    val Panel get() = if (Prefs.themeDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
    val PanelInner get() = if (Prefs.themeDark) Color(0xFF3A3A3C) else Color(0xFFE8E8ED)
    val Border get() = if (Prefs.themeDark) Color(0xFF3A3A3C) else Color(0xFFD1D1D6)
    val Track get() = if (Prefs.themeDark) Color(0xFF48484A) else Color(0xFFD1D1D6)
    val Text get() = if (Prefs.themeDark) Color(0xFFF5F5F7) else Color(0xFF1C1C1E)
    val Secondary get() = if (Prefs.themeDark) Color(0xFF98989D) else Color(0xFF6E6E73)
    val Blue get() = if (Prefs.themeDark) Color(0xFF0A84FF) else Color(0xFF007AFF)
    val Green get() = if (Prefs.themeDark) Color(0xFF30D158) else Color(0xFF34C759)
    val Yellow get() = if (Prefs.themeDark) Color(0xFFFF9F0A) else Color(0xFFFF9500)
    val Red get() = if (Prefs.themeDark) Color(0xFFFF453A) else Color(0xFFFF3B30)

    // Warna traffic-light macOS (tombol jendela) — sama di kedua tema.
    val TrafficRed = Color(0xFFFF5F57)
    val TrafficYellow = Color(0xFFFEBC2E)
    val TrafficGreen = Color(0xFF28C840)
}

/** Status tema aktif (gelap/terang) yang diobservasi UI; tersimpan di [Prefs.themeDark]. */
object ThemeState {
    private val _dark = MutableStateFlow(Prefs.themeDark)
    val dark: StateFlow<Boolean> = _dark
    fun setDark(v: Boolean) {
        Prefs.themeDark = v
        _dark.value = v
    }
}

/** Skema warna Material 3 yang dipakai seluruh aplikasi (ikut tema aktif). */
fun ompColors(): ColorScheme = if (Prefs.themeDark) {
    darkColorScheme(
        primary = Mac.Blue,
        onPrimary = Color.White,
        secondary = Mac.Green,
        tertiary = Mac.Yellow,
        background = Mac.Background,
        surface = Mac.Panel,
        surfaceVariant = Mac.PanelInner,
        onSurface = Mac.Text,
        onSurfaceVariant = Mac.Secondary,
        outline = Mac.Border,
        error = Mac.Red,
        onError = Color.White
    )
} else {
    lightColorScheme(
        primary = Mac.Blue,
        onPrimary = Color.White,
        secondary = Mac.Green,
        tertiary = Mac.Yellow,
        background = Mac.Background,
        surface = Mac.Panel,
        surfaceVariant = Mac.PanelInner,
        onSurface = Mac.Text,
        onSurfaceVariant = Mac.Secondary,
        outline = Mac.Border,
        error = Mac.Red,
        onError = Color.White
    )
}

/** Bentuk macOS: kartu 12dp, tombol pill 20dp. */
val OmpShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

/** Tiga dot traffic-light macOS (merah / kuning / hijau), motif jendela. */
@Composable
fun TrafficLights(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(Mac.TrafficRed))
        Box(Modifier.size(12.dp).clip(CircleShape).background(Mac.TrafficYellow))
        Box(Modifier.size(12.dp).clip(CircleShape).background(Mac.TrafficGreen))
    }
}
