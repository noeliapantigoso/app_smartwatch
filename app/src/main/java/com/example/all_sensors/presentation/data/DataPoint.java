package com.example.all_sensors.presentation.data;

/** Representa un punto de datos genérico */
public class DataPoint {
    public final long timestamp;
    public final String sensorType; // p.ej. "ACC", "HR", "SPO2"
    public final String values;     // p.ej. "0.12,0.34,9.81" o "72" para HR

    public DataPoint(long timestamp, String sensorType, String values) {
        this.timestamp  = timestamp;
        this.sensorType = sensorType;
        this.values     = values;
    }
}
