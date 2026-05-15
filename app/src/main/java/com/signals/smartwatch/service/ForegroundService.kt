package com.signals.smartwatch.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.signals.smartwatch.R
import com.signals.smartwatch.SensorEvents
import com.signals.smartwatch.data.CsvExporter
import com.signals.smartwatch.data.SensorDatabase
import com.signals.smartwatch.sensors.samsung.AccListener
import com.signals.smartwatch.sensors.samsung.HrListener
import com.signals.smartwatch.sensors.samsung.OnDemandScheduler
import com.signals.smartwatch.sensors.samsung.PpgListener
import com.signals.smartwatch.sensors.samsung.SkinTempListener
import com.signals.smartwatch.sensors.samsung.TrackerManager
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class ForegroundService : Service() {

    // ── Coroutine scope del servicio ──────────────────────────────────────────
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    // ── Sensores ──────────────────────────────────────────────────────────────
    private lateinit var trackerManager: TrackerManager
    private lateinit var scheduler: OnDemandScheduler

    // ── Backoff de reconexión ─────────────────────────────────────────────────
    @Volatile private var reconnectAttempt = 0

    // ── Room ──────────────────────────────────────────────────────────────────
    private val dao by lazy { SensorDatabase.getInstance(this).sensorDao() }

    // ── WakeLock ──────────────────────────────────────────────────────────────
    private var wakeLock: PowerManager.WakeLock? = null

    // ── Listener UI (vía LocalBinder) ─────────────────────────────────────────
    @Volatile private var uiListener: SensorEvents? = null

    private val listenerProxy = object : SensorEvents {
        override fun onAcc(x: Float, y: Float, z: Float) = uiListener?.onAcc(x, y, z) ?: Unit
        override fun onHr(bpm: Int)                       = uiListener?.onHr(bpm)       ?: Unit
        override fun onPpg(green: Float, ir: Float, red: Float) = uiListener?.onPpg(green, ir, red) ?: Unit
        override fun onSpO2(percent: Float)               = uiListener?.onSpO2(percent) ?: Unit
        override fun onSkinTemp(celsius: Float)           = uiListener?.onSkinTemp(celsius) ?: Unit
    }

    fun setUiListener(l: SensorEvents)  { uiListener = l }
    fun clearUiListener()               { uiListener = null }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AllSensors:ForegroundWakelock")

        val connListener = object : ConnectionListener {
            override fun onConnectionSuccess() {
                reconnectAttempt = 0
                trackerManager.markConnected()
                trackerManager.startContinuous(
                    HealthTrackerType.ACCELEROMETER_CONTINUOUS, AccListener(dao, listenerProxy))
                trackerManager.startContinuous(
                    HealthTrackerType.HEART_RATE_CONTINUOUS, HrListener(dao, listenerProxy))
                trackerManager.startContinuous(
                    HealthTrackerType.PPG_CONTINUOUS, PpgListener(dao, listenerProxy))
                trackerManager.startContinuous(
                    HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS, SkinTempListener(dao, listenerProxy))
                scheduler.start()
            }

            override fun onConnectionFailed(e: HealthTrackerException) {
                Log.e(TAG, "Conexión fallida (errorCode=${e.errorCode})", e)
                serviceScope.launch {
                    Toast.makeText(
                        this@ForegroundService,
                        getString(R.string.msg_connection_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
                scheduleReconnect()
            }

            override fun onConnectionEnded() {
                Log.w(TAG, "HealthTrackingService desconectado — programando reconexión")
                trackerManager.markDisconnected()
                scheduler.stop()
                scheduleReconnect()
            }
        }

        trackerManager = TrackerManager(this, connListener)
        scheduler      = OnDemandScheduler(trackerManager, dao, listenerProxy)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NOTIF_ID, notif)
        }

        wakeLock?.takeIf { !it.isHeld }?.acquire(WAKELOCK_TIMEOUT_MS)

        running = true
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        serviceJob.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        scheduler.destroy()
        trackerManager.stopAllContinuous()
        trackerManager.shutdown()
    }

    // ── Binder ────────────────────────────────────────────────────────────────
    inner class LocalBinder : Binder() {
        fun getService(): ForegroundService = this@ForegroundService
    }

    private val binder = LocalBinder()
    override fun onBind(intent: Intent): IBinder = binder

    // ── API pública ───────────────────────────────────────────────────────────
    fun exportCsv(filename: String) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                CsvExporter.export(dao, this@ForegroundService, filename)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ForegroundService,
                        getString(R.string.msg_csv_saved, filename), Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error exportando CSV", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ForegroundService,
                        getString(R.string.msg_csv_error), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ── Reconexión con backoff exponencial ────────────────────────────────────
    private fun scheduleReconnect() {
        if (!running) return
        val delayMs = (BASE_RECONNECT_MS shl reconnectAttempt.coerceAtMost(MAX_SHIFT))
            .coerceAtMost(MAX_RECONNECT_MS)
        reconnectAttempt++
        Log.i(TAG, "Reconectando en ${delayMs}ms (intento $reconnectAttempt)")
        serviceScope.launch {
            delay(delayMs)
            if (running) trackerManager.reconnect()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = getString(R.string.notif_channel_desc) }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "ForegroundService"
        private const val CHANNEL_ID = "sensors_channel"
        private const val NOTIF_ID = 1
        private const val WAKELOCK_TIMEOUT_MS = 12L * 60 * 60 * 1000

        // Backoff: 2s, 4s, 8s, 16s, 32s, 60s (tope)
        private const val BASE_RECONNECT_MS = 2_000L
        private const val MAX_RECONNECT_MS  = 60_000L
        private const val MAX_SHIFT         = 5       // 2^5 * 2s = 64s > tope

        @Volatile private var running = false
        fun isRunning() = running
    }
}
