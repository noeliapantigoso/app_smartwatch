package com.signals.smartwatch.sensors.samsung

import android.util.Log
import com.signals.smartwatch.SensorEvents
import com.signals.smartwatch.data.SensorDao
import com.signals.smartwatch.data.SensorEntity
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.ValueKey

class AccListener(
    private val dao: SensorDao,
    private val events: SensorEvents
) : HealthTracker.TrackerEventListener {

    private var logCounter = 0

    override fun onDataReceived(dataPoints: List<DataPoint>) {
        val now = System.currentTimeMillis()
        dataPoints.forEach { dp ->
            val x = dp.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_X)
            val y = dp.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_Y)
            val z = dp.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_Z)

            if (logCounter++ % LOG_EVERY_N == 0) {
                Log.d(TAG, "$now — x:${"%.2f".format(x)}, y:${"%.2f".format(y)}, z:${"%.2f".format(z)}")
            }

            dao.insertBlocking(SensorEntity(
                timestamp = now,
                sensor    = "ACC",
                values    = "%.2f,%.2f,%.2f".format(x, y, z)
            ))
            events.onAcc(x.toFloat(), y.toFloat(), z.toFloat())
        }
    }

    override fun onFlushCompleted() = Unit

    override fun onError(error: HealthTracker.TrackerError) {
        Log.e(TAG, "Error: $error")
    }

    companion object {
        private const val TAG = "ACC_DATA"
        private const val LOG_EVERY_N = 25
    }
}
