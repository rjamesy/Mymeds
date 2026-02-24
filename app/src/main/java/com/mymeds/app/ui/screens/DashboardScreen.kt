package com.mymeds.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mymeds.app.data.model.DayAdherence
import com.mymeds.app.data.model.DoseLog
import com.mymeds.app.data.model.InteractionWarning
import com.mymeds.app.data.model.Medication
import com.mymeds.app.data.model.checkInteractions
import android.content.Context
import com.mymeds.app.ui.theme.*
import com.mymeds.app.ui.viewmodel.MedsViewModel
import com.mymeds.app.util.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// ── Main Dashboard Screen ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: MedsViewModel) {
    val medications by viewModel.medications.collectAsState()
    val todaysDoses by viewModel.todaysDoses.collectAsState()

    val activeMeds = remember(medications) { medications.filter { it.active } }

    // If no active medications, show empty state
    if (activeMeds.isEmpty()) {
        EmptyState(onAddMedication = { viewModel.setShowAddMed(true) })
        return
    }

    // Build a lookup map: medicationId -> Medication
    val medMap = remember(activeMeds) { activeMeds.associateBy { it.id } }

    // Sort toggle state: persisted in SharedPreferences
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("mymeds_prefs", Context.MODE_PRIVATE) }
    var sortByStatus by remember { mutableStateOf(prefs.getBoolean("sort_by_status", false)) }

    // Separate scheduled vs PRN doses, sorted by time then name
    val scheduledDoses = remember(todaysDoses, medMap, sortByStatus) {
        val sorted = todaysDoses.filter { it.scheduledTime != "PRN" }
            .sortedWith(compareBy<DoseLog> { it.scheduledTime }
                .thenBy { medMap[it.medicationId]?.name?.lowercase() ?: "" })
        if (sortByStatus) {
            // Not taken (pending) first, then taken/skipped
            sorted.sortedBy { if (it.status == "pending") 0 else 1 }
        } else {
            sorted
        }
    }
    val prnDoses = remember(todaysDoses, medMap) {
        todaysDoses.filter { it.scheduledTime == "PRN" }
            .sortedBy { medMap[it.medicationId]?.name?.lowercase() ?: "" }
    }

    // Progress: taken / total scheduled (excluding PRN)
    val takenCount = remember(scheduledDoses) {
        scheduledDoses.count { it.status == "taken" }
    }
    val totalScheduled = scheduledDoses.size

    // Stock warnings — recalculate from latest medications data (not cached)
    val stockWarnings = remember(medications) {
        medications.filter { it.active }.filter { med ->
            val status = getStockStatus(med)
            status == "critical" || status == "empty" || status == "low"
        }.sortedBy { it.name.lowercase() }
    }

    // Medication interaction warnings
    val interactionWarnings = remember(activeMeds) {
        checkInteractions(activeMeds.map { it.name })
    }

    // Pull to refresh
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            scope.launch {
                viewModel.refreshDosesAndWait()
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header with progress ring
            item {
                DashboardHeader(
                    taken = takenCount,
                    total = totalScheduled
                )
            }

            // 2. Stock warning banners
            if (stockWarnings.isNotEmpty()) {
                items(stockWarnings, key = { "stock-${it.id}" }) { med ->
                    StockWarningBanner(
                        medication = med,
                        onRefill = { viewModel.setShowAddStock(med.id) }
                    )
                }
            }

            // 3. Interaction warnings
            if (interactionWarnings.isNotEmpty()) {
                items(interactionWarnings, key = { "interaction-${it.drug1}-${it.drug2}" }) { warning ->
                    InteractionWarningBanner(warning = warning)
                }
            }

            // 4. 7-Day Adherence chart
            item {
                AdherenceChart(
                    viewModel = viewModel,
                    refreshKey = scheduledDoses.map { "${it.id}:${it.status}:${it.scheduledTime}:${it.takenAt}" }
                        .joinToString("|")
                )
            }

            // 5. Scheduled Doses Section
            if (scheduledDoses.isNotEmpty()) {
                item {
                    ScheduleSectionHeader(
                        sortByStatus = sortByStatus,
                        onToggleSort = {
                            sortByStatus = !sortByStatus
                            prefs.edit().putBoolean("sort_by_status", sortByStatus).apply()
                        }
                    )
                }
                items(scheduledDoses, key = { "dose-${it.id}" }) { dose ->
                    val med = medMap[dose.medicationId]
                    if (med != null) {
                        DoseCard(
                            dose = dose,
                            medication = med,
                            isPrn = false,
                            onTake = { viewModel.takeDose(dose) },
                            onSkip = { viewModel.skipDose(dose) },
                            onUndo = { viewModel.undoDose(dose) }
                        )
                    }
                }
            }

            // 6. As Needed Section
            if (prnDoses.isNotEmpty()) {
                item {
                    SectionHeader(title = "AS NEEDED")
                }
                items(prnDoses, key = { "prn-${it.id}" }) { dose ->
                    val med = medMap[dose.medicationId]
                    if (med != null) {
                        DoseCard(
                            dose = dose,
                            medication = med,
                            isPrn = true,
                            onTake = { viewModel.takeDose(dose) },
                            onSkip = { viewModel.skipDose(dose) },
                            onUndo = { viewModel.undoDose(dose) }
                        )
                    }
                }
            }

            // Bottom spacer
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

// ── Header with circular progress ────────────────────────────────────────────

@Composable
private fun DashboardHeader(taken: Int, total: Int) {
    val todayFormatted = formatDate(getTodayStr())
    val progress = if (total > 0) taken.toFloat() / total.toFloat() else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Today",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = todayFormatted,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // Circular progress indicator
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(72.dp)
            ) {
                val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                val progressColor = if (taken == total && total > 0) Success else Primary

                Canvas(modifier = Modifier.size(72.dp)) {
                    val strokeWidth = 6.dp.toPx()
                    val radius = (size.minDimension - strokeWidth) / 2f
                    val topLeft = Offset(
                        (size.width - radius * 2) / 2f,
                        (size.height - radius * 2) / 2f
                    )
                    val arcSize = Size(radius * 2, radius * 2)

                    // Background track
                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Progress arc
                    if (progress > 0f) {
                        drawArc(
                            color = progressColor,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                // Center text
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$taken/$total",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "doses",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

// ── Stock Warning Banner ─────────────────────────────────────────────────────

@Composable
private fun StockWarningBanner(medication: Medication, onRefill: () -> Unit) {
    val status = getStockStatus(medication)
    val daysLeft = getDaysSupply(medication)

    val backgroundColor = when (status) {
        "critical", "empty" -> Color(0xFFFEE2E2) // red-100
        "low" -> Color(0xFFFEF3C7)               // amber-100
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when (status) {
        "critical", "empty" -> Color(0xFFFCA5A5) // red-300
        "low" -> Color(0xFFFCD34D)               // amber-300
        else -> Color.Transparent
    }
    val textColor = when (status) {
        "critical", "empty" -> Color(0xFF991B1B) // red-800
        "low" -> Color(0xFF92400E)               // amber-800
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = medication.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append("${medication.currentStock} ${medication.unit}s remaining")
                        if (status != "empty") {
                            append(" \u2022 $daysLeft day${if (daysLeft != 1) "s" else ""} supply")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            FilledTonalButton(
                onClick = onRefill,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = textColor.copy(alpha = 0.15f),
                    contentColor = textColor
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Refill", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ── Interaction Warning Banner ────────────────────────────────────────────────

@Composable
private fun InteractionWarningBanner(warning: InteractionWarning) {
    val backgroundColor = when (warning.severity) {
        "high" -> Color(0xFFFEE2E2)    // red-100
        "moderate" -> Color(0xFFFEF3C7) // amber-100
        else -> Color(0xFFEFF6FF)       // blue-100
    }
    val borderColor = when (warning.severity) {
        "high" -> Color(0xFFFCA5A5)
        "moderate" -> Color(0xFFFCD34D)
        else -> Color(0xFF93C5FD)
    }
    val textColor = when (warning.severity) {
        "high" -> Color(0xFF991B1B)
        "moderate" -> Color(0xFF92400E)
        else -> Color(0xFF1E40AF)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = "\u26A0 Interaction: ${warning.drug1} + ${warning.drug2}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = warning.description,
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.85f)
            )
        }
    }
}

// ── 7-Day Adherence Chart ────────────────────────────────────────────────────

@Composable
private fun AdherenceChart(
    viewModel: MedsViewModel,
    refreshKey: String
) {
    var adherenceData by remember { mutableStateOf<List<DayAdherence>>(emptyList()) }

    LaunchedEffect(refreshKey) {
        adherenceData = viewModel.getLast7DaysAdherence()
    }

    if (adherenceData.isEmpty()) return

    val todayStr = getTodayStr()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "7-Day Adherence",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Bar chart area (fixed height for bars only)
            val maxBarHeight = 80.dp

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                adherenceData.forEach { day ->
                    val isToday = day.date == todayStr
                    val barColor = when {
                        isToday -> Primary                  // indigo
                        day.rate >= 80.0 -> Success         // green
                        day.rate >= 50.0 -> Warning         // amber
                        day.rate > 0.0 -> Danger            // red
                        else -> Color(0xFFE5E7EB)           // gray-200
                    }

                    val barHeight = if (day.rate > 0) {
                        maxBarHeight * (day.rate / 100.0).toFloat().coerceAtLeast(0.1f)
                    } else {
                        8.dp // Visible placeholder for 0%
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Rate label
                        if (day.rate > 0) {
                            Text(
                                text = "${day.rate.toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        } else {
                            // Reserve space so bars align at bottom
                            Spacer(modifier = Modifier.height(18.dp))
                        }

                        // Bar
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(barColor)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Day labels row (separate from bars to ensure alignment)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                adherenceData.forEach { day ->
                    val isToday = day.date == todayStr
                    val dayLabel = getDayLabel(day.date)

                    Text(
                        text = dayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) Primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

/**
 * Get short day-of-week label from an ISO date string (e.g. "Mon", "Tue").
 */
private fun getDayLabel(isoDate: String): String {
    return try {
        val date = LocalDate.parse(isoDate)
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    } catch (_: Exception) {
        ""
    }
}

// ── Schedule Section Header with Sort Toggle ────────────────────────────────

@Composable
private fun ScheduleSectionHeader(
    sortByStatus: Boolean,
    onToggleSort: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "SCHEDULE",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )

        TextButton(
            onClick = onToggleSort,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Icon(
                Icons.Default.SwapVert,
                contentDescription = "Sort",
                modifier = Modifier.size(16.dp),
                tint = Primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (sortByStatus) "Not Taken" else "By Time",
                style = MaterialTheme.typography.labelSmall,
                color = Primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ── Section Header ───────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
    )
}

// ── Dose Card ────────────────────────────────────────────────────────────────

@Composable
private fun DoseCard(
    dose: DoseLog,
    medication: Medication,
    isPrn: Boolean,
    onTake: () -> Unit,
    onSkip: () -> Unit,
    onUndo: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isOverdue = dose.status == "pending" && !isPrn && isTimeOverdue(dose.scheduledTime)
    val isOutOfStock = medication.currentStock <= 0

    val cardBackground = when {
        dose.status == "taken" -> Color(0xFFF0FDF4)    // green-50
        dose.status == "skipped" -> Color(0xFFF9FAFB).copy(alpha = 0.8f) // gray-50
        isOverdue -> Color(0xFFFEF2F2)                 // red-50
        else -> MaterialTheme.colorScheme.surface
    }

    val medColor = parseHexColor(medication.color)
    val abbreviation = medication.name.take(2).uppercase()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color dot with abbreviation
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(medColor)
            ) {
                Text(
                    text = abbreviation,
                    color = if (medication.color == "#ffffff" || medication.color == "#FFFFFF")
                        Color.Black else Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Medication info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = medication.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${medication.dosage} ${medication.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Time
                    Text(
                        text = if (isPrn) "As needed" else formatTime(dose.scheduledTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )

                    // Overdue label
                    if (isOverdue) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Overdue",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Danger
                        )
                    }

                    // Stock remaining
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isOutOfStock) "Out of stock" else "${medication.currentStock} left",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOutOfStock) Danger.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }

                // Taken timestamp
                if (dose.status == "taken" && dose.takenAt != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Taken at ${formatTakenAtTime(dose.takenAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = Success.copy(alpha = 0.8f)
                    )
                }

                // Notes (if present)
                if (medication.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = medication.notes,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action buttons
            when (dose.status) {
                "pending" -> {
                    if (isOutOfStock) {
                        // Out of stock — disable take button
                        Text(
                            text = "No Stock",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Danger.copy(alpha = 0.6f)
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Take button (green checkmark) with haptic feedback
                            FilledIconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onTake()
                                },
                                modifier = Modifier.size(36.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Success,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Take dose",
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Skip button (gray X) -- not shown for PRN, with haptic feedback
                            if (!isPrn) {
                                FilledIconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onSkip()
                                    },
                                    modifier = Modifier.size(36.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = Color(0xFF9CA3AF), // gray-400
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Skip dose",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                "taken", "skipped" -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (dose.status == "taken") "Taken" else "Skipped",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (dose.status == "taken") Success
                            else Color(0xFF6B7280) // gray-500
                        )
                        TextButton(
                            onClick = onUndo,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = "Undo",
                                style = MaterialTheme.typography.labelSmall,
                                color = Primary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Format an ISO timestamp (e.g. "2025-01-15T08:12:30.123") to "8:12 AM".
 */
private fun formatTakenAtTime(isoTimestamp: String): String {
    return try {
        val dt = LocalDateTime.parse(isoTimestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val h = dt.hour
        val m = dt.minute
        val ampm = if (h >= 12) "PM" else "AM"
        val hour = if (h % 12 == 0) 12 else h % 12
        "$hour:${m.toString().padStart(2, '0')} $ampm"
    } catch (_: Exception) {
        isoTimestamp
    }
}

// ── Empty State ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(onAddMedication: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Medication,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No medications yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Add your first medication to start tracking doses.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 48.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onAddMedication,
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add First Medication")
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Parse a hex color string (e.g. "#4f46e5") to a Compose Color.
 * Falls back to Primary if parsing fails.
 */
private fun parseHexColor(hex: String): Color {
    return try {
        val cleaned = hex.removePrefix("#")
        val colorLong = cleaned.toLong(16)
        Color(0xFF000000 or colorLong)
    } catch (_: Exception) {
        Primary
    }
}
