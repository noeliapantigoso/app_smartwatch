package com.signals.smartwatch.sensors.samsung

import android.util.Log
import com.signals.smartwatch.SensorEvents
import com.signals.smartwatch.data.SensorDao
import com.signals.smartwatch.data.SensorEntity
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.ValueKey

class HrListener(
    private val dao: SensorDao,
    private val events: SensorEvents
) : HealthTracker.TrackerEventListener {

    override fun onDataReceived(dataPoints: List<DataPoint>) {
        val now = System.currentTimeMillis()
        dataPoints.forEach { dp ->
            val status = dp.getValue(ValueKey.HeartRateSet.HEART_RATE_STATUS)

            // Status 1 = medición válida según documentación Samsung Health SDK
            if (status != STATUS_MEASUREMENT_VALID) {
                Log.w(TAG, "HR descartado — status=$status")
                return@forEach
            }

            val hr = dp.getValue(ValueKey.HeartRateSet.HEART_RATE)
            Log.d(TAG, hr.toString())
            dao.insertBlocking(SensorEntity(timestamp = now, sensor = "HR", values = hr.toString()))
            events.onHr(hr)
        }
    }

    override fun onFlushCompleted() = Unit

    override fun onError(error: HealthTracker.TrackerError) {
        Log.e(TAG, "Error: $error")
    }

    companion object {
        private const val TAG = "HR_DATA"
        private const val STATUS_MEASUREMENT_VALID = 1
    }
}
