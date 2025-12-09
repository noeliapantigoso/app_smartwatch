package com.example.all_sensors.presentation;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.example.all_sensors.presentation.data.DataBuffer;
import com.samsung.android.service.health.tracking.HealthTracker;
import com.samsung.android.service.health.tracking.data.DataPoint;
import com.samsung.android.service.health.tracking.data.HealthTrackerType;
import com.samsung.android.service.health.tracking.data.ValueKey;

import java.util.List;
import java.util.Locale;

/**
 * Scheduler que en un loop ejecuta:
 *   SpO₂ → ECG →
 * con ventana y cool-down configurables.
 */
public class OnDemandScheduler {

    private final TrackerManager trackerManager;
    private final HandlerThread thread;
    private final Handler handler;
    private final DataBuffer buffer;

    // Configuración (milisegundos)
    private long windowSpO2   = 20_000;
    private long cooldownSpO2 = 20_000;
    private long windowECG    = 20_000;
    private long cooldownECG  = 20_000;

    private volatile boolean isRunning = false;

    public OnDemandScheduler(TrackerManager tm, DataBuffer buffer) {
        this.trackerManager = tm;
        this.buffer = buffer;

        thread = new HandlerThread("OnDemandScheduler");
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    /** Arranca el bucle de mediciones */
    public void start() {
        // Si ya está corriendo o todavía no estamos conectados, no hacemos nada
        if (isRunning || !trackerManager.isConnected()) return;

        isRunning = true;
        handler.post(this::loop);
    }

    /** Detiene el bucle y libera el hilo */
    public void stop() {
        isRunning = false;
        handler.removeCallbacksAndMessages(null);
        thread.quitSafely();
    }

    /** El loop principal: SpO₂ → ECG → vuelve a empezar */
    private void loop() {
        if (!isRunning) return;

        try {
            // SpO₂
            trackerManager.runOnDemand(
                    HealthTrackerType.SPO2_ON_DEMAND,
                    new HealthTracker.TrackerEventListener() {
                        @Override
                        public void onDataReceived(List<DataPoint> dataPoints) {
                            long now = System.currentTimeMillis();
                            for (DataPoint dp : dataPoints) {
                                float spo2 = dp.getValue(ValueKey.SpO2Set.SPO2);
                                // logueo:
                                Log.d("SPO2_DATA",
                                        String.format(Locale.US, "SpO₂: %.1f%%", spo2));
                                // almacenas en el buffer (usa tu clase interna DataPoint para CSV):
                                buffer.add(
                                        new com.example.all_sensors.presentation.data.DataPoint(
                                                now,
                                                "SPO2",
                                                String.format(Locale.US, "%.1f", spo2)
                                        )
                                );
                            }
                        }
                        @Override
                        public void onFlushCompleted() {
                            // opcional
                        }
                        @Override
                        public void onError(HealthTracker.TrackerError trackerError) {
                            // opcional: manejo de errores de SpO₂
                        }
                    },
                    windowSpO2
            );
            Thread.sleep(cooldownSpO2);

            // ECG
            trackerManager.runOnDemand(
                    HealthTrackerType.ECG_ON_DEMAND,
                    new HealthTracker.TrackerEventListener() {
                        @Override
                        public void onDataReceived(List<DataPoint> dataPoints) {
                            long now = System.currentTimeMillis();
                            for (DataPoint dp : dataPoints) {
                                float ecg = dp.getValue(ValueKey.EcgSet.ECG_MV);
                                // logueo:
                                Log.d("ECG_DATA",
                                        String.format(Locale.US, "ECG: %.3fmV", ecg));
                                // almacenas en el buffer:
                                buffer.add(
                                        new com.example.all_sensors.presentation.data.DataPoint(
                                                now,
                                                "ECG",
                                                String.format(Locale.US, "%.3f", ecg)
                                        )
                                );
                            }
                        }
                        @Override
                        public void onFlushCompleted() {
                            // opcional
                        }
                        @Override
                        public void onError(HealthTracker.TrackerError trackerError) {
                            // opcional: manejo de errores de ECG
                        }
                    },
                    windowECG
            );
            Thread.sleep(cooldownECG);


        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Repite mientras siga corriendo
        if (isRunning) {
            handler.post(this::loop);
        }
    }

    /** Permite ajustar dinámicamente los tiempos */
    public void configureWindows(long wSpO2, long cdSpO2,
                                 long wECG,  long cdECG) {
        windowSpO2    = wSpO2;   cooldownSpO2 = cdSpO2;
        windowECG     = wECG;    cooldownECG  = cdECG;
    }
}