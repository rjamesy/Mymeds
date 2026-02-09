package com.mymeds.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mymeds.app.data.model.FREQUENCY_LABELS
import com.mymeds.app.data.model.Medication
import com.mymeds.app.notification.DoseAlarmScheduler
import com.mymeds.app.notification.OverdueDoseWorker
import java.util.concurrent.TimeUnit
import com.mymeds.app.ui.theme.Danger
import com.mymeds.app.ui.theme.Warning
import com.mymeds.app.ui.viewmodel.MedsViewModel
import com.mymeds.app.util.formatTime
import com.mymeds.app.util.getDaysSupply
import com.mymeds.app.util.getStockStatus
import kotlinx.coroutines.launch
import java.time.LocalDate

// ── SettingsScreen ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MedsViewModel,
    onNavigateToDashboard: () -> Unit
) {
    val medications by viewModel.medications.collectAsState()
    val context = LocalContext.current

    val activeMeds = medications.filter { it.active }
    val inactiveMeds = medications.filter { !it.active }

    // Clear-all-data confirmation dialogs (two-step)
    var showClearConfirm1 by remember { mutableStateOf(false) }
    var showClearConfirm2 by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    // Pending export data to be written once a file URI is obtained
    var pendingExportJson by remember { mutableStateOf<String?>(null) }

    // Export: Create a new document and write JSON to it
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && pendingExportJson != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(pendingExportJson!!.toByteArray())
                }
                Toast.makeText(context, "Backup exported successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            pendingExportJson = null
        }
    }

    // Import: Open a JSON file and read it
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().readText()
                }
                if (jsonString != null) {
                    viewModel.importData(jsonString) { success, message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Notification preference ────────────────────────────────────────────
    val prefs = context.getSharedPreferences("mymeds_prefs", Context.MODE_PRIVATE)
    var notificationsEnabled by remember {
        mutableStateOf(prefs.getBoolean("notifications_enabled", true))
    }

    // Permission launcher for Android 13+ POST_NOTIFICATIONS
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            notificationsEnabled = true
            prefs.edit().putBoolean("notifications_enabled", true).apply()
            coroutineScope.launch {
                DoseAlarmScheduler.scheduleDoseAlarms(context)
            }
            startOverdueWorker(context)
        } else {
            notificationsEnabled = false
            prefs.edit().putBoolean("notifications_enabled", false).apply()
            Toast.makeText(
                context,
                "Notification permission denied. Enable it in system settings.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Header Row ───────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Button(
                    onClick = { viewModel.setShowAddMed(true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add medication",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Med")
                }
            }
        }

        // ── Notifications Toggle ─────────────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Dose Reminders",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Get notified when doses are due",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                // Check if POST_NOTIFICATIONS permission needed (Android 13+)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED

                                    if (hasPermission) {
                                        notificationsEnabled = true
                                        prefs.edit().putBoolean("notifications_enabled", true).apply()
                                        coroutineScope.launch {
                                            DoseAlarmScheduler.scheduleDoseAlarms(context)
                                        }
                                        startOverdueWorker(context)
                                    } else {
                                        notificationPermissionLauncher.launch(
                                            Manifest.permission.POST_NOTIFICATIONS
                                        )
                                    }
                                } else {
                                    notificationsEnabled = true
                                    prefs.edit().putBoolean("notifications_enabled", true).apply()
                                    coroutineScope.launch {
                                        DoseAlarmScheduler.scheduleDoseAlarms(context)
                                    }
                                    startOverdueWorker(context)
                                }
                            } else {
                                notificationsEnabled = false
                                prefs.edit().putBoolean("notifications_enabled", false).apply()
                                DoseAlarmScheduler.cancelAllAlarms(context)
                                WorkManager.getInstance(context)
                                    .cancelUniqueWork(OverdueDoseWorker.WORK_NAME)
                            }
                        }
                    )
                }
            }
        }

        // ── Active Medications Section ───────────────────────────────────────
        item {
            Text(
                text = "ACTIVE MEDICATIONS (${activeMeds.size})",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        if (activeMeds.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "No active medications. Tap \"+ Add Med\" to get started.",
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        items(activeMeds, key = { it.id }) { med ->
            MedCard(
                med = med,
                isActive = true,
                onRefill = { viewModel.setShowAddStock(med.id) },
                onEdit = { viewModel.setEditingMedId(med.id) },
                onToggleActive = { viewModel.toggleMedicationActive(med) }
            )
        }

        // ── Inactive Medications Section ─────────────────────────────────────
        if (inactiveMeds.isNotEmpty()) {
            item {
                Text(
                    text = "INACTIVE (${inactiveMeds.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }

            items(inactiveMeds, key = { it.id }) { med ->
                MedCard(
                    med = med,
                    isActive = false,
                    onRefill = { viewModel.setShowAddStock(med.id) },
                    onEdit = { viewModel.setEditingMedId(med.id) },
                    onToggleActive = { viewModel.toggleMedicationActive(med) }
                )
            }
        }

        // ── Data Management Card ─────────────────────────────────────────────
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Data",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Export
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    pendingExportJson = viewModel.exportData()
                                    val fileName = "mymeds-backup-${LocalDate.now()}.json"
                                    exportLauncher.launch(fileName)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Export Backup")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Import
                    OutlinedButton(
                        onClick = {
                            importLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Import Backup")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Clear All Data
                    Button(
                        onClick = { showClearConfirm1 = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Danger,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Clear All Data")
                    }
                }
            }
        }

        // ── Footer ───────────────────────────────────────────────────────────
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "MyMeds v1.4 \u00B7 Data stored locally on your device",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // ── Clear Data Confirmation Dialogs ──────────────────────────────────────

    // First confirmation
    if (showClearConfirm1) {
        AlertDialog(
            onDismissRequest = { showClearConfirm1 = false },
            title = { Text("Clear All Data?") },
            text = {
                Text("This will permanently delete all medications, dose history, and stock records. This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm1 = false
                        showClearConfirm2 = true
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Danger)
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm1 = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Second confirmation
    if (showClearConfirm2) {
        AlertDialog(
            onDismissRequest = { showClearConfirm2 = false },
            title = { Text("Are you absolutely sure?") },
            text = {
                Text("All your medication data will be permanently deleted. There is no way to recover it.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm2 = false
                        viewModel.clearAllData()
                        Toast.makeText(context, "All data cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Danger)
                ) {
                    Text("Yes, Delete Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm2 = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ── Helper: start the periodic overdue dose check worker ─────────────────────

private fun startOverdueWorker(context: Context) {
    val workRequest = PeriodicWorkRequestBuilder<OverdueDoseWorker>(
        15, TimeUnit.MINUTES
    ).build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        OverdueDoseWorker.WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        workRequest
    )
}

// ── MedCard Composable ───────────────────────────────────────────────────────

@Composable
private fun MedCard(
    med: Medication,
    isActive: Boolean,
    onRefill: () -> Unit,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit
) {
    val alphaModifier = if (isActive) 1f else 0.55f
    val stockStatus = getStockStatus(med)
    val daysSupply = getDaysSupply(med)
    val medColor = try {
        Color(android.graphics.Color.parseColor(med.color))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }

    // Two-letter abbreviation from the medication name
    val abbreviation = med.name
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifEmpty { "??" }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alphaModifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── Top row: color dot + abbreviation, name, dosage, frequency ───
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Color dot with abbreviation
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(medColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = abbreviation,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = med.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (med.dosage.isNotBlank()) {
                        Text(
                            text = "${med.dosage} \u00B7 ${FREQUENCY_LABELS[med.frequency] ?: med.frequency}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = FREQUENCY_LABELS[med.frequency] ?: med.frequency,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── Scheduled times + tablets per dose ───────────────────────────
            if (med.scheduledTimes.isNotEmpty()) {
                val timesText = med.scheduledTimes.joinToString(", ") { formatTime(it) }
                val dosageInfo = if (med.tabletsPerDose > 1) {
                    "$timesText \u00B7 ${med.tabletsPerDose} ${med.unit}s per dose"
                } else {
                    "$timesText \u00B7 ${med.tabletsPerDose} ${med.unit} per dose"
                }
                Text(
                    text = dosageInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // ── Stock info bar ───────────────────────────────────────────────
            val stockBgColor = when (stockStatus) {
                "empty", "critical" -> Danger.copy(alpha = 0.12f)
                "low" -> Warning.copy(alpha = 0.12f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            }
            val stockTextColor = when (stockStatus) {
                "empty", "critical" -> Danger
                "low" -> Warning
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(stockBgColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val supplyText = if (daysSupply == Int.MAX_VALUE) {
                    "--"
                } else {
                    "$daysSupply day${if (daysSupply != 1) "s" else ""}"
                }

                Text(
                    text = "${med.currentStock} ${med.unit}${if (med.currentStock != 1) "s" else ""} \u00B7 $supplyText supply \u00B7 ${med.repeatsRemaining} repeat${if (med.repeatsRemaining != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = stockTextColor,
                    modifier = Modifier.weight(1f)
                )

                // Refill button
                FilledIconButton(
                    onClick = onRefill,
                    modifier = Modifier.size(28.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add stock",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Action buttons: Edit + Deactivate/Activate ───────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Edit")
                }

                if (isActive) {
                    OutlinedButton(
                        onClick = onToggleActive,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Warning
                        )
                    ) {
                        Text("Deactivate")
                    }
                } else {
                    Button(
                        onClick = onToggleActive,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Activate")
                    }
                }
            }
        }
    }
}
