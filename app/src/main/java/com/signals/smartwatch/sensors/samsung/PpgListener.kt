package com.signals.smartwatch.sensors.samsung

import android.util.Log
import com.signals.smartwatch.data.SensorDao
import com.signals.smartwatch.data.SensorEntity
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.ValueKey

class PpgListener(
    private val dao: SensorDao,
    private val events: com.signals.smartwatch.SensorEvents
) : HealthTracker.TrackerEventListener {

    override fun onDataReceived(dataPoints: List<DataPoint>) {
        val now = System.currentTimeMillis()
        dataPoints.forEach { dp ->
            val green = dp.getValue(ValueKey.PpgSet.PPG_GREEN)
            val ir    = dp.getValue(ValueKey.PpgSet.PPG_IR)
            val red   = dp.getValue(ValueKey.PpgSet.PPG_RED)
            Log.d(TAG, "PPG @$now → G:${"%.1f".format(green)} IR:${"%.1f".format(ir)} R:${"%.1f".format(red)}")
            dao.insertBlocking(SensorEntity(
                timestamp = now,
                sensor    = "PPG",
                values    = "%.2f,%.2f,%.2f".format(green, ir, red)
            ))
            events.onPpg(green.toFloat(), ir.toFloat(), red.toFloat())
        }
    }

    override fun onFlushCompleted() = Unit

    override fun onError(error: HealthTracker.TrackerError) {
        Log.e(TAG, "Error: $error")
    }

    companion object { private const val TAG = "PPG_DATA" }
}
