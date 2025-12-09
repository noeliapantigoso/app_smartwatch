package com.example.all_sensors.presentation;

import android.util.Log;

import com.example.all_sensors.presentation.data.DataBuffer;
import com.samsung.android.service.health.tracking.HealthTracker;
import com.samsung.android.service.health.tracking.data.DataPoint;
import com.samsung.android.service.health.tracking.data.ValueKey;

import java.util.List;
import java.util.Locale;

/**
 * Listener para el acelerómetro continuo.
 * Aquí puedes enviar los datos a tu DataBuffer u Observer.
 */
public class AccListener implements HealthTracker.TrackerEventListener {
    private final DataBuffer buffer;

    public AccListener(DataBuffer buffer) {
        this.buffer = buffer;
    }
    @Override
    public void onDataReceived(List<DataPoint> dataPoints) {
        long now = System.currentTimeMillis();
        // dataPoints contendrá AccelerometerSet con X,Y,Z a 25Hz extraes cada datapoint con samsung
        // y conviertes a data point
        for (com.samsung.android.service.health.tracking.data.DataPoint dp : dataPoints) {
            float x = dp.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_X);
            float y = dp.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_Y);
            float z = dp.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_Z);

            Log.d("ACC_DATA",
                    String.format(Locale.US, "%d — x:%.2f, y:%.2f, z:%.2f", now, x, y, z));

            String vals = String.format(Locale.US, "%.2f,%.2f,%.2f", x, y, z);
            buffer.add(new com.example.all_sensors.presentation.data.DataPoint(now, "ACC", vals));

            ObserverUpdater.getObserverUpdater().notifyAccObservers(x, y, z);
        }
    }

    @Override
    public void onFlushCompleted() {
        // No suele usarse para continuos
    }

    @Override
    public void onError(com.samsung.android.service.health.tracking.HealthTracker.TrackerError error) {
        // TODO: manejar error (por ejemplo permisos denegados)
        Log.e("ACC_DATA", "ACC error");
    }


}
