package com.mymeds.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mymeds.app.MyMedsApp
import com.mymeds.app.data.model.*
import com.mymeds.app.data.repository.MedsRepository
import com.mymeds.app.notification.DoseAlarmScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MedsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = MedsRepository(
        (application as MyMedsApp).database
    )

    // All medications (reactive via Room Flow)
    val medications: StateFlow<List<Medication>> = repo.getAllMedications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Today's doses — refreshed manually
    private val _todaysDoses = MutableStateFlow<List<DoseLog>>(emptyList())
    val todaysDoses: StateFlow<List<DoseLog>> = _todaysDoses.asStateFlow()

    // UI state
    private val _editingMedId = MutableStateFlow<String?>(null)
    val editingMedId: StateFlow<String?> = _editingMedId.asStateFlow()

    private val _showAddMed = MutableStateFlow(false)
    val showAddMed: StateFlow<Boolean> = _showAddMed.asStateFlow()

    private val _showAddStock = MutableStateFlow<String?>(null)
    val showAddStock: StateFlow<String?> = _showAddStock.asStateFlow()

    // Track the current refresh coroutine so we can cancel stale ones.
    // This prevents two overlapping ensureDoseLogsForDate calls that
    // would race and create duplicate DoseLog rows.
    private var refreshJob: Job? = null

    init {
        refreshDoses()
    }

    fun refreshDoses() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _todaysDoses.value = repo.generateTodaysDoses()
        }
    }

    suspend fun refreshDosesAndWait() {
        refreshJob?.cancel()
        _todaysDoses.value = repo.generateTodaysDoses()
    }

    fun takeDose(log: DoseLog) {
        viewModelScope.launch {
            val med = repo.getMedicationById(log.medicationId) ?: return@launch
            repo.takeDose(log, med)
            refreshDoses()
            rescheduleAlarms()
        }
    }

    fun skipDose(log: DoseLog) {
        viewModelScope.launch {
            repo.skipDose(log)
            refreshDoses()
            rescheduleAlarms()
        }
    }

    fun undoDose(log: DoseLog) {
        viewModelScope.launch {
            val med = repo.getMedicationById(log.medicationId) ?: return@launch
            repo.undoDose(log, med)
            refreshDoses()
            rescheduleAlarms()
        }
    }

    fun updateDoseStatus(log: DoseLog, newStatus: String) {
        viewModelScope.launch {
            val med = repo.getMedicationById(log.medicationId) ?: return@launch
            when (newStatus) {
                "taken" -> {
                    if (log.status == "taken") return@launch
                    // Reset to pending first so takeDose properly decrements stock
                    val resetLog = log.copy(status = "pending", takenAt = null)
                    repo.takeDose(resetLog, med)
                }
                "missed" -> {
                    repo.markMissed(log, med)
                }
            }
            refreshDoses()
            rescheduleAlarms()
        }
    }

    fun saveMedication(med: Medication) {
        viewModelScope.launch {
            repo.upsertMedication(med)
            refreshDoses()
            rescheduleAlarms()
        }
    }

    fun deleteMedication(id: String) {
        viewModelScope.launch {
            repo.deleteMedication(id)
            refreshDoses()
            rescheduleAlarms()
        }
    }

    fun addStock(medId: String, quantity: Int, note: String, useRepeat: Boolean) {
        viewModelScope.launch {
            repo.addStock(medId, quantity, note)
            if (useRepeat) {
                repo.useRepeat(medId)
            }
            refreshDoses()
        }
    }

    fun toggleMedicationActive(med: Medication) {
        viewModelScope.launch {
            repo.upsertMedication(med.copy(active = !med.active))
            refreshDoses()
            rescheduleAlarms()
        }
    }

    // Adherence
    suspend fun getAdherenceStats(startDate: String, endDate: String) =
        repo.getAdherenceStats(startDate, endDate)

    suspend fun getLast7DaysAdherence() = repo.getLast7DaysAdherence()

    suspend fun getDoseLogsForDate(date: String) = repo.getDoseLogsForDate(date)

    suspend fun getMedicationById(id: String) = repo.getMedicationById(id)

    // Data management
    fun clearAllData() {
        viewModelScope.launch {
            repo.clearAllData()
            refreshDoses()
            rescheduleAlarms()
        }
    }

    suspend fun exportData(): String = repo.exportData()

    fun importData(jsonString: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                repo.importData(jsonString)
                refreshDoses()
                rescheduleAlarms()
                onResult(true, "Data imported successfully!")
            } catch (e: Exception) {
                onResult(false, "Import failed: ${e.message}")
            }
        }
    }

    private suspend fun rescheduleAlarms() {
        DoseAlarmScheduler.scheduleDoseAlarms(getApplication())
    }

    // UI state setters
    fun setEditingMedId(id: String?) { _editingMedId.value = id }
    fun setShowAddMed(show: Boolean) { _showAddMed.value = show }
    fun setShowAddStock(medId: String?) { _showAddStock.value = medId }
}
