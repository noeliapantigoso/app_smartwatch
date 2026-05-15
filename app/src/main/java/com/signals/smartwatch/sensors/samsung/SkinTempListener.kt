package com.signals.smartwatch.sensors.samsung

import android.util.Log
import com.signals.smartwatch.data.SensorDao
import com.signals.smartwatch.data.SensorEntity
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.ValueKey

/**
 * Listener para temperatura de piel (muñeca) continua a 1 Hz.
 * Disponible en Galaxy Watch 5 y superior.
 * No requiere interacción del usuario — completamente pasivo.
 *
 * Nota: mide temperatura superficial de la muñeca, no temperatura corporal central.
 * La diferencia con la temperatura corporal real suele ser ~2-4 °C.
 */
class SkinTempListener(
    private val dao: SensorDao,
    private val events: com.signals.smartwatch.SensorEvents
) : HealthTracker.TrackerEventListener {

    override fun onDataReceived(dataPoints: List<DataPoint>) {
        val now = System.currentTimeMillis()
        dataPoints.forEach { dp ->
            val status = dp.getValue(ValueKey.SkinTemperatureSet.STATUS)

            if (status != STATUS_VALID) {
                Log.w(TAG, "Temperatura descartada — status=$status")
                return@forEach
            }

            val skinTemp = dp.getValue<Float>(ValueKey.SkinTemperatureSet.OBJECT_TEMPERATURE)
            Log.d(TAG, "SkinTemp: ${"%.1f".format(skinTemp)}°C")

            dao.insertBlocking(SensorEntity(
                timestamp = now,
                sensor    = "SKIN_TEMP",
                values    = "%.1f".format(skinTemp)
            ))
            events.onSkinTemp(skinTemp)
        }
    }

    override fun onFlushCompleted() = Unit

    override fun onError(error: HealthTracker.TrackerError) {
        Log.e(TAG, "Error: $error")
    }

    companion object {
        private const val TAG = "SKIN_TEMP_DATA"
        private const val STATUS_VALID = 1
    }
}
