package com.signals.smartwatch.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.signals.smartwatch.R
import com.signals.smartwatch.SensorEvents
import com.signals.smartwatch.service.ForegroundService
import java.util.Locale

/**
 * Implementa [SensorEvents]: el ForegroundService la registra como listener vía
 * LocalBinder y la des-registra al unbind. Sin singletons globales.
 */
class MainActivity : Activity(), SensorEvents {

    private lateinit var accText: TextView
    private lateinit var hrText: TextView
    private lateinit var ppgText: TextView
    private lateinit var spO2Text: TextView
    private lateinit var skinTempText: TextView
    private lateinit var btnStart: Button

    private var serviceRunning = false
    private var svc: ForegroundService? = null
    private var bound = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        accText     = findViewById(R.id.txtAcc)
        hrText      = findViewById(R.id.txtHr)
        ppgText     = findViewById(R.id.txtPpg)
        spO2Text    = findViewById(R.id.txtSpO2)
        skinTempText = findViewById(R.id.txtSkinTemp)
        btnStart    = findViewById(R.id.btnStartService)

        btnStart.setOnClickListener { toggleService() }

        if (!hasAllPermissions()) {
            ActivityCompat.requestPermissions(this, PERMS, REQ_SENSORS)
        }

        serviceRunning = ForegroundService.isRunning()
        btnStart.text = getString(
            if (serviceRunning) R.string.btn_stop_service else R.string.btn_start_service
        )
    }

    // ── SensorEvents ──────────────────────────────────────────────────────────
    override fun onAcc(x: Float, y: Float, z: Float) {
        runOnUiThread {
            accText.text = String.format(Locale.US, "x=%.2f  y=%.2f  z=%.2f", x, y, z)
        }
    }

    override fun onHr(bpm: Int) {
        runOnUiThread { hrText.text = String.format(Locale.US, "HR: %d bpm", bpm) }
    }

    override fun onPpg(green: Float, ir: Float, red: Float) {
        runOnUiThread {
            ppgText.text = String.format(Locale.US, "PPG: G=%.0f  IR=%.0f  R=%.0f", green, ir, red)
        }
    }

    override fun onSpO2(percent: Float) {
        runOnUiThread {
            spO2Text.text = String.format(Locale.US, "SpO2: %.1f %%", percent)
        }
    }

    override fun onSkinTemp(celsius: Float) {
        runOnUiThread {
            skinTempText.text = String.format(Locale.US, "Temp: %.1f °C", celsius)
        }
    }

    // ── Service binding ───────────────────────────────────────────────────────
    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, b: IBinder) {
            svc = (b as ForegroundService.LocalBinder).getService()
            svc!!.setUiListener(this@MainActivity)
            bound = true
        }
        override fun onServiceDisconnected(name: ComponentName) {
            bound = false; svc = null
        }
    }

    override fun onStart() {
        super.onStart()
        if (ForegroundService.isRunning() && !bound) {
            bindService(Intent(this, ForegroundService::class.java), conn, 0)
            serviceRunning = true
            btnStart.text = getString(R.string.btn_stop_service)
        }
    }

    override fun onStop() {
        super.onStop()
        unbindIfBound()
    }

    // ── Botones ───────────────────────────────────────────────────────────────
    private fun toggleService() {
        val intent = Intent(this, ForegroundService::class.java)
        if (!serviceRunning) {
            if (!hasAllPermissions()) {
                Toast.makeText(this, R.string.msg_permissions_required, Toast.LENGTH_SHORT).show()
                ActivityCompat.requestPermissions(this, PERMS, REQ_SENSORS)
                return
            }
            ActivityCompat.startForegroundService(this, intent)
            bindService(intent, conn, 0)
            btnStart.text = getString(R.string.btn_stop_service)
            serviceRunning = true
        } else {
            unbindIfBound()
            stopService(intent)
            btnStart.text = getString(R.string.btn_start_service)
            serviceRunning = false
        }
    }

    /** Conectado al botón btn_save vía android:onClick="onSaveCsvClicked" */
    fun onSaveCsvClicked(@Suppress("UNUSED_PARAMETER") v: View) {
        if (bound && svc != null) {
            svc!!.exportCsv("sesion_${System.currentTimeMillis()}.csv")
        } else {
            Toast.makeText(this, R.string.msg_service_not_active, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == REQ_SENSORS && grantResults.any { it == PackageManager.PERMISSION_DENIED }) {
            Toast.makeText(this, R.string.msg_permissions_denied, Toast.LENGTH_LONG).show()
            finish()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun hasAllPermissions() = PERMS.all {
        ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun unbindIfBound() {
        if (bound) {
            svc?.clearUiListener()
            unbindService(conn)
            bound = false; svc = null
        }
    }

    companion object {
        private const val REQ_SENSORS = 100
        private val PERMS = buildList {
            add(android.Manifest.permission.BODY_SENSORS)
            add(android.Manifest.permission.ACTIVITY_RECOGNITION)
            // BODY_TEMPERATURE requerido en Android 14+ para temperatura de piel
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                add(android.Manifest.permission.BODY_SENSORS_BACKGROUND)
            }
        }.toTypedArray()
    }
}
