package com.signals.smartwatch.sensors.samsung

import android.content.Context
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

class TrackerManager(context: Context, connListener: ConnectionListener) {

    private val sdk = HealthTrackingService(connListener, context).also { it.connectService() }
    private val cache = mutableMapOf<HealthTrackerType, HealthTracker>()

    @Volatile private var connected = false

    fun markConnected()    { connected = true }
    fun markDisconnected() { connected = false }
    fun isConnected()      = connected

    private fun ensureConnected() {
        check(connected) { "HealthTrackingService aún no conectado" }
    }

    private fun getTracker(type: HealthTrackerType): HealthTracker {
        ensureConnected()
        return synchronized(cache) { cache.getOrPut(type) { sdk.getHealthTracker(type) } }
    }

    fun startContinuous(type: HealthTrackerType, listener: HealthTracker.TrackerEventListener) {
        getTracker(type).setEventListener(listener)
    }

    fun stopContinuous(type: HealthTrackerType) {
        cache[type]?.unsetEventListener()
    }

    fun stopAllContinuous() {
        cache.values.forEach { it.unsetEventListener() }
    }

    /**
     * Mantiene el listener activo durante [timeoutMs] para recibir todas las muestras
     * de la ventana. Termina antes solo si el SDK reporta error.
     */
    suspend fun runOnDemand(
        type: HealthTrackerType,
        listener: HealthTracker.TrackerEventListener,
        timeoutMs: Long
    ) {
        val tracker = synchronized(cache) { getTracker(type) }
        val errorSignal = CompletableDeferred<Unit>()

        val wrapped = object : HealthTracker.TrackerEventListener {
            override fun onDataReceived(dataPoints: List<DataPoint>) =
                listener.onDataReceived(dataPoints)
            override fun onFlushCompleted() = listener.onFlushCompleted()
            override fun onError(err: HealthTracker.TrackerError) {
                listener.onError(err)
                errorSignal.complete(Unit)
            }
        }
        tracker.setEventListener(wrapped)
        try {
            withTimeoutOrNull(timeoutMs) { errorSignal.await() }
        } finally {
            tracker.unsetEventListener()
        }
    }

    /** Inicia un nuevo intento de conexión tras una desconexión inesperada. */
    fun reconnect() {
        sdk.connectService()
    }

    fun shutdown() {
        stopAllContinuous()
        sdk.disconnectService()
        cache.clear()
    }
}
