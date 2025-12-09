package com.example.all_sensors.presentation;

import android.util.Log;

import com.example.all_sensors.presentation.data.DataBuffer;
import com.samsung.android.service.health.tracking.HealthTracker;
import com.samsung.android.service.health.tracking.data.DataPoint;
import com.samsung.android.service.health.tracking.data.ValueKey;

import java.util.List;
import java.util.Locale;
public class PpgListener implements HealthTracker.TrackerEventListener{
    private final DataBuffer buffer;
    public PpgListener(DataBuffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public void onDataReceived(List<DataPoint> dataPoints) {
        long now = System.currentTimeMillis();
        for (com.samsung.android.service.health.tracking.data.DataPoint dp : dataPoints) {
            float green = dp.getValue(ValueKey.PpgSet.PPG_RED);
            float ir    = dp.getValue(ValueKey.PpgSet.PPG_IR);
            float red   = dp.getValue(ValueKey.PpgSet.PPG_RED);

            Log.d("PPG_DATA", String.format(Locale.US, "PPG @%d → G:%.1f IR:%.1f R:%.1f",
                    now, green, ir, red));

            String vals = String.format(Locale.US, "%.2f,%.2f,%.2f", green, ir, red);
            buffer.add(new com.example.all_sensors.presentation.data.DataPoint(now, "PPG", vals));

            ObserverUpdater.getObserverUpdater().notifyAccObservers(green,ir,red);
        }
    }

    @Override public void onFlushCompleted() { }
    @Override public void onError(com.samsung.android.service.health.tracking.HealthTracker.TrackerError error) {
        Log.e("PPG_DATA", "PPG error");
    }
}
