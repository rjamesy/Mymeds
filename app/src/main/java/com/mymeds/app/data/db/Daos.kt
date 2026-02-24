package com.mymeds.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.mymeds.app.data.model.DoseLog
import com.mymeds.app.data.model.Medication
import com.mymeds.app.data.model.StockEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {

    @Query("SELECT * FROM medications ORDER BY name ASC")
    fun getAll(): Flow<List<Medication>>

    @Query("SELECT * FROM medications ORDER BY name ASC")
    suspend fun getAllOnce(): List<Medication>

    @Query("SELECT * FROM medications WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Medication?

    @Upsert
    suspend fun upsert(med: Medication)

    @Upsert
    suspend fun upsertAll(meds: List<Medication>)

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM medications")
    suspend fun deleteAll()
}

@Dao
interface DoseLogDao {

    @Query("SELECT * FROM dose_logs ORDER BY scheduledDate DESC, scheduledTime ASC")
    fun getAll(): Flow<List<DoseLog>>

    @Query("SELECT * FROM dose_logs ORDER BY scheduledDate DESC, scheduledTime ASC")
    suspend fun getAllOnce(): List<DoseLog>

    @Query("SELECT * FROM dose_logs WHERE scheduledDate = :date ORDER BY scheduledTime ASC")
    suspend fun getForDate(date: String): List<DoseLog>

    @Query("SELECT * FROM dose_logs WHERE medicationId = :medId ORDER BY scheduledDate DESC, scheduledTime ASC")
    suspend fun getForMedication(medId: String): List<DoseLog>

    @Query("SELECT * FROM dose_logs WHERE scheduledDate >= :start AND scheduledDate <= :end ORDER BY scheduledDate ASC, scheduledTime ASC")
    suspend fun getForDateRange(start: String, end: String): List<DoseLog>

    @Upsert
    suspend fun upsert(log: DoseLog)

    @Upsert
    suspend fun upsertAll(logs: List<DoseLog>)

    @Query("DELETE FROM dose_logs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM dose_logs WHERE medicationId = :medId")
    suspend fun deleteForMedication(medId: String)

    @Query("DELETE FROM dose_logs")
    suspend fun deleteAll()
}

@Dao
interface StockEventDao {

    @Query("SELECT * FROM stock_events ORDER BY createdAt DESC")
    suspend fun getAll(): List<StockEvent>

    @Insert
    suspend fun insert(event: StockEvent)

    @Insert
    suspend fun insertAll(events: List<StockEvent>)

    @Query("DELETE FROM stock_events")
    suspend fun deleteAll()
}
