package com.example.all_sensors.presentation.data;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DataBuffer {
    private final List<DataPoint> buffer = new ArrayList<>();

    public synchronized void add(DataPoint p) {
        buffer.add(p);
    }

    /** Exporta todo lo que hay en el buffer a un CSV y luego lo vacía */
    public synchronized void exportToCsv(Context ctx, String filename) throws IOException {
        File f = new File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), filename);
        try (FileWriter w = new FileWriter(f)) {
            w.write("timestamp,sensor,values\n");
            for (DataPoint p : buffer) {
                w.write(p.timestamp + "," + p.sensorType + "," + p.values + "\n");
            }
        }
        buffer.clear();
    }
}
