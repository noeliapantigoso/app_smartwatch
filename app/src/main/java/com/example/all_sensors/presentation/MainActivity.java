package com.example.all_sensors.presentation;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.util.Locale;

import androidx.core.app.ActivityCompat;

import com.example.all_sensors.R;
import com.example.all_sensors.presentation.ObserverUpdater;
import com.example.all_sensors.presentation.service.ForegroundService;

/**
 * Pantalla principal: inicia/detiene el Service y muestra datos de ACC y HR en tiempo real.
 */
public class MainActivity extends Activity
        implements ObserverUpdater.AccObserver, ObserverUpdater.HrObserver {

    private static final int REQ_SENSORS = 100;
    private static final String[] PERMS = {
            android.Manifest.permission.BODY_SENSORS,
            android.Manifest.permission.ACTIVITY_RECOGNITION
    };

    private TextView accText;
    private TextView hrText;
    private Button btnStart;
    private boolean serviceRunning = false;
    private ForegroundService svc;
    private boolean bound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        accText = findViewById(R.id.txtAcc);
        hrText  = findViewById(R.id.txtHr);
        btnStart = findViewById(R.id.btnStartService);

        btnStart.setOnClickListener(v -> toggleService());

        // Pedir permisos si hacen falta
        boolean ok = true;
        for (String p : PERMS) {
            if (ActivityCompat.checkSelfPermission(this, p)
                    == PackageManager.PERMISSION_DENIED) {
                ok = false;
                break;
            }
        }
        if (!ok) {
            ActivityCompat.requestPermissions(this, PERMS, REQ_SENSORS);
        }

        // Registrar observadores
        ObserverUpdater.getObserverUpdater().addAccObserver(this);
        ObserverUpdater.getObserverUpdater().addHrObserver(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Quitar observadores
        ObserverUpdater.getObserverUpdater().removeAccObserver(this);
        ObserverUpdater.getObserverUpdater().removeHrObserver(this);
    }

    private void toggleService() {
        Intent svc = new Intent(this, ForegroundService.class);
        if (!serviceRunning) {
            ActivityCompat.startForegroundService(this, svc);
            btnStart.setText("Detener Service");
            serviceRunning = true;
        } else {
            stopService(svc);
            btnStart.setText("Iniciar Service");
            serviceRunning = false;
        }
    }

    // ======= Callbacks de AccObserver =======
    @Override
    public void onAccChanged(float x, float y, float z) {
        runOnUiThread(() ->
                accText.setText(String.format(Locale.US,
                        "x=%.2f  y=%.2f  z=%.2f", x, y, z))
        );
    }

    // ======= Callbacks de HrObserver =======
    @Override
    public void onHrChanged(int heartRate) {
        runOnUiThread(() ->
                hrText.setText(String.format(Locale.US, "HR: %d bpm", heartRate))
        );
    }

    // Opcional: manejar resultado de petición de permisos
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        if (requestCode == REQ_SENSORS) {
            boolean granted = true;
            for (int r : grantResults) {
                if (r == PackageManager.PERMISSION_DENIED) {
                    granted = false;
                    break;
                }
            }
            if (!granted) {
                // Muestra mensaje o cierra Activity
                finish();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder b) {
            svc = ((ForegroundService.LocalBinder)b).getService();
            bound = true;
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            bound = false;
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        Intent i = new Intent(this, ForegroundService.class);
        bindService(i, conn, Context.BIND_AUTO_CREATE);
    }
    @Override
    protected void onStop() {
        super.onStop();
        if (bound) {
            unbindService(conn);
            bound = false;
        }
    }

    // Llama a esto desde un botón “Guardar CSV”
    public void onSaveCsvClicked(View v) {
        if (bound) {
            svc.exportCsv("sesion_" + System.currentTimeMillis() + ".csv");
        }
    }

}
