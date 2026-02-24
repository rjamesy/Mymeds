package com.mymeds.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mymeds.app.data.model.DEFAULT_TIMES
import com.mymeds.app.data.model.FREQUENCY_LABELS
import com.mymeds.app.data.model.FREQUENCY_TIMES
import com.mymeds.app.data.model.MED_COLORS
import com.mymeds.app.data.model.Medication
import com.mymeds.app.util.generateId
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── MedicationFormDialog ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationFormDialog(
    medication: Medication?,
    onSave: (Medication) -> Unit,
    onDelete: ((String) -> Unit)?,
    onDismiss: () -> Unit
) {
    val isEditing = medication != null

    // ── Form State ──────────────────────────────────────────────────────────
    var name by remember { mutableStateOf(medication?.name ?: "") }
    var dosage by remember { mutableStateOf(medication?.dosage ?: "") }
    var unit by remember { mutableStateOf(medication?.unit ?: "tablet") }
    var frequency by remember { mutableStateOf(medication?.frequency ?: "daily") }
    var scheduledTimes by remember {
        mutableStateOf(
            medication?.scheduledTimes
                ?: (DEFAULT_TIMES[frequency] ?: listOf("08:00"))
        )
    }
    var doseIntervalHours by remember {
        mutableStateOf((medication?.doseIntervalHours?.coerceIn(1, 6) ?: 6).toString())
    }
    var tabletsPerDose by remember {
        mutableStateOf((medication?.tabletsPerDose ?: 1).toString())
    }
    var currentStock by remember {
        mutableStateOf((medication?.currentStock ?: 0).toString())
    }
    var repeatsRemaining by remember {
        mutableStateOf((medication?.repeatsRemaining ?: 0).toString())
    }
    var lowStockThreshold by remember {
        mutableStateOf((medication?.lowStockThreshold ?: 10).toString())
    }
    var color by remember { mutableStateOf(medication?.color ?: MED_COLORS.first()) }
    var notes by remember { mutableStateOf(medication?.notes ?: "") }

    // For "every_x_hours" frequency: interval in hours
    var hourInterval by remember {
        mutableStateOf(
            if (medication?.frequency == "every_x_hours" && medication.scheduledTimes.size > 1) {
                // Infer interval from existing times
                val times = medication.scheduledTimes.mapNotNull {
                    val p = it.split(":")
                    if (p.size == 2) p[0].toIntOrNull() else null
                }.sorted()
                if (times.size >= 2) (times[1] - times[0]).toString() else "4"
            } else "4"
        )
    }

    // ── Dropdown expanded states ────────────────────────────────────────────
    var unitExpanded by remember { mutableStateOf(false) }
    var frequencyExpanded by remember { mutableStateOf(false) }
    var doseIntervalExpanded by remember { mutableStateOf(false) }

    // ── Time picker state ───────────────────────────────────────────────────
    var showTimePicker by remember { mutableStateOf(false) }
    var editingTimeIndex by remember { mutableIntStateOf(-1) }

    // ── Delete confirmation ─────────────────────────────────────────────────
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // ── Validation ──────────────────────────────────────────────────────────
    val isNameValid = name.isNotBlank()

    // Unit options
    val unitOptions = listOf("tablet", "capsule", "ml", "drop", "puff", "patch", "injection")

    // When frequency changes (and not editing), reset scheduled times to defaults
    LaunchedEffect(frequency) {
        if (!isEditing) {
            if (frequency == "every_x_hours") {
                // Auto-generate from interval
                val interval = hourInterval.toIntOrNull() ?: 4
                val times = mutableListOf<String>()
                var hour = 8
                while (hour < 24) {
                    times.add("${hour.toString().padStart(2, '0')}:00")
                    hour += interval
                }
                if (times.isEmpty()) times.add("08:00")
                scheduledTimes = times
            } else {
                scheduledTimes = DEFAULT_TIMES[frequency] ?: listOf("08:00")
            }
        }
    }

    // ── Full-screen Dialog ──────────────────────────────────────────────────
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Top Bar ─────────────────────────────────────────────────
                TopAppBar(
                    title = {
                        Text(
                            text = if (isEditing) "Edit Medication" else "Add Medication",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // ── Scrollable Form Content (including buttons at bottom) ──
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Name (required)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Medication Name *") },
                        placeholder = { Text("e.g. Metformin") },
                        singleLine = true,
                        isError = name.isBlank() && name != (medication?.name ?: ""),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // 2. Dosage
                    OutlinedTextField(
                        value = dosage,
                        onValueChange = { dosage = it },
                        label = { Text("Dosage") },
                        placeholder = { Text("e.g. 500mg") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // 3. Unit dropdown
                    ExposedDropdownMenuBox(
                        expanded = unitExpanded,
                        onExpandedChange = { unitExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = unit.replaceFirstChar { it.uppercase() },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Unit") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = unitExpanded,
                            onDismissRequest = { unitExpanded = false }
                        ) {
                            unitOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.replaceFirstChar { it.uppercase() }) },
                                    onClick = {
                                        unit = option
                                        unitExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // 4. Frequency dropdown
                    ExposedDropdownMenuBox(
                        expanded = frequencyExpanded,
                        onExpandedChange = { frequencyExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = FREQUENCY_LABELS[frequency] ?: frequency,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Frequency") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = frequencyExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = frequencyExpanded,
                            onDismissRequest = { frequencyExpanded = false }
                        ) {
                            FREQUENCY_LABELS.forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        frequency = key
                                        frequencyExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // 5a. Hour interval input (only for "every_x_hours")
                    ExposedDropdownMenuBox(
                        expanded = doseIntervalExpanded,
                        onExpandedChange = { doseIntervalExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = "$doseIntervalHours hour${if (doseIntervalHours == "1") "" else "s"}",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Time between doses") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = doseIntervalExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = doseIntervalExpanded,
                            onDismissRequest = { doseIntervalExpanded = false }
                        ) {
                            (1..6).forEach { hours ->
                                DropdownMenuItem(
                                    text = {
                                        Text("$hours hour${if (hours == 1) "" else "s"}")
                                    },
                                    onClick = {
                                        doseIntervalHours = hours.toString()
                                        doseIntervalExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // 5b. Hour interval input (only for "every_x_hours")
                    if (frequency == "every_x_hours") {
                        OutlinedTextField(
                            value = hourInterval,
                            onValueChange = { newVal ->
                                hourInterval = newVal.filter { c -> c.isDigit() }
                                // Auto-generate scheduled times based on interval
                                val interval = hourInterval.toIntOrNull() ?: 4
                                if (interval in 1..24) {
                                    val times = mutableListOf<String>()
                                    var hour = 8 // Start at 8 AM
                                    while (hour < 24) {
                                        times.add("${hour.toString().padStart(2, '0')}:00")
                                        hour += interval
                                    }
                                    if (times.isEmpty()) times.add("08:00")
                                    scheduledTimes = times
                                }
                            },
                            label = { Text("Every how many hours?") },
                            placeholder = { Text("e.g. 4") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            supportingText = {
                                val interval = hourInterval.toIntOrNull() ?: 4
                                val count = if (interval in 1..24) {
                                    var h = 8
                                    var c = 0
                                    while (h < 24) { c++; h += interval }
                                    c
                                } else 0
                                Text("$count dose${if (count != 1) "s" else ""} per day starting at 8:00 AM")
                            }
                        )
                    }

                    // 5c. Scheduled Times (hidden when frequency is "as_needed")
                    if (frequency != "as_needed") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Scheduled Times",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            scheduledTimes.forEachIndexed { index, time ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Time chip - clickable to open time picker
                                    AssistChip(
                                        onClick = {
                                            editingTimeIndex = index
                                            showTimePicker = true
                                        },
                                        label = { Text(formatTime24To12(time)) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.AccessTime,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )

                                    // Remove button (only if more than 1 time)
                                    if (scheduledTimes.size > 1) {
                                        IconButton(
                                            onClick = {
                                                scheduledTimes = scheduledTimes.toMutableList().also {
                                                    it.removeAt(index)
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove time",
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }

                            // Add another time button
                            TextButton(
                                onClick = {
                                    scheduledTimes = scheduledTimes + "12:00"
                                }
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add another time")
                            }
                        }
                    }

                    // 6. Tablets per dose
                    OutlinedTextField(
                        value = tabletsPerDose,
                        onValueChange = { tabletsPerDose = it.filter { c -> c.isDigit() } },
                        label = { Text("Tablets per dose") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // 7. Current Stock
                    OutlinedTextField(
                        value = currentStock,
                        onValueChange = { currentStock = it.filter { c -> c.isDigit() } },
                        label = { Text("Current Stock") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // 8. Repeats Remaining
                    OutlinedTextField(
                        value = repeatsRemaining,
                        onValueChange = { repeatsRemaining = it.filter { c -> c.isDigit() } },
                        label = { Text("Repeats Remaining") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // 9. Low Stock Warning (days)
                    OutlinedTextField(
                        value = lowStockThreshold,
                        onValueChange = { lowStockThreshold = it.filter { c -> c.isDigit() } },
                        label = { Text("Low Stock Warning (days)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // 10. Color Picker
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Color",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Two rows of 8 colors
                        for (rowStart in MED_COLORS.indices step 8) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                for (i in rowStart until minOf(rowStart + 8, MED_COLORS.size)) {
                                    val colorHex = MED_COLORS[i]
                                    val isSelected = color == colorHex
                                    val parsedColor = parseHexColor(colorHex)
                                    val isWhite = colorHex == "#ffffff" || colorHex == "#FFFFFF"

                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(parsedColor, CircleShape)
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected)
                                                    MaterialTheme.colorScheme.onBackground
                                                else
                                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                                shape = CircleShape
                                            )
                                            .clickable { color = colorHex },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = if (isWhite) Color.Black else Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 11. Notes
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes") },
                        placeholder = { Text("Optional notes...") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // ── Divider before buttons ──────────────────────────────
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // ── Delete button (only when editing) ───────────────────
                    if (isEditing && onDelete != null) {
                        Button(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Delete Medication")
                        }
                    }

                    // ── Cancel + Save buttons ───────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                val now = SimpleDateFormat(
                                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                                    Locale.US
                                ).format(Date())

                                val timesPerDay = when (frequency) {
                                    "as_needed" -> 0
                                    "every_x_hours" -> scheduledTimes.size
                                    "every_other_day" -> scheduledTimes.size
                                    else -> FREQUENCY_TIMES[frequency] ?: scheduledTimes.size
                                }

                                val finalTimes = if (frequency == "as_needed") {
                                    emptyList()
                                } else {
                                    scheduledTimes
                                }

                                val med = Medication(
                                    id = medication?.id ?: generateId(),
                                    name = name.trim(),
                                    dosage = dosage.trim(),
                                    unit = unit,
                                    frequency = frequency,
                                    timesPerDay = timesPerDay,
                                    scheduledTimes = finalTimes,
                                    doseIntervalHours = doseIntervalHours.toIntOrNull()
                                        ?.coerceIn(1, 6) ?: 6,
                                    tabletsPerDose = tabletsPerDose.toIntOrNull() ?: 1,
                                    currentStock = currentStock.toIntOrNull() ?: 0,
                                    repeatsRemaining = repeatsRemaining.toIntOrNull() ?: 0,
                                    lowStockThreshold = lowStockThreshold.toIntOrNull() ?: 10,
                                    notes = notes.trim(),
                                    active = medication?.active ?: true,
                                    createdAt = medication?.createdAt ?: now,
                                    color = color
                                )

                                onSave(med)
                            },
                            modifier = Modifier.weight(1f),
                            enabled = isNameValid,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (isEditing) "Save" else "Add Medication")
                        }
                    }

                    // Extra space at the very bottom for navigation bar clearance
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
    }

    // ── Time Picker Dialog ──────────────────────────────────────────────────
    if (showTimePicker && editingTimeIndex >= 0 && editingTimeIndex < scheduledTimes.size) {
        val currentTime = scheduledTimes[editingTimeIndex]
        val parts = currentTime.split(":")
        val initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val timePickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = false
        )

        AlertDialog(
            onDismissRequest = {
                showTimePicker = false
                editingTimeIndex = -1
            },
            title = { Text("Select Time") },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val h = timePickerState.hour.toString().padStart(2, '0')
                        val m = timePickerState.minute.toString().padStart(2, '0')
                        val newTime = "$h:$m"
                        scheduledTimes = scheduledTimes.toMutableList().also {
                            it[editingTimeIndex] = newTime
                        }
                        showTimePicker = false
                        editingTimeIndex = -1
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showTimePicker = false
                        editingTimeIndex = -1
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Delete Confirmation Dialog ──────────────────────────────────────────
    if (showDeleteConfirm && isEditing) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Medication?") },
            text = {
                Text(
                    "This will permanently delete \"${medication!!.name}\" and all its associated dose history. This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete?.invoke(medication!!.id)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────

/**
 * Format "08:00" to "8:00 AM" for display in the time chips.
 */
private fun formatTime24To12(time24: String): String {
    val parts = time24.split(":")
    if (parts.size != 2) return time24
    val h = parts[0].toIntOrNull() ?: return time24
    val m = parts[1].toIntOrNull() ?: return time24
    val ampm = if (h >= 12) "PM" else "AM"
    val hour = if (h % 12 == 0) 12 else h % 12
    return "$hour:${m.toString().padStart(2, '0')} $ampm"
}

/**
 * Parse a hex color string (e.g. "#4f46e5") to a Compose Color.
 */
private fun parseHexColor(hex: String): Color {
    return try {
        val cleaned = hex.removePrefix("#")
        val colorLong = cleaned.toLong(16)
        Color(0xFF000000 or colorLong)
    } catch (_: Exception) {
        Color(0xFF4F46E5) // fallback to primary indigo
    }
}
