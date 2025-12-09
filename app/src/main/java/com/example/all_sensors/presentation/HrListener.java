package com.example.all_sensors.presentation;

import android.util.Log;

import com.example.all_sensors.presentation.data.DataBuffer;
import com.samsung.android.service.health.tracking.HealthTracker;
import com.samsung.android.service.health.tracking.data.DataPoint;
import com.samsung.android.service.health.tracking.data.ValueKey;

import java.util.List;
import java.util.Locale;

/**
 * Listener para frecuencia cardiaca continua.
 */
public class HrListener implements HealthTracker.TrackerEventListener {
    private final DataBuffer buffer;

    public HrListener(DataBuffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public void onDataReceived(List<DataPoint> dataPoints) {
        long now = System.currentTimeMillis();
        // DataPoint tendrá ValueKey.HeartRateSet.HEART_RATE a 1Hz
        for (com.samsung.android.service.health.tracking.data.DataPoint dp : dataPoints) {
            int hr = dp.getValue(ValueKey.HeartRateSet.HEART_RATE);

            Log.d("ACC_DATA",
                    String.format(Locale.US, "%d", hr));

            buffer.add(new com.example.all_sensors.presentation.data.DataPoint(now,
                    "HR",String.format(Locale.US, "%d", hr)));

            ObserverUpdater.getObserverUpdater().notifyHrObservers(hr);
        }
    }

    @Override
    public void onFlushCompleted() {
        // No suele usarse
    }

    @Override
    public void onError(com.samsung.android.service.health.tracking.HealthTracker.TrackerError error) {
        // TODO: manejo de error
        Log.e("HR_DATA", "HR error");
    }
}
