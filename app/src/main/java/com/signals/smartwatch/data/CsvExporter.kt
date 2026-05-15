package com.signals.smartwatch.data

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CsvExporter {

    suspend fun export(dao: SensorDao, context: Context, filename: String) {
        val rows = dao.getAll()
        val file = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            filename
        )
        withContext(Dispatchers.IO) {
            file.bufferedWriter().use { w ->
                w.write("timestamp,sensor,values\n")
                rows.forEach { row ->
                    w.write("${row.timestamp},${row.sensor},${row.values}\n")
                }
            }
        }
        dao.deleteAll()
    }
}
