package com.signals.smartwatch.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SensorDao {
    /** Inserción bloqueante: llamada desde callbacks del SDK (hilos background de Samsung). */
    @Insert
    fun insertBlocking(entity: SensorEntity)

    @Query("SELECT * FROM sensor_data ORDER BY timestamp ASC")
    suspend fun getAll(): List<SensorEntity>

    @Query("SELECT * FROM sensor_data WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp ASC")
    suspend fun getRange(from: Long, to: Long): List<SensorEntity>

    @Query("DELETE FROM sensor_data")
    suspend fun deleteAll()
}
