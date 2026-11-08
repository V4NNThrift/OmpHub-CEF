package id.omphub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Start server otomatis setelah HP selesai boot, kalau toggle di Setup ON.
 * Di Android 12+ start foreground service dari background bisa ditolak sistem
 * (ForegroundServiceStartNotAllowedException) — kegagalan ditelan diam-diam,
 * user tinggal buka aplikasi sekali.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs.bootAutoStart) return
        runCatching {
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, ServerService::class.java).setAction(ServerService.ACTION_START)
            )
        }
    }
}
