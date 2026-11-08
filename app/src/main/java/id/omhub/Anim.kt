package id.omphub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ProvideTextStyle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Animasi gaya macOS: spring halus dan natural (damping 0.8, stiffness 300).
 * Dipakai di semua screen supaya pergerakan terasa konsisten dan ringan
 * (tanpa blur/shadow besar).
 */

/** Spring standar macOS untuk animasi nilai float. */
fun macSpringFloat() = spring<Float>(dampingRatio = 0.8f, stiffness = 300f)

/** Sumber interaksi untuk efek tekan; pasangkan dengan [pressScale] di tombol. */
@Composable
fun rememberPressSource(): MutableInteractionSource = remember { MutableInteractionSource() }

/**
 * Efek tekan ala macOS: mengecil sedikit (0.94x) saat ditekan, kembali
 * dengan spring saat dilepas. Pasang bersama `interactionSource` yang sama
 * dengan tombolnya, mis. `Button(..., interactionSource = src, modifier =
 * Modifier.pressScale(src))`.
 */
@Composable
fun Modifier.pressScale(source: InteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "pressScale"
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Dot status berdenyut halus (cincin membesar + memudar, tanpa blur). */
@Composable
fun PulsingDot(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val ringScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 2.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val ringAlpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )
    Box(modifier.size(10.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(10.dp)
                .graphicsLayer { scaleX = ringScale; scaleY = ringScale; alpha = ringAlpha }
                .clip(CircleShape)
                .background(color)
        )
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
    }
}

/**
 * Item yang masuk berurutan (stagger): fade + naik sedikit, delay
 * bertambah per [index]. Dipakai untuk kartu-kartu di semua screen supaya
 * layar terasa "hidup" saat pertama dibuka.
 */
@Composable
fun StaggeredItem(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val delay = (index * 70).coerceAtMost(560)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300, delayMillis = delay, easing = FastOutSlowInEasing)) +
            slideInVertically(tween(300, delayMillis = delay, easing = FastOutSlowInEasing)) { it / 6 },
        exit = fadeOut(tween(150)),
        modifier = modifier
    ) { content() }
}

/** Dialog dengan animasi masuk (scale + fade) ala macOS. */
@Composable
fun AnimatedDialog(onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    Dialog(onDismissRequest = onDismissRequest) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(200, easing = FastOutSlowInEasing)) +
                scaleIn(
                    spring(dampingRatio = 0.85f, stiffness = 400f),
                    initialScale = 0.92f
                ),
            exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.95f)
        ) { content() }
    }
}

/**
 * Pengganti AlertDialog dengan animasi masuk. Susunan sama (judul, isi,
 * tombol aksi di kanan bawah) supaya call site lama tinggal ganti nama.
 */
@Composable
fun AnimatedAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null
) {
    AnimatedDialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() }
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                    Column(Modifier.fillMaxWidth()) { text() }
                }
                if (confirmButton != null || dismissButton != null) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        dismissButton?.invoke()
                        confirmButton?.invoke()
                    }
                }
            }
        }
    }
}
