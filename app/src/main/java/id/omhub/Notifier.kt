package id.omphub

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Notifikasi event (bukan notifikasi foreground service): pemain join/leave,
 * server mati mendadak, dan restart otomatis oleh watchdog.
 * Event pemain digabung dalam satu notifikasi (debounce 4 detik) supaya tidak spam.
 */
object Notifier {
    private const val CHANNEL_EVENTS = "omphub_events"
    private const val NOTIF_EVENTS_ID = 10

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pendingLines = mutableListOf<String>()
    private var flushJob: Job? = null

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_EVENTS) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_EVENTS,
                    "Aktivitas server",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Pemain join/leave, server mati, dan restart otomatis" }
            )
        }
    }

    /** Satu baris event pemain ("Evan bergabung" / "Dean keluar"). Dikirim gabungan. */
    fun playerEvent(ctx: Context, line: String) {
        synchronized(pendingLines) { pendingLines.add(line) }
        flushJob?.cancel()
        flushJob = scope.launch {
            delay(4000)
            val lines = synchronized(pendingLines) { pendingLines.toList().also { pendingLines.clear() } }
            if (lines.isEmpty()) return@launch
            post(ctx, "Pemain server", lines)
        }
    }

    fun serverDied(ctx: Context, text: String) {
        post(ctx, "Server berhenti", listOf(text))
    }

    fun watchdogRestart(ctx: Context, what: String) {
        post(ctx, "Server di-restart otomatis", listOf("$what mati mendadak — dinyalakan lagi."))
    }

    fun watchdogGaveUp(ctx: Context, what: String) {
        post(ctx, "Watchdog berhenti", listOf("$what mati 5x dalam 10 menit — nyalakan manual dari aplikasi."))
    }

    private fun post(ctx: Context, title: String, lines: List<String>) {
        if (!Prefs.notifyEvents) return
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        lines.take(5).forEach { style.addLine(it) }
        if (lines.size > 5) style.setSummaryText("+${lines.size - 5} lainnya")
        val notif = NotificationCompat.Builder(ctx, CHANNEL_EVENTS)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(if (lines.size == 1) lines[0] else "${lines.size} aktivitas")
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(NOTIF_EVENTS_ID, notif) }
    }
}
