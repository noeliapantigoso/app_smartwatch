package com.example.all_sensors.presentation;

import android.content.Context;

import com.samsung.android.service.health.tracking.ConnectionListener;
import com.samsung.android.service.health.tracking.HealthTrackingService;
import com.samsung.android.service.health.tracking.HealthTracker;
import com.samsung.android.service.health.tracking.data.HealthTrackerType;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Envuelve HealthTrackingService y cada HealthTracker.
 * Ahora recibe un ConnectionListener y solo arranca trackers tras onConnectionSuccess().
 */
public class TrackerManager {
    private final HealthTrackingService sdk;
    private final Map<HealthTrackerType, HealthTracker> cache = new HashMap<>();

    /***** Métodos package-private para que el ConnectionListener pueda avisar *****/
    private boolean isConnected = false;

    public void markConnected() {
        isConnected = true;
    }

    public void markDisconnected() {
        isConnected = false;
    }
    private void ensureConnected() {
        if (!isConnected) {
            throw new IllegalStateException("HealthTrackingService aún no conectado");
        }
    }
    public boolean isConnected() {
        return isConnected;
    }


    /**
     * @param context  contexto
     * @param connListener  tu listener que recibe onConnectionSuccess/Failed
     */
    public TrackerManager(Context context, ConnectionListener connListener) {
        // Pasamos siempre un listener no-nulo
        sdk = new HealthTrackingService(connListener, context);
        sdk.connectService();
    }

    /** Devuelve (y cachea) el HealthTracker */
    private HealthTracker getTracker(HealthTrackerType type) {
        ensureConnected();
        return cache.computeIfAbsent(type, t -> sdk.getHealthTracker(t));
    }

    /** Arranca un tracker continuo */
    public void startContinuous(HealthTrackerType type,
                                HealthTracker.TrackerEventListener listener) {
        HealthTracker tr = getTracker(type);
        tr.setEventListener(listener);
    }

    /** Para un tracker continuo */
    public void stopContinuous(HealthTrackerType type) {
        HealthTracker tr = cache.get(type);
        if (tr != null) tr.unsetEventListener();
    }

    public void stopAllContinuous() {
        for (HealthTracker tr : cache.values()) {
            tr.unsetEventListener();
        }
    }

    /** On-demand bloqueante (igual que antes) */
    public synchronized void runOnDemand(HealthTrackerType type,
                                         HealthTracker.TrackerEventListener listener,
                                         long timeoutMs) throws InterruptedException {
        HealthTracker tr = getTracker(type);
        CountDownLatch latch = new CountDownLatch(1);
        HealthTracker.TrackerEventListener wrapped = new HealthTracker.TrackerEventListener() {
            @Override
            public void onDataReceived(java.util.List<com.samsung.android.service.health.tracking.data.DataPoint> dataPoints) {
                listener.onDataReceived(dataPoints);
                latch.countDown();
            }
            @Override public void onFlushCompleted() { listener.onFlushCompleted(); }
            @Override public void onError(com.samsung.android.service.health.tracking.HealthTracker.TrackerError err) {
                listener.onError(err);
                latch.countDown();
            }
        };
        tr.setEventListener(wrapped);
        latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        tr.unsetEventListener();
    }

    public void shutdown() {
        stopAllContinuous();
        sdk.disconnectService();
        cache.clear();
    }
}
