package id.omphub

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

class ServerService : Service() {
    companion object {
        const val ACTION_START = "id.omphub.START"
        const val ACTION_STOP = "id.omphub.STOP"
        const val ACTION_RESTART = "id.omphub.RESTART"
        const val ACTION_START_DB = "id.omphub.START_DB"
        const val ACTION_STOP_DB = "id.omphub.STOP_DB"
        private const val CHANNEL = "omphub"
        private const val NOTIF_ID = 1

        fun send(ctx: Context, action: String) {
            val i = Intent(ctx, ServerService::class.java).setAction(action)
            ContextCompat.startForegroundService(ctx, i)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val busy = AtomicInteger(0)
    private var wake: PowerManager.WakeLock? = null
    private var watching = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(NOTIF_ID, notification("OmpHub aktif"))
        if (wake == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "omphub:server").apply { acquire() }
        }
        watch()
        PerfMonitor.ensureStarted()

        val action = intent?.action
        busy.incrementAndGet()
        Controller.scope.launch {
            try {
                when (action) {
                    ACTION_START -> Controller.startAll()
                    ACTION_STOP -> Controller.stopAll()
                    ACTION_RESTART -> { Controller.stopGame(); Controller.startGame() }
                    ACTION_START_DB -> Controller.startDb()
                    ACTION_STOP_DB -> Controller.stopDb()
                }
            } catch (e: Throwable) {
                Controller.gameLog.add("[panel] Error: ${e.message}")
            } finally {
                busy.decrementAndGet()
                maybeStop()
            }
        }
        return START_NOT_STICKY
    }

    private fun watch() {
        if (watching) return
        watching = true
        scope.launch {
            Controller.gameState.combine(Controller.dbState) { g, d -> g to d }.collect { (g, d) ->
                val text = "${Prefs.serverType.label}: ${g.name.lowercase()}  |  MariaDB: ${d.name.lowercase()}"
                getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(text))
                maybeStop()
            }
        }
    }

    private fun maybeStop() {
        if (busy.get() == 0 &&
            Controller.gameState.value == RunState.STOPPED &&
            Controller.dbState.value == RunState.STOPPED
        ) {
            stopForeground(true)
            stopSelf()
        }
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Server", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("OmpHub")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(0, "Stop", stop)
            .build()
    }

    override fun onDestroy() {
        wake?.let { if (it.isHeld) it.release() }
        scope.cancel()
        super.onDestroy()
    }
}
