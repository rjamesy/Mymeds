package com.mymeds.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mymeds.app.data.model.AdherenceStats
import com.mymeds.app.data.model.DoseLog
import com.mymeds.app.data.model.Medication
import com.mymeds.app.ui.theme.*
import com.mymeds.app.ui.viewmodel.MedsViewModel
import com.mymeds.app.util.formatDate
import com.mymeds.app.util.formatTime
import com.mymeds.app.util.getDateNDaysAgo
import com.mymeds.app.util.getTodayStr
import java.text.SimpleDateFormat
import java.util.*

// ── HistoryScreen ────────────────────────────────────────────────────────────

@Composable
fun HistoryScreen(viewModel: MedsViewModel) {

    val medications by viewModel.medications.collectAsState()
    val todayStr = remember { getTodayStr() }

    // 30-day summary stats
    var stats by remember { mutableStateOf<AdherenceStats?>(null) }

    // Calendar state
    var monthOffset by remember { mutableIntStateOf(0) }
    var selectedDate by remember { mutableStateOf(todayStr) }

    // Day detail
    var dayLogs by remember { mutableStateOf<List<DoseLog>>(emptyList()) }
    var dayStats by remember { mutableStateOf<AdherenceStats?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    // Load 30-day stats
    LaunchedEffect(refreshKey) {
        val startDate = getDateNDaysAgo(30)
        val endDate = getTodayStr()
        stats = viewModel.getAdherenceStats(startDate, endDate)
    }

    // Load day detail when selected date changes or after dose update
    LaunchedEffect(selectedDate, refreshKey) {
        dayLogs = viewModel.getDoseLogsForDate(selectedDate)
        dayStats = viewModel.getAdherenceStats(selectedDate, selectedDate)
    }

    // Compute calendar data for current month offset
    val calendarData = remember(monthOffset) { getCalendarData(monthOffset) }

    // Load dose logs for the visible month to power the dot indicators
    var monthDoseLogs by remember { mutableStateOf<Map<String, List<DoseLog>>>(emptyMap()) }

    LaunchedEffect(monthOffset) {
        val (_, _, daysInMonth) = calendarData
        val logMap = mutableMapOf<String, List<DoseLog>>()
        for (dayInfo in daysInMonth) {
            if (dayInfo.dateStr != null) {
                val logs = viewModel.getDoseLogsForDate(dayInfo.dateStr)
                if (logs.isNotEmpty()) {
                    logMap[dayInfo.dateStr] = logs
                }
            }
        }
        monthDoseLogs = logMap
    }

    // Build medication lookup map
    val medMap = remember(medications) { medications.associateBy { it.id } }

    // ── UI ────────────────────────────────────────────────────────────────────

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {

        // ── Header ───────────────────────────────────────────────────────────
        Text(
            text = "History",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // ── 30-Day Summary Stats ─────────────────────────────────────────────
        stats?.let { s ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    label = "Adherence",
                    value = "${s.adherenceRate.toInt()}%",
                    color = Success,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Taken",
                    value = "${s.takenDoses}",
                    color = Primary,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Skipped",
                    value = "${s.skippedDoses}",
                    color = Color(0xFF6B7280),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // ── Calendar ─────────────────────────────────────────────────────────
        CalendarGrid(
            calendarData = calendarData,
            monthOffset = monthOffset,
            selectedDate = selectedDate,
            todayStr = todayStr,
            monthDoseLogs = monthDoseLogs,
            onMonthChange = { newOffset ->
                if (newOffset <= 0) monthOffset = newOffset
            },
            onDateSelected = { date -> selectedDate = date }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Day Detail ───────────────────────────────────────────────────────
        DayDetailPanel(
            selectedDate = selectedDate,
            dayLogs = dayLogs,
            dayStats = dayStats,
            medMap = medMap,
            viewModel = viewModel,
            onDoseUpdated = { refreshKey++ }
        )
    }
}

// ── Stat Card ────────────────────────────────────────────────────────────────

@Composable
private fun StatCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = color.copy(alpha = 0.8f)
            )
        }
    }
}

// ── Calendar Grid ────────────────────────────────────────────────────────────

private data class CalendarDayInfo(
    val dayOfMonth: Int?,       // null for empty padding cells
    val dateStr: String?,       // "yyyy-MM-dd" or null for padding
    val isFuture: Boolean = false
)

private data class CalendarMonthData(
    val monthLabel: String,     // e.g. "January 2025"
    val yearMonth: String,      // e.g. "2025-01"
    val daysInMonth: List<CalendarDayInfo>
)

private fun getCalendarData(monthOffset: Int): CalendarMonthData {
    val cal = Calendar.getInstance()
    cal.add(Calendar.MONTH, monthOffset)
    cal.set(Calendar.DAY_OF_MONTH, 1)

    val monthLabel = SimpleDateFormat("MMMM yyyy", Locale.US).format(cal.time)
    val yearMonth = SimpleDateFormat("yyyy-MM", Locale.US).format(cal.time)

    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH)

    // Day of week for the 1st (Calendar: 1=Sun, 2=Mon, ..., 7=Sat)
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)

    // Convert to Monday-start index (0=Mon, 1=Tue, ..., 6=Sun)
    val startPadding = when (firstDayOfWeek) {
        Calendar.MONDAY -> 0
        Calendar.TUESDAY -> 1
        Calendar.WEDNESDAY -> 2
        Calendar.THURSDAY -> 3
        Calendar.FRIDAY -> 4
        Calendar.SATURDAY -> 5
        Calendar.SUNDAY -> 6
        else -> 0
    }

    val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val todayCal = Calendar.getInstance()
    val todayYear = todayCal.get(Calendar.YEAR)
    val todayMonth = todayCal.get(Calendar.MONTH)
    val todayDay = todayCal.get(Calendar.DAY_OF_MONTH)

    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    val days = mutableListOf<CalendarDayInfo>()

    // Leading padding
    repeat(startPadding) {
        days.add(CalendarDayInfo(dayOfMonth = null, dateStr = null))
    }

    // Actual days
    for (d in 1..maxDay) {
        cal.set(year, month, d)
        val dateStr = sdf.format(cal.time)

        val isFuture = when {
            year > todayYear -> true
            year == todayYear && month > todayMonth -> true
            year == todayYear && month == todayMonth && d > todayDay -> true
            else -> false
        }

        days.add(CalendarDayInfo(dayOfMonth = d, dateStr = dateStr, isFuture = isFuture))
    }

    return CalendarMonthData(
        monthLabel = monthLabel,
        yearMonth = yearMonth,
        daysInMonth = days
    )
}

@Composable
private fun CalendarGrid(
    calendarData: CalendarMonthData,
    monthOffset: Int,
    selectedDate: String,
    todayStr: String,
    monthDoseLogs: Map<String, List<DoseLog>>,
    onMonthChange: (Int) -> Unit,
    onDateSelected: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // Month header with navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onMonthChange(monthOffset - 1) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous month"
                    )
                }
                Text(
                    text = calendarData.monthLabel,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                IconButton(
                    onClick = { onMonthChange(monthOffset + 1) },
                    enabled = monthOffset < 0
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next month",
                        tint = if (monthOffset < 0)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Weekday headers (Monday start)
            val weekDays = listOf("M", "T", "W", "T", "F", "S", "S")
            Row(modifier = Modifier.fillMaxWidth()) {
                weekDays.forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Days grid
            val days = calendarData.daysInMonth
            val rows = (days.size + 6) / 7 // ceiling division

            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val index = row * 7 + col
                        if (index < days.size) {
                            val dayInfo = days[index]
                            CalendarDayCell(
                                dayInfo = dayInfo,
                                isSelected = dayInfo.dateStr == selectedDate,
                                isToday = dayInfo.dateStr == todayStr,
                                doseLogs = dayInfo.dateStr?.let { monthDoseLogs[it] },
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    dayInfo.dateStr?.let { onDateSelected(it) }
                                }
                            )
                        } else {
                            // Trailing empty cell
                            Spacer(modifier = Modifier.weight(1f).height(44.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    dayInfo: CalendarDayInfo,
    isSelected: Boolean,
    isToday: Boolean,
    doseLogs: List<DoseLog>?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (dayInfo.dayOfMonth == null) {
        // Empty padding cell
        Spacer(modifier = modifier.height(44.dp))
        return
    }

    val bgColor = when {
        isSelected -> Primary
        isToday -> PrimaryContainer
        else -> Color.Transparent
    }

    val textColor = when {
        isSelected -> Color.White
        dayInfo.isFuture -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.onSurface
    }

    // Determine dot color based on dose logs
    val dotColor = remember(doseLogs) {
        getDotColor(doseLogs)
    }

    Column(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (!dayInfo.isFuture) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .background(bgColor, RoundedCornerShape(8.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "${dayInfo.dayOfMonth}",
            fontSize = 14.sp,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )

        // Dot indicator
        if (dotColor != null && !dayInfo.isFuture) {
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        if (isSelected) Color.White.copy(alpha = 0.8f) else dotColor,
                        CircleShape
                    )
            )
        }
    }
}

/**
 * Determine the dot color for a calendar day based on its dose logs.
 * - Green: 100% adherence (all taken)
 * - Amber: partial (some taken, some not)
 * - Red: none taken but has logs (missed/skipped/pending)
 * - null: no logs for this day
 */
private fun getDotColor(doseLogs: List<DoseLog>?): Color? {
    if (doseLogs.isNullOrEmpty()) return null

    // Filter out PRN doses for adherence calculation
    val scheduled = doseLogs.filter { it.scheduledTime != "PRN" }
    if (scheduled.isEmpty()) return null

    val taken = scheduled.count { it.status == "taken" }
    val total = scheduled.size

    return when {
        taken == total -> Color(0xFF22C55E)              // Green - all taken
        taken > 0 -> Color(0xFFF59E0B)                   // Amber - partial
        else -> Color(0xFFEF4444)                         // Red - none taken
    }
}

// ── Day Detail Panel ─────────────────────────────────────────────────────────

@Composable
private fun DayDetailPanel(
    selectedDate: String,
    dayLogs: List<DoseLog>,
    dayStats: AdherenceStats?,
    medMap: Map<String, Medication>,
    viewModel: MedsViewModel,
    onDoseUpdated: () -> Unit
) {
    var selectedLog by remember { mutableStateOf<DoseLog?>(null) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Date header with adherence badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDate(selectedDate),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                dayStats?.let { s ->
                    if (s.totalDoses > 0) {
                        val rate = s.adherenceRate.toInt()
                        val badgeColor = when {
                            rate >= 80 -> Success
                            rate >= 50 -> Warning
                            else -> Danger
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = badgeColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "$rate%",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = badgeColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dose logs list or empty state
            val scheduledLogs = dayLogs.filter { it.scheduledTime != "PRN" }

            if (scheduledLogs.isEmpty()) {
                Text(
                    text = "No medication data for ${formatDate(selectedDate)}",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                scheduledLogs.forEach { log ->
                    DoseLogRow(
                        log = log,
                        medication = medMap[log.medicationId],
                        onClick = { selectedLog = log }
                    )
                    if (log != scheduledLogs.last()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                    }
                }
            }
        }
    }

    // Confirmation dialog
    selectedLog?.let { log ->
        val med = medMap[log.medicationId]
        val medName = med?.name ?: "Unknown"

        if (log.status == "taken") {
            AlertDialog(
                onDismissRequest = { selectedLog = null },
                title = { Text(medName) },
                text = { Text("This dose is marked as taken. Change to missed?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.updateDoseStatus(log, "missed")
                        selectedLog = null
                        onDoseUpdated()
                    }) { Text("Mark Missed") }
                },
                dismissButton = {
                    TextButton(onClick = { selectedLog = null }) { Text("Cancel") }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { selectedLog = null },
                title = { Text(medName) },
                text = { Text("Did you take this medication?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.updateDoseStatus(log, "taken")
                        selectedLog = null
                        onDoseUpdated()
                    }) { Text("Yes") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.updateDoseStatus(log, "missed")
                        selectedLog = null
                        onDoseUpdated()
                    }) { Text("No") }
                }
            )
        }
    }
}

@Composable
private fun DoseLogRow(
    log: DoseLog,
    medication: Medication?,
    onClick: () -> Unit = {}
) {
    val statusColor = when (log.status) {
        "taken" -> Success
        "skipped" -> Color(0xFF6B7280)
        "missed" -> Danger
        else -> Warning       // pending
    }

    val statusIcon = when (log.status) {
        "taken" -> Icons.Default.Check
        "skipped", "missed" -> Icons.Default.Close
        else -> Icons.Default.QuestionMark
    }

    val statusLabel = when (log.status) {
        "taken" -> "Taken"
        "skipped" -> "Skipped"
        "missed" -> "Missed"
        else -> "Pending"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(statusColor.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = statusLabel,
                tint = statusColor,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Med name + dosage
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = medication?.name ?: "Unknown",
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
            if (medication != null) {
                Text(
                    text = "${medication.dosage} ${medication.unit}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        // Time + status
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatTime(log.scheduledTime),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = statusLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = statusColor
            )
        }
    }
}
