package com.signals.smartwatch.tests

import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.EnumSet

@RunWith(AndroidJUnit4::class)
class HealthTrackingCapabilityTest {

    private lateinit var client: HealthTrackingTestClient

    @Before fun setUp() {
        client = HealthTrackingTestClient(ApplicationProvider.getApplicationContext())
        client.connectOrThrow(5_000)
    }

    @After fun tearDown() = client.stopAll()

    @Test fun supportedSensors_areLogged() {
        val supported = client.getCapability().supportHealthTrackerTypes
        Assert.assertFalse("La lista de sensores soportados viene vacía", supported.isEmpty())
        Log.i(TAG, "Tipos de tracker disponibles: $supported")
    }

    @Test fun listeners_canBeAttachedToAvailableSensors() {
        val interesting = EnumSet.of(
            HealthTrackerType.ACCELEROMETER_CONTINUOUS,
            HealthTrackerType.HEART_RATE_CONTINUOUS,
            HealthTrackerType.PPG_CONTINUOUS,
            HealthTrackerType.SPO2_ON_DEMAND,
            HealthTrackerType.ECG_ON_DEMAND,
            HealthTrackerType.BIA_ON_DEMAND,
            HealthTrackerType.SKIN_TEMPERATURE_ON_DEMAND,
            HealthTrackerType.SWEAT_LOSS
        )

        client.getCapability().supportHealthTrackerTypes
            .filter { it in interesting }
            .forEach { type ->
                Log.i(TAG, "Arrancando listener para: ${type.name}")
                client.startTracking(type, LoggingListener(type))
            }

        SystemClock.sleep(2_000)
    }

    private class LoggingListener(private val type: HealthTrackerType) :
        HealthTracker.TrackerEventListener {

        override fun onDataReceived(list: List<DataPoint>) {
            if (list.isEmpty()) Log.w(TAG, "${type.name} no devolvió muestras")
            else Log.i(TAG, "${type.name} -> ${list[0]}")
        }
        override fun onFlushCompleted() = Log.i(TAG, "${type.name} flush completado")
        override fun onError(err: HealthTracker.TrackerError) = Log.e(TAG, "${type.name} error: $err")
    }

    companion object { private const val TAG = "HealthSensorTest" }
}
