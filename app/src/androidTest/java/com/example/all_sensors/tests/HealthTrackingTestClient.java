package com.example.all_sensors.tests;

import android.content.Context;
import android.util.Log;

import com.samsung.android.service.health.tracking.ConnectionListener;
import com.samsung.android.service.health.tracking.HealthTracker;
import com.samsung.android.service.health.tracking.HealthTrackerCapability;
import com.samsung.android.service.health.tracking.HealthTrackerException;
import com.samsung.android.service.health.tracking.HealthTrackingService;
import com.samsung.android.service.health.tracking.data.HealthTrackerType;
import com.samsung.android.service.health.tracking.data.PpgType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Cliente ligero para las pruebas instrumentadas que se encarga de conectar el SDK,
 * exponer la capability y crear trackers con la configuración necesaria.
 */
class HealthTrackingTestClient {
    private static final String TAG = "HealthTrackingTestClient";
    private final HealthTrackingService service;
    private final CountDownLatch connectedLatch = new CountDownLatch(1);
    private final List<HealthTracker> startedTrackers = new ArrayList<>();
    private volatile boolean connected = false;

    HealthTrackingTestClient(Context context) {
        service = new HealthTrackingService(new ConnectionListener() {
            @Override
            public void onConnectionSuccess() {
                connected = true;
                connectedLatch.countDown();
            }

            @Override
            public void onConnectionFailed(HealthTrackerException e) {
                connectedLatch.countDown();
                Log.e(TAG, "Fallo al conectar con HealthTrackingService", e);
            }

            @Override
            public void onConnectionEnded() {
                connected = false;
            }
        }, context);
    }

    void connectOrThrow(long timeoutMs) throws InterruptedException {
        service.connectService();
        if (!connectedLatch.await(timeoutMs, TimeUnit.MILLISECONDS) || !connected) {
            throw new IllegalStateException("No se pudo conectar con HealthTrackingService");
        }
    }

    HealthTrackerCapability getCapability() {
        ensureConnected();
        return service.getTrackingCapability();
    }

    HealthTracker startTracking(HealthTrackerType type, HealthTracker.TrackerEventListener listener) {
        ensureConnected();
        HealthTracker tracker = buildTracker(type);
        tracker.setEventListener(listener);
        startedTrackers.add(tracker);
        return tracker;
    }

    void stopAll() {
        for (HealthTracker tracker : startedTrackers) {
            tracker.unsetEventListener();
        }
        startedTrackers.clear();
        if (connected) {
            service.disconnectService();
            connected = false;
        }
    }

    private HealthTracker buildTracker(HealthTrackerType type) {
        // Para los PPG permitimos pasar una lista explícita de tipos de LED.
        Set<PpgType> ppgTypes = EnumSet.of(PpgType.GREEN);
        switch (type) {
            case PPG_CONTINUOUS:
            case PPG_ON_DEMAND:
            case PPG_GREEN:
            case PPG_RED:
            case PPG_IR:
                return service.getHealthTracker(type, ppgTypes);
            default:
                return service.getHealthTracker(type);
        }
    }

    private void ensureConnected() {
        if (!connected) {
            throw new IllegalStateException("El servicio no está conectado");
        }
    }
}
