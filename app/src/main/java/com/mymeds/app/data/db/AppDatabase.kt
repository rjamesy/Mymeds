package com.mymeds.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mymeds.app.data.model.Converters
import com.mymeds.app.data.model.DoseLog
import com.mymeds.app.data.model.Medication
import com.mymeds.app.data.model.StockEvent

@Database(
    entities = [Medication::class, DoseLog::class, StockEvent::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun medicationDao(): MedicationDao
    abstract fun doseLogDao(): DoseLogDao
    abstract fun stockEventDao(): StockEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mymeds.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
