package com.mymeds.app.util

import com.mymeds.app.data.model.Medication
import java.text.SimpleDateFormat
import java.util.*

// Generate UUID
fun generateId(): String = UUID.randomUUID().toString()

// Get today as "yyyy-MM-dd"
fun getTodayStr(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return sdf.format(Date())
}

// Format "2025-01-15" to "Fri 15 Jan"
fun formatDate(dateStr: String): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val d = sdf.parse(dateStr) ?: return dateStr
    val out = SimpleDateFormat("EEE d MMM", Locale.US)
    return out.format(d)
}

// Format "08:00" to "8:00 AM", "PRN" to "As needed"
fun formatTime(time24: String): String {
    if (time24 == "PRN") return "As needed"
    val parts = time24.split(":")
    if (parts.size != 2) return time24
    val h = parts[0].toIntOrNull() ?: return time24
    val m = parts[1].toIntOrNull() ?: return time24
    val ampm = if (h >= 12) "PM" else "AM"
    val hour = if (h % 12 == 0) 12 else h % 12
    return "$hour:${m.toString().padStart(2, '0')} $ampm"
}

// Check if scheduled time has passed today
fun isTimeOverdue(scheduledTime: String): Boolean {
    if (scheduledTime == "PRN") return false
    val parts = scheduledTime.split(":")
    if (parts.size != 2) return false
    val h = parts[0].toIntOrNull() ?: return false
    val m = parts[1].toIntOrNull() ?: return false
    val now = Calendar.getInstance()
    val scheduled = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, h)
        set(Calendar.MINUTE, m)
        set(Calendar.SECOND, 0)
    }
    return now.after(scheduled)
}

// Days of supply remaining
fun getDaysSupply(med: Medication): Int {
    if (med.frequency == "as_needed") {
        return if (med.currentStock > 0) {
            kotlin.math.ceil(med.currentStock.toDouble() / med.tabletsPerDose).toInt()
        } else 0
    }
    // For every-other-day, daily consumption is halved
    val dailyConsumption = if (med.frequency == "every_other_day") {
        // Consumes on alternating days, so average daily is half
        val perDoseDay = med.timesPerDay * med.tabletsPerDose
        if (perDoseDay == 0) return Int.MAX_VALUE
        // Return supply in actual calendar days (double the doses)
        return (med.currentStock * 2) / perDoseDay
    } else {
        med.timesPerDay * med.tabletsPerDose
    }
    if (dailyConsumption == 0) return Int.MAX_VALUE
    return med.currentStock / dailyConsumption
}

// Stock status
fun getStockStatus(med: Medication): String {
    if (med.currentStock <= 0) return "empty"
    val daysLeft = getDaysSupply(med)
    if (daysLeft <= 1) return "critical"
    if (daysLeft <= med.lowStockThreshold) return "low"
    return "ok"
}

// Repeat status
fun getRepeatStatus(med: Medication): String {
    if (med.repeatsRemaining <= 0) return "critical"
    if (med.repeatsRemaining <= 1) return "warning"
    return "ok"
}

// Get date string for N days ago
fun getDateNDaysAgo(n: Int): String {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, -n)
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
}

// Get day-of-week (0=Sunday..6=Saturday) from ISO date string
fun getDayOfWeek(isoDateStr: String): Int {
    // Handle both "yyyy-MM-dd" and full ISO timestamp
    val dateOnly = isoDateStr.substringBefore("T")
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val date = sdf.parse(dateOnly) ?: return -1
    val cal = Calendar.getInstance()
    cal.time = date
    return cal.get(Calendar.DAY_OF_WEEK) // 1=Sunday .. 7=Saturday
}
