package com.example.all_sensors.presentation;

import java.util.ArrayList;
import java.util.List;

/**
 * Clase singleton para manejar observadores de distintos tipos de datos de sensores.
 */
public class ObserverUpdater {
    private static ObserverUpdater instance;

    // Observers existentes
    private final List<TrackerObserver> trackerObservers = new ArrayList<>();
    private final List<ConnectionObserver> connectionObservers = new ArrayList<>();

    // Observers para acelerómetro y frecuencia cardíaca
    private final List<AccObserver> accObservers = new ArrayList<>();
    private final List<HrObserver>  hrObservers  = new ArrayList<>();

    private ObserverUpdater() { }

    /** Devuelve la instancia singleton */
    public static ObserverUpdater getObserverUpdater() {
        if (instance == null) {
            instance = new ObserverUpdater();
        }
        return instance;
    }

    // ---------- TrackerObservers ----------
    public void addTrackerObserver(TrackerObserver o)       { trackerObservers.add(o); }
    public void removeTrackerObserver(TrackerObserver o)    { trackerObservers.remove(o); }
    public void notifyTrackerObservers(int status, int spo2) {
        for (TrackerObserver o : trackerObservers) {
            o.onTrackerDataChanged(status, spo2);
        }
    }
    public void displayError(int errorResId) {
        for (TrackerObserver o : trackerObservers) {
            o.onError(errorResId);
        }
    }

    // ---------- ConnectionObservers ----------
    public void addConnectionObserver(ConnectionObserver o)    { connectionObservers.add(o); }
    public void removeConnectionObserver(ConnectionObserver o) { connectionObservers.remove(o); }
    public void notifyConnectionObservers(int resId) {
        for (ConnectionObserver o : connectionObservers) {
            o.onConnectionResult(resId);
        }
    }

    // ---------- AccObservers ----------
    public void addAccObserver(AccObserver o)      { accObservers.add(o); }
    public void removeAccObserver(AccObserver o)   { accObservers.remove(o); }
    public void notifyAccObservers(float x, float y, float z) {
        for (AccObserver o : accObservers) {
            o.onAccChanged(x, y, z);
        }
    }

    // ---------- HrObservers ----------
    public void addHrObserver(HrObserver o)      { hrObservers.add(o); }
    public void removeHrObserver(HrObserver o)   { hrObservers.remove(o); }
    public void notifyHrObservers(int hr) {
        for (HrObserver o : hrObservers) {
            o.onHrChanged(hr);
        }
    }

    // ======= Interfaces de observadores =======
    /** Observador para datos de acelerómetro */
    public interface AccObserver {
        void onAccChanged(float x, float y, float z);
    }

    /** Observador para datos de frecuencia cardíaca */
    public interface HrObserver {
        void onHrChanged(int heartRate);
    }

    /** Observador para datos de SpO2 (tracker on-demand) */
    public interface TrackerObserver {
        void onTrackerDataChanged(int status, int spo2Value);
        void onError(int errorResourceId);
    }

    /** Observador para eventos de conexión de servicio */
    public interface ConnectionObserver {
        void onConnectionResult(int statusResourceId);
    }
}
