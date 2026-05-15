package com.signals.smartwatch.tests

import android.content.Context
import android.util.Log
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerCapability
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.PpgType
import java.util.EnumSet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Cliente ligero para pruebas instrumentadas: conecta el SDK, expone la capability
 * y crea trackers con la configuración necesaria.
 */
internal class HealthTrackingTestClient(context: Context) {

    private val connectedLatch = CountDownLatch(1)
    private val startedTrackers = mutableListOf<HealthTracker>()

    @Volatile private var connected = false

    private val service = HealthTrackingService(object : ConnectionListener {
        override fun onConnectionSuccess() {
            connected = true
            connectedLatch.countDown()
        }
        override fun onConnectionFailed(e: HealthTrackerException) {
            Log.e(TAG, "Fallo al conectar con HealthTrackingService", e)
            connectedLatch.countDown()
        }
        override fun onConnectionEnded() { connected = false }
    }, context)

    fun connectOrThrow(timeoutMs: Long) {
        service.connectService()
        check(connectedLatch.await(timeoutMs, TimeUnit.MILLISECONDS) && connected) {
            "No se pudo conectar con HealthTrackingService"
        }
    }

    fun getCapability(): HealthTrackerCapability {
        ensureConnected()
        return service.trackingCapability
    }

    fun startTracking(type: HealthTrackerType, listener: HealthTracker.TrackerEventListener): HealthTracker {
        ensureConnected()
        return buildTracker(type).also {
            it.setEventListener(listener)
            startedTrackers += it
        }
    }

    fun stopAll() {
        startedTrackers.forEach { it.unsetEventListener() }
        startedTrackers.clear()
        if (connected) {
            service.disconnectService()
            connected = false
        }
    }

    private fun buildTracker(type: HealthTrackerType): HealthTracker {
        val ppgTypes = EnumSet.of(PpgType.GREEN)
        return when (type) {
            HealthTrackerType.PPG_CONTINUOUS,
            HealthTrackerType.PPG_ON_DEMAND,
            HealthTrackerType.PPG_GREEN,
            HealthTrackerType.PPG_RED,
            HealthTrackerType.PPG_IR -> service.getHealthTracker(type, ppgTypes)
            else                     -> service.getHealthTracker(type)
        }
    }

    private fun ensureConnected() = check(connected) { "El servicio no está conectado" }

    companion object { private const val TAG = "HealthTrackingTestClient" }
}
