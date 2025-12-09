package com.example.all_sensors.presentation.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.all_sensors.R;
import com.example.all_sensors.presentation.AccListener;
import com.example.all_sensors.presentation.HrListener;
import com.example.all_sensors.presentation.PpgListener;

import com.example.all_sensors.presentation.OnDemandScheduler;
import com.example.all_sensors.presentation.TrackerManager;
import com.example.all_sensors.presentation.data.DataBuffer;
import com.samsung.android.service.health.tracking.ConnectionListener;
import com.samsung.android.service.health.tracking.HealthTrackerException;
import com.samsung.android.service.health.tracking.data.HealthTrackerType;

import android.os.PowerManager;
import android.widget.Toast;

import java.io.IOException;

/** Service en primer plano que mantiene sensores continuos y lanza on-demand */
public class ForegroundService extends Service {
    private static final String CHANNEL_ID = "sensors_channel";
    private static final int NOTIF_ID = 1;
    private TrackerManager trackerManager;
    private OnDemandScheduler scheduler;
    private PowerManager.WakeLock wakeLock;
    private DataBuffer dataBuffer;

    @Override
    public void onCreate() {
        super.onCreate();

        dataBuffer = new DataBuffer();

        // PREPARA EL WAKELOCK (partial, solo CPU)
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"AllSensors:ForegroundWakelock");

        // 1) Creamos un ConnectionListener que arranca trackers al conectar
        ConnectionListener connListener = new ConnectionListener() {
            @Override
            public void onConnectionSuccess() {
                // Marca en el manager que ya estamos conectados
                trackerManager.markConnected();

                // ¡Ahora sí podemos arrancar los continuos!
                trackerManager.startContinuous(HealthTrackerType.ACCELEROMETER_CONTINUOUS,
                        new AccListener(dataBuffer));
                trackerManager.startContinuous(HealthTrackerType.HEART_RATE_CONTINUOUS,
                        new HrListener(dataBuffer));
                //trackerManager.startContinuous(HealthTrackerType.PPG_CONTINUOUS,
                        //new PpgListener(dataBuffer));

                // Y también arrancamos el scheduler on-demand
                scheduler.start();
            }
            @Override public void onConnectionFailed(HealthTrackerException e) {
                // Maneja fallo (quizá notificar con Toast o reintentar)
            }
            @Override public void onConnectionEnded() {
                trackerManager.markDisconnected();
            }


        };

        // 2) Instanciamos TrackerManager **con** ese listener
        trackerManager = new TrackerManager(this, connListener);

        // 3) Preparamos el scheduler, pero **no** lo arrancamos todavía
        scheduler = new OnDemandScheduler(trackerManager, dataBuffer);
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 4) Crea canal y notificación de primer plano
        createNotificationChannel();
        Notification notif = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Sensores activos")
                .setContentText("Recolección continua y eventos on-demand")
                .setSmallIcon(R.drawable.ic_launcher_foreground)  // icono en mipmap o drawable
                .build();
        startForeground(NOTIF_ID, notif);

        // **5) Adquiere el WakeLock** para que el CPU siga despierto con pantalla apagada
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire();
        }

        // 6) Sticky para que se reinicie si Android mata el service
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // **Libera el WakeLock**
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        // Para scheduler y trackers, desconecta SDK
        if (scheduler != null) scheduler.stop();
        if (trackerManager != null) {
            trackerManager.stopAllContinuous();
            trackerManager.shutdown();
        }
    }

    /** Binder para que la actividad pueda invocar exportCsv() */
    public class LocalBinder extends Binder {
        public ForegroundService getService() {
            return ForegroundService.this;
        }
    }
    private final IBinder binder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /** Llamarás a esto desde tu UI para volcar a CSV */
    public void exportCsv(String filename) {
        try {
            dataBuffer.exportToCsv(this, filename);
            Toast.makeText(this, "CSV grabado: " + filename, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Error exportando CSV", Toast.LENGTH_SHORT).show();
        }
    }


    /** Crea canal de notificación necesario en Android 8+ */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Sensores en primer plano",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Recolección continua de sensores y on-demand");
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }
}
