package com.signals.smartwatch

/**
 * Callback para muestras de sensores en tiempo real.
 * Las invocaciones llegan desde el hilo del SDK; el consumidor es responsable
 * de re-postear al main thread si toca vistas.
 */
interface SensorEvents {
    fun onAcc(x: Float, y: Float, z: Float)
    fun onHr(bpm: Int)
    fun onPpg(green: Float, ir: Float, red: Float)
    fun onSpO2(percent: Float)
    fun onSkinTemp(celsius: Float)
}
