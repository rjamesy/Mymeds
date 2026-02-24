package com.mymeds.app.data.repository

import com.mymeds.app.data.db.AppDatabase
import com.mymeds.app.data.model.AdherenceStats
import com.mymeds.app.data.model.DayAdherence
import com.mymeds.app.data.model.DoseLog
import com.mymeds.app.data.model.MED_COLORS
import com.mymeds.app.data.model.Medication
import com.mymeds.app.data.model.StockEvent
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

class MedsRepository(private val db: AppDatabase) {

    private val medicationDao = db.medicationDao()
    private val doseLogDao = db.doseLogDao()
    private val stockEventDao = db.stockEventDao()
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    // ── Medication operations ────────────────────────────────────────────────

    fun getAllMedications(): Flow<List<Medication>> = medicationDao.getAll()

    suspend fun getAllMedicationsOnce(): List<Medication> = medicationDao.getAllOnce()

    suspend fun getMedicationById(id: String): Medication? = medicationDao.getById(id)

    suspend fun upsertMedication(med: Medication) = medicationDao.upsert(med)

    suspend fun deleteMedication(id: String) {
        doseLogDao.deleteForMedication(id)
        medicationDao.delete(id)
    }

    // ── DoseLog operations ───────────────────────────────────────────────────

    fun getAllDoseLogs(): Flow<List<DoseLog>> = doseLogDao.getAll()

    suspend fun getAllDoseLogsOnce(): List<DoseLog> = doseLogDao.getAllOnce()

    suspend fun getDoseLogsForDate(date: String): List<DoseLog> = doseLogDao.getForDate(date)

    suspend fun getDoseLogsForMedication(medId: String): List<DoseLog> =
        doseLogDao.getForMedication(medId)

    suspend fun getDoseLogsForDateRange(start: String, end: String): List<DoseLog> =
        doseLogDao.getForDateRange(start, end)

    suspend fun upsertDoseLog(log: DoseLog) = doseLogDao.upsert(log)

    // ── StockEvent operations ────────────────────────────────────────────────

    suspend fun getAllStockEvents(): List<StockEvent> = stockEventDao.getAll()

    suspend fun insertStockEvent(event: StockEvent) = stockEventDao.insert(event)

    // ── Business logic ───────────────────────────────────────────────────────

    suspend fun takeDose(log: DoseLog, med: Medication) {
        val takenAt = LocalDateTime.now()
        val now = takenAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        val updatedLog = log.copy(
            status = "taken",
            takenAt = now
        )
        doseLogDao.upsert(updatedLog)

        shiftRemainingDosesForToday(
            takenLog = log,
            med = med,
            takenAt = takenAt
        )

        val newStock = (med.currentStock - med.tabletsPerDose).coerceAtLeast(0)
        medicationDao.upsert(med.copy(currentStock = newStock))

        stockEventDao.insert(
            StockEvent(
                id = UUID.randomUUID().toString(),
                medicationId = med.id,
                type = "consumed",
                quantity = med.tabletsPerDose,
                note = "Dose taken",
                createdAt = now
            )
        )
    }

    /**
     * For today's doses only, push later pending doses forward so they remain at least
     * `doseIntervalHours` after the actual taken time.
     *
     * This preserves safety for overdue doses taken late (e.g., 1pm dose makes next dose 7pm
     * when interval is 6h) and keeps reminders/UI aligned with the adjusted schedule.
     */
    private suspend fun shiftRemainingDosesForToday(
        takenLog: DoseLog,
        med: Medication,
        takenAt: LocalDateTime
    ) {
        if (takenLog.scheduledTime == "PRN") return

        val today = LocalDate.now()
        val scheduledDate = runCatching { LocalDate.parse(takenLog.scheduledDate) }.getOrNull()
            ?: return
        if (scheduledDate != today) return

        val takenScheduledTime = parseTimeOrNull(takenLog.scheduledTime) ?: return
        val intervalHours = med.doseIntervalHours.coerceIn(1, 6).toLong()

        val pendingLaterLogs = doseLogDao.getForDate(takenLog.scheduledDate)
            .asSequence()
            .filter { it.medicationId == med.id }
            .filter { it.status == "pending" && it.scheduledTime != "PRN" }
            .mapNotNull { log ->
                val t = parseTimeOrNull(log.scheduledTime) ?: return@mapNotNull null
                log to t
            }
            .filter { (_, time) -> time.isAfter(takenScheduledTime) }
            .sortedBy { (_, time) -> time }
            .toList()

        if (pendingLaterLogs.isEmpty()) return

        var previousDoseDateTime = takenAt
        val endOfDay = LocalDateTime.of(scheduledDate, LocalTime.of(23, 59))

        for ((pendingLog, pendingTime) in pendingLaterLogs) {
            val scheduledDateTime = LocalDateTime.of(scheduledDate, pendingTime)
            val earliestAllowed = previousDoseDateTime.plusHours(intervalHours)

            val adjustedDateTime = when {
                scheduledDateTime.isBefore(earliestAllowed) && earliestAllowed.isAfter(endOfDay) ->
                    endOfDay
                scheduledDateTime.isBefore(earliestAllowed) ->
                    earliestAllowed
                else -> scheduledDateTime
            }

            val adjustedTime = adjustedDateTime.format(timeFormatter)
            if (adjustedTime != pendingLog.scheduledTime) {
                doseLogDao.upsert(pendingLog.copy(scheduledTime = adjustedTime))
            }
            previousDoseDateTime = adjustedDateTime
        }
    }

    suspend fun skipDose(log: DoseLog) {
        doseLogDao.upsert(log.copy(status = "skipped"))
    }

    suspend fun undoDose(log: DoseLog, med: Medication) {
        val wasTaken = log.status == "taken"

        doseLogDao.upsert(log.copy(status = "pending", takenAt = null))

        if (wasTaken) {
            val restoredStock = med.currentStock + med.tabletsPerDose
            medicationDao.upsert(med.copy(currentStock = restoredStock))

            val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            stockEventDao.insert(
                StockEvent(
                    id = UUID.randomUUID().toString(),
                    medicationId = med.id,
                    type = "adjusted",
                    quantity = med.tabletsPerDose,
                    note = "Dose undone — stock restored",
                    createdAt = now
                )
            )
        }
    }

    suspend fun markMissed(log: DoseLog, med: Medication) {
        val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        if (log.status == "taken") {
            // Restore stock since dose was previously recorded as taken
            val restoredStock = med.currentStock + med.tabletsPerDose
            medicationDao.upsert(med.copy(currentStock = restoredStock))

            stockEventDao.insert(
                StockEvent(
                    id = UUID.randomUUID().toString(),
                    medicationId = med.id,
                    type = "adjusted",
                    quantity = med.tabletsPerDose,
                    note = "Dose changed to missed — stock restored",
                    createdAt = now
                )
            )
        }

        doseLogDao.upsert(log.copy(status = "missed", takenAt = null))
    }

    suspend fun addStock(medId: String, quantity: Int, note: String = "") {
        val med = medicationDao.getById(medId) ?: return
        medicationDao.upsert(med.copy(currentStock = med.currentStock + quantity))

        val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        stockEventDao.insert(
            StockEvent(
                id = UUID.randomUUID().toString(),
                medicationId = medId,
                type = "added",
                quantity = quantity,
                note = note.ifBlank { "Stock added" },
                createdAt = now
            )
        )
    }

    suspend fun useRepeat(medId: String) {
        val med = medicationDao.getById(medId) ?: return
        if (med.repeatsRemaining > 0) {
            medicationDao.upsert(med.copy(repeatsRemaining = med.repeatsRemaining - 1))
        }
    }

    /**
     * Generate today's dose log entries for all active medications.
     *
     * Logic:
     * - as_needed: single entry with scheduledTime = "PRN"
     * - weekly: only generate if today matches the day-of-week the medication was created on
     * - daily / twice_daily / three_times_daily: one entry per scheduledTime
     * - Existing logs for today are preserved (no duplicates)
     *
     * Returns: sorted list — scheduled times first (ascending), PRN entries at the end.
     */
    suspend fun generateTodaysDoses(): List<DoseLog> {
        val allToday = ensureDoseLogsForDate(LocalDate.now())
        return allToday.sortedWith(compareBy<DoseLog> {
            if (it.scheduledTime == "PRN") 1 else 0
        }.thenBy { it.scheduledTime })
    }

    /**
     * Ensure dose logs exist for a specific date, creating missing entries from active meds.
     *
     * This is used by both UI flows and background notification workers so overdue checks
     * still work even if the app has not been opened that day.
     */
    suspend fun ensureDoseLogsForDate(date: LocalDate): List<DoseLog> {
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val today = LocalDate.now()

        val activeMeds = medicationDao.getAllOnce().filter { it.active }
        val existingLogs = doseLogDao.getForDate(dateStr)
        val expectedTimesByMedication = mutableMapOf<String, Set<String>>()

        // ── Step 1: Reconcile stale logs when medication times have changed ──
        for (med in activeMeds) {
            val logsForMed = existingLogs.filter { it.medicationId == med.id }
            val baseExpectedTimes = expectedTimesForDate(med, date)
            val preserveAdjustedTodayTimes = date == today &&
                med.frequency != "as_needed" &&
                logsForMed.any { it.status == "taken" && it.takenAt != null }

            val expectedTimes = if (preserveAdjustedTodayTimes) {
                val existingTimes = logsForMed
                    .map { it.scheduledTime }
                    .filter { it != "PRN" }
                    .toSet()
                if (existingTimes.isNotEmpty()) existingTimes else baseExpectedTimes
            } else {
                baseExpectedTimes
            }

            expectedTimesByMedication[med.id] = expectedTimes

            val staleLogs = logsForMed.filter { it.scheduledTime !in expectedTimes }
            val matchedTimes = logsForMed.filter { it.scheduledTime in expectedTimes }
                .map { it.scheduledTime }.toMutableSet()

            // Times that need a new log (not yet covered by an existing log)
            val uncoveredTimes = expectedTimes.toMutableSet().apply { removeAll(matchedTimes) }

            for (staleLog in staleLogs) {
                if (staleLog.status == "taken" || staleLog.status == "skipped") {
                    // Reassign to an uncovered time slot, preserving status
                    val newTime = uncoveredTimes.firstOrNull()
                    if (newTime != null) {
                        uncoveredTimes.remove(newTime)
                        doseLogDao.upsert(staleLog.copy(scheduledTime = newTime))
                    } else {
                        // No uncovered time left — user reduced doses, delete the extra
                        doseLogDao.deleteById(staleLog.id)
                    }
                } else {
                    // Pending/missed stale log — just delete it
                    doseLogDao.deleteById(staleLog.id)
                }
            }
        }

        // ── Step 2: Re-read logs after reconciliation and create any still-missing entries ──
        val reconciledLogs = doseLogDao.getForDate(dateStr)
        val reconciledKeys = reconciledLogs.map { it.medicationId to it.scheduledTime }.toSet()

        val newLogs = mutableListOf<DoseLog>()

        for (med in activeMeds) {
            val expectedTimes = expectedTimesByMedication[med.id].orEmpty()
            if (med.frequency == "as_needed") {
                val key = med.id to "PRN"
                if (key !in reconciledKeys) {
                    newLogs.add(
                        DoseLog(
                            id = UUID.randomUUID().toString(),
                            medicationId = med.id,
                            scheduledDate = dateStr,
                            scheduledTime = "PRN",
                            status = "pending",
                            takenAt = null,
                            createdAt = now
                        )
                    )
                }
                continue
            }

            for (time in expectedTimes) {
                if (time == "PRN") continue
                val key = med.id to time
                if (key !in reconciledKeys) {
                    newLogs.add(
                        DoseLog(
                            id = UUID.randomUUID().toString(),
                            medicationId = med.id,
                            scheduledDate = dateStr,
                            scheduledTime = time,
                            status = "pending",
                            takenAt = null,
                            createdAt = now
                        )
                    )
                }
            }
        }

        for (log in newLogs) {
            doseLogDao.upsert(log)
        }

        // ── Step 3: Deduplicate — remove duplicate entries for the same
        //    (medicationId, scheduledTime) on this date. This guards against
        //    race conditions when multiple callers invoke ensureDoseLogsForDate
        //    concurrently (ViewModel, AlarmScheduler, WorkManager, etc.).
        val finalLogs = doseLogDao.getForDate(dateStr)
        val grouped = finalLogs.groupBy { it.medicationId to it.scheduledTime }
        for ((_, group) in grouped) {
            if (group.size <= 1) continue
            // Keep the log with the most significant status
            val keeper = group.maxByOrNull { log ->
                when (log.status) {
                    "taken" -> 3
                    "skipped" -> 2
                    "missed" -> 1
                    else -> 0
                }
            }!!
            group.filter { it.id != keeper.id }.forEach { doseLogDao.deleteById(it.id) }
        }

        return doseLogDao.getForDate(dateStr)
    }

    private fun shouldScheduleWeeklyDoseOnDate(med: Medication, date: LocalDate): Boolean {
        val createdDate = parseCreatedDate(med) ?: return false
        return date.dayOfWeek == createdDate.dayOfWeek
    }

    /**
     * For "every_other_day" frequency: check if the given date is an even number
     * of days from the creation date (0, 2, 4, 6, …).
     */
    private fun shouldScheduleEveryOtherDay(med: Medication, date: LocalDate): Boolean {
        val createdDate = parseCreatedDate(med) ?: return true
        val daysBetween = ChronoUnit.DAYS.between(createdDate, date)
        return daysBetween >= 0 && daysBetween % 2 == 0L
    }

    private fun expectedTimesForDate(med: Medication, date: LocalDate): Set<String> {
        return when (med.frequency) {
            "as_needed" -> setOf("PRN")
            "weekly" -> if (shouldScheduleWeeklyDoseOnDate(med, date))
                med.scheduledTimes.filter { it != "PRN" }.toSet() else emptySet()
            "every_other_day" -> if (shouldScheduleEveryOtherDay(med, date))
                med.scheduledTimes.filter { it != "PRN" }.toSet() else emptySet()
            else -> med.scheduledTimes.filter { it != "PRN" }.toSet()
        }
    }

    private fun parseTimeOrNull(time: String): LocalTime? {
        return runCatching { LocalTime.parse(time) }.getOrNull()
    }

    private fun parseCreatedDate(med: Medication): LocalDate? {
        return try {
            LocalDate.parse(med.createdAt.take(10))
        } catch (_: Exception) {
            try {
                LocalDateTime.parse(med.createdAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .toLocalDate()
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Calculate adherence statistics for a date range.
     */
    suspend fun getAdherenceStats(startDate: String, endDate: String): AdherenceStats {
        val logs = doseLogDao.getForDateRange(startDate, endDate)
            .filter { it.scheduledTime != "PRN" }

        val total = logs.size
        val taken = logs.count { it.status == "taken" }
        val skipped = logs.count { it.status == "skipped" }
        val missed = logs.count { it.status == "missed" }
        val pending = logs.count { it.status == "pending" }

        val rate = if (total > 0) {
            (taken.toDouble() / total) * 100.0
        } else {
            0.0
        }

        return AdherenceStats(
            totalDoses = total,
            takenDoses = taken,
            skippedDoses = skipped,
            missedDoses = missed,
            pendingDoses = pending,
            adherenceRate = rate
        )
    }

    /**
     * Get adherence rate for each of the last 7 days.
     */
    suspend fun getLast7DaysAdherence(): List<DayAdherence> {
        val today = LocalDate.now()
        val days = mutableListOf<DayAdherence>()

        for (i in 6 downTo 0) {
            val date = today.minusDays(i.toLong())
            val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)

            val logs = doseLogDao.getForDate(dateStr)
                .filter { it.scheduledTime != "PRN" }

            val total = logs.size
            val taken = logs.count { it.status == "taken" }
            val rate = if (total > 0) (taken.toDouble() / total) * 100.0 else 0.0

            days.add(DayAdherence(date = dateStr, rate = rate))
        }

        return days
    }

    /**
     * Clear all data from the database.
     */
    suspend fun clearAllData() {
        stockEventDao.deleteAll()
        doseLogDao.deleteAll()
        medicationDao.deleteAll()
    }

    /**
     * Export all data as a JSON string.
     */
    suspend fun exportData(): String {
        val meds = medicationDao.getAllOnce()
        val logs = doseLogDao.getAllOnce()
        val events = stockEventDao.getAll()

        val root = org.json.JSONObject()

        // Medications
        val medsArray = org.json.JSONArray()
        for (med in meds) {
            val obj = org.json.JSONObject()
            obj.put("id", med.id)
            obj.put("name", med.name)
            obj.put("dosage", med.dosage)
            obj.put("unit", med.unit)
            obj.put("frequency", med.frequency)
            obj.put("timesPerDay", med.timesPerDay)
            val timesArr = org.json.JSONArray()
            med.scheduledTimes.forEach { timesArr.put(it) }
            obj.put("scheduledTimes", timesArr)
            obj.put("doseIntervalHours", med.doseIntervalHours)
            obj.put("tabletsPerDose", med.tabletsPerDose)
            obj.put("currentStock", med.currentStock)
            obj.put("repeatsRemaining", med.repeatsRemaining)
            obj.put("lowStockThreshold", med.lowStockThreshold)
            obj.put("notes", med.notes)
            obj.put("active", med.active)
            obj.put("createdAt", med.createdAt)
            obj.put("color", med.color)
            medsArray.put(obj)
        }
        root.put("medications", medsArray)

        // Dose Logs
        val logsArray = org.json.JSONArray()
        for (log in logs) {
            val obj = org.json.JSONObject()
            obj.put("id", log.id)
            obj.put("medicationId", log.medicationId)
            obj.put("scheduledDate", log.scheduledDate)
            obj.put("scheduledTime", log.scheduledTime)
            obj.put("status", log.status)
            obj.put("takenAt", log.takenAt ?: org.json.JSONObject.NULL)
            obj.put("createdAt", log.createdAt)
            logsArray.put(obj)
        }
        root.put("doseLogs", logsArray)

        // Stock Events
        val eventsArray = org.json.JSONArray()
        for (event in events) {
            val obj = org.json.JSONObject()
            obj.put("id", event.id)
            obj.put("medicationId", event.medicationId)
            obj.put("type", event.type)
            obj.put("quantity", event.quantity)
            obj.put("note", event.note)
            obj.put("createdAt", event.createdAt)
            eventsArray.put(obj)
        }
        root.put("stockEvents", eventsArray)

        root.put("exportedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))

        return root.toString(2)
    }

    /**
     * Import data from a JSON string. Replaces all existing data.
     */
    suspend fun importData(jsonString: String) {
        val root = org.json.JSONObject(jsonString)

        // Clear existing data
        stockEventDao.deleteAll()
        doseLogDao.deleteAll()
        medicationDao.deleteAll()

        // Import medications
        if (root.has("medications")) {
            val arr = root.getJSONArray("medications")
            val meds = mutableListOf<Medication>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val timesArr = obj.optJSONArray("scheduledTimes")
                val times = if (timesArr != null) {
                    (0 until timesArr.length()).map { timesArr.getString(it) }
                } else {
                    emptyList()
                }
                meds.add(
                    Medication(
                        id = obj.getString("id"),
                        name = obj.optString("name", ""),
                        dosage = obj.optString("dosage", ""),
                        unit = obj.optString("unit", "tablet"),
                        frequency = obj.optString("frequency", "daily"),
                        timesPerDay = obj.optInt("timesPerDay", 1),
                        scheduledTimes = times,
                        doseIntervalHours = obj.optInt("doseIntervalHours", 6).coerceIn(1, 6),
                        tabletsPerDose = obj.optInt("tabletsPerDose", 1),
                        currentStock = obj.optInt("currentStock", 0),
                        repeatsRemaining = obj.optInt("repeatsRemaining", 0),
                        lowStockThreshold = obj.optInt("lowStockThreshold", 10),
                        notes = obj.optString("notes", ""),
                        active = obj.optBoolean("active", true),
                        createdAt = obj.optString("createdAt", ""),
                        color = obj.optString("color", MED_COLORS.first())
                    )
                )
            }
            medicationDao.upsertAll(meds)
        }

        // Import dose logs
        if (root.has("doseLogs")) {
            val arr = root.getJSONArray("doseLogs")
            val logs = mutableListOf<DoseLog>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                logs.add(
                    DoseLog(
                        id = obj.getString("id"),
                        medicationId = obj.getString("medicationId"),
                        scheduledDate = obj.getString("scheduledDate"),
                        scheduledTime = obj.getString("scheduledTime"),
                        status = obj.optString("status", "pending"),
                        takenAt = if (obj.isNull("takenAt")) null else obj.optString("takenAt"),
                        createdAt = obj.optString("createdAt", "")
                    )
                )
            }
            doseLogDao.upsertAll(logs)
        }

        // Import stock events
        if (root.has("stockEvents")) {
            val arr = root.getJSONArray("stockEvents")
            val events = mutableListOf<StockEvent>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                events.add(
                    StockEvent(
                        id = obj.getString("id"),
                        medicationId = obj.getString("medicationId"),
                        type = obj.getString("type"),
                        quantity = obj.optInt("quantity", 0),
                        note = obj.optString("note", ""),
                        createdAt = obj.optString("createdAt", "")
                    )
                )
            }
            stockEventDao.insertAll(events)
        }
    }
}
