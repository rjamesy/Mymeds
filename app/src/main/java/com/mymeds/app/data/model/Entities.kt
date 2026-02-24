package com.mymeds.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import org.json.JSONArray

// ── Type Converters ──────────────────────────────────────────────────────────

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        val arr = JSONArray()
        value.forEach { arr.put(it) }
        return arr.toString()
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        val arr = JSONArray(value)
        return (0 until arr.length()).map { arr.getString(it) }
    }
}

// ── Entities ─────────────────────────────────────────────────────────────────

@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey
    val id: String,
    val name: String = "",
    val dosage: String = "",
    val unit: String = "tablet",
    val frequency: String = "daily",
    val timesPerDay: Int = 1,
    val scheduledTimes: List<String> = listOf("08:00"),
    val doseIntervalHours: Int = 6,
    val tabletsPerDose: Int = 1,
    val currentStock: Int = 0,
    val repeatsRemaining: Int = 0,
    val lowStockThreshold: Int = 10,
    val notes: String = "",
    val active: Boolean = true,
    val createdAt: String = "",
    val color: String = MED_COLORS.first()
)

@Entity(tableName = "dose_logs")
data class DoseLog(
    @PrimaryKey
    val id: String,
    val medicationId: String,
    val scheduledDate: String,
    val scheduledTime: String,
    val status: String = "pending",
    val takenAt: String? = null,
    val createdAt: String = ""
)

@Entity(tableName = "stock_events")
data class StockEvent(
    @PrimaryKey
    val id: String,
    val medicationId: String,
    val type: String,
    val quantity: Int,
    val note: String = "",
    val createdAt: String = ""
)

// ── Constants ────────────────────────────────────────────────────────────────

val MED_COLORS: List<String> = listOf(
    "#4f46e5",
    "#7c3aed",
    "#db2777",
    "#dc2626",
    "#ea580c",
    "#d97706",
    "#16a34a",
    "#0891b2",
    "#2563eb",
    "#4338ca",
    "#9333ea",
    "#c026d3",
    "#64748b",
    "#059669",
    "#0284c7",
    "#ffffff"
)

val FREQUENCY_LABELS: Map<String, String> = mapOf(
    "daily" to "Once daily",
    "twice_daily" to "Twice daily",
    "three_times_daily" to "Three times daily",
    "every_other_day" to "Every other day",
    "every_x_hours" to "Every X hours",
    "weekly" to "Once weekly",
    "as_needed" to "As needed"
)

val DEFAULT_TIMES: Map<String, List<String>> = mapOf(
    "daily" to listOf("08:00"),
    "twice_daily" to listOf("08:00", "20:00"),
    "three_times_daily" to listOf("08:00", "14:00", "20:00"),
    "every_other_day" to listOf("08:00"),
    "every_x_hours" to listOf("08:00"),
    "weekly" to listOf("08:00"),
    "as_needed" to emptyList()
)

val FREQUENCY_TIMES: Map<String, Int> = mapOf(
    "daily" to 1,
    "twice_daily" to 2,
    "three_times_daily" to 3,
    "every_other_day" to 1,
    "every_x_hours" to 1,
    "weekly" to 1,
    "as_needed" to 0
)

// ── Stats data classes ───────────────────────────────────────────────────────

data class AdherenceStats(
    val totalDoses: Int,
    val takenDoses: Int,
    val skippedDoses: Int,
    val missedDoses: Int,
    val pendingDoses: Int,
    val adherenceRate: Double
)

data class DayAdherence(
    val date: String,
    val rate: Double
)
