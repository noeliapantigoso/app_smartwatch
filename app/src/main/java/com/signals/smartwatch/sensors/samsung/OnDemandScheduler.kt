package com.signals.smartwatch.sensors.samsung

import android.util.Log
import com.signals.smartwatch.data.SensorDao
import com.signals.smartwatch.data.SensorEntity
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Mide SpO2 de forma periódica mientras el usuario lleva el reloj puesto.
 * No requiere interacción: el sensor óptico de muñeca funciona de forma pasiva
 * (aunque puede dar STATUS_NO_MEASUREMENT si hay mucho movimiento).
 *
 * ECG se excluye del bucle automático porque requiere que el usuario coloque
 * activamente el dedo en el electrodo — se implementará como botón manual.
 */
class OnDemandScheduler(
    private val trackerManager: TrackerManager,
    private val dao: SensorDao,
    private val events: com.signals.smartwatch.SensorEvents
) {
    /** Tiempo que el SDK tiene para completar la medición de SpO2 (máx 30s por SDK). */
    var windowSpO2   = 20_000L
    /** Pausa entre mediciones de SpO2. */
    var cooldownSpO2 = 60_000L   // 1 minuto: razonable para uso diario

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true || !trackerManager.isConnected()) return
        job = scope.launch { measurementLoop() }
    }

    /** Cancela la medición en curso pero deja el scope vivo para poder relanzar con [start]. */
    fun stop() {
        job?.cancel()
        job = null
    }

    /** Cierre definitivo; llamar solo desde onDestroy del servicio. */
    fun destroy() {
        scope.cancel()
    }

    private suspend fun measurementLoop() {
        while (currentCoroutineContext().isActive) {
            measureSpO2()
            delay(cooldownSpO2)
        }
    }

    private suspend fun measureSpO2() {
        trackerManager.runOnDemand(
            HealthTrackerType.SPO2_ON_DEMAND,
            object : HealthTracker.TrackerEventListener {
                override fun onDataReceived(dataPoints: List<DataPoint>) {
                    val now = System.currentTimeMillis()
                    dataPoints.forEach { dp ->
                        val status = dp.getValue(ValueKey.SpO2Set.STATUS)

                        // Solo guardar si la medición fue exitosa
                        if (status != STATUS_MEASUREMENT_COMPLETED) {
                            Log.w(TAG, "SpO2 descartado — status=$status (medición no completada)")
                            return@forEach
                        }

                        val spo2 = dp.getValue(ValueKey.SpO2Set.SPO2)
                        Log.d(TAG, "SpO₂: ${"%.1f".format(spo2)}%")
                        dao.insertBlocking(SensorEntity(
                            timestamp = now,
                            sensor    = "SPO2",
                            values    = "%.1f".format(spo2)
                        ))
                        events.onSpO2(spo2.toFloat())
                    }
                }
                override fun onFlushCompleted() = Unit
                override fun onError(err: HealthTracker.TrackerError) {
                    Log.e(TAG, "SpO2 error: $err")
                }
            },
            windowSpO2
        )
    }

    companion object {
        private const val TAG = "OnDemandScheduler"

        // Según la documentación Samsung Health SDK
        private const val STATUS_MEASUREMENT_COMPLETED = 1
    }
}
