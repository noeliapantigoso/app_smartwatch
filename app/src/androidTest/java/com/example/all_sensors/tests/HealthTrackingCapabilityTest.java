package com.example.all_sensors.tests;

import android.os.SystemClock;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.samsung.android.service.health.tracking.HealthTracker;
import com.samsung.android.service.health.tracking.data.DataPoint;
import com.samsung.android.service.health.tracking.data.HealthTrackerType;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Conjunto de pruebas instrumentadas para listar sensores soportados y arrancar
 * listeners básicos sobre los que devuelvan datos.
 */
@RunWith(AndroidJUnit4.class)
public class HealthTrackingCapabilityTest {
    private static final String TAG = "HealthSensorTest";
    private HealthTrackingTestClient client;

    @Before
    public void setUp() throws Exception {
        client = new HealthTrackingTestClient(ApplicationProvider.getApplicationContext());
        client.connectOrThrow(5000);
    }

    @After
    public void tearDown() {
        if (client != null) {
            client.stopAll();
        }
    }

    @Test
    public void supportedSensors_areLogged() {
        List<HealthTrackerType> supported = client.getCapability().getSupportHealthTrackerTypes();
        Assert.assertFalse("La lista de sensores soportados viene vacía", supported.isEmpty());
        Log.i(TAG, "Tipos de tracker disponibles: " + supported);
    }

    @Test
    public void listeners_canBeAttachedToAvailableSensors() {
        Set<HealthTrackerType> interesting = EnumSet.of(
                HealthTrackerType.ACCELEROMETER_CONTINUOUS,
                HealthTrackerType.HEART_RATE_CONTINUOUS,
                HealthTrackerType.PPG_CONTINUOUS,
                HealthTrackerType.SPO2_ON_DEMAND,
                HealthTrackerType.ECG_ON_DEMAND,
                HealthTrackerType.BIA_ON_DEMAND,
                HealthTrackerType.SKIN_TEMPERATURE_ON_DEMAND,
                HealthTrackerType.SWEAT_LOSS
        );

        List<HealthTrackerType> supported = client.getCapability().getSupportHealthTrackerTypes();
        for (HealthTrackerType type : supported) {
            if (!interesting.contains(type)) {
                continue;
            }
            Log.i(TAG, "Arrancando listener para: " + type.name());
            client.startTracking(type, new LoggingTrackerListener(type));
        }

        // Espera breve para recibir callbacks y validar que los listeners están configurados
        SystemClock.sleep(2000);
    }

    private static class LoggingTrackerListener implements HealthTracker.TrackerEventListener {
        private final HealthTrackerType type;

        LoggingTrackerListener(HealthTrackerType type) {
            this.type = type;
        }

        @Override
        public void onDataReceived(List<DataPoint> list) {
            if (list == null || list.isEmpty()) {
                Log.w(TAG, type.name() + " no devolvió muestras durante la ventana de prueba");
            } else {
                Log.i(TAG, type.name() + " -> " + list.get(0));
            }
        }

        @Override
        public void onFlushCompleted() {
            Log.i(TAG, type.name() + " flush completado");
        }

        @Override
        public void onError(HealthTracker.TrackerError trackerError) {
            Log.e(TAG, type.name() + " devolvió error: " + trackerError);
        }
    }
}
