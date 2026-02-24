package com.mymeds.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mymeds.app.data.model.Medication
import com.mymeds.app.notification.DoseAlarmScheduler
import com.mymeds.app.ui.components.AddStockDialog
import com.mymeds.app.ui.components.MedicationFormDialog
import com.mymeds.app.ui.navigation.AppNavigation
import com.mymeds.app.ui.theme.MyMedsTheme
import com.mymeds.app.ui.theme.ThemeState
import com.mymeds.app.ui.viewmodel.MedsViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Load saved theme preference before setContent
        ThemeState.loadFromPrefs(this)
        enableEdgeToEdge()
        setContent {
            MyMedsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: MedsViewModel = viewModel()
                    RequestNotificationPermission()
                    MainContent(viewModel)
                }
            }
        }
    }
}

@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            android.util.Log.d("MyMeds", "POST_NOTIFICATIONS permission granted")
            scope.launch { DoseAlarmScheduler.scheduleDoseAlarms(context) }
        } else {
            android.util.Log.d("MyMeds", "POST_NOTIFICATIONS permission denied")
        }
    }

    LaunchedEffect(Unit) {
        val already = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!already) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            android.util.Log.d("MyMeds", "POST_NOTIFICATIONS permission already granted")
        }
    }
}

@Composable
private fun MainContent(viewModel: MedsViewModel) {
    val medications by viewModel.medications.collectAsState()
    val showAddMed by viewModel.showAddMed.collectAsState()
    val editingMedId by viewModel.editingMedId.collectAsState()
    val showAddStock by viewModel.showAddStock.collectAsState()

    AppNavigation(viewModel)

    // Add medication dialog
    if (showAddMed) {
        MedicationFormDialog(
            medication = null,
            onSave = { med ->
                viewModel.saveMedication(med)
                viewModel.setShowAddMed(false)
            },
            onDelete = null,
            onDismiss = { viewModel.setShowAddMed(false) }
        )
    }

    // Edit medication dialog
    editingMedId?.let { medId ->
        val med = medications.find { it.id == medId }
        if (med != null) {
            MedicationFormDialog(
                medication = med,
                onSave = { updated ->
                    viewModel.saveMedication(updated)
                    viewModel.setEditingMedId(null)
                },
                onDelete = { id ->
                    viewModel.deleteMedication(id)
                    viewModel.setEditingMedId(null)
                },
                onDismiss = { viewModel.setEditingMedId(null) }
            )
        }
    }

    // Add stock dialog
    showAddStock?.let { medId ->
        val med = medications.find { it.id == medId }
        if (med != null) {
            AddStockDialog(
                medication = med,
                onConfirm = { qty, note, useRepeat ->
                    viewModel.addStock(medId, qty, note, useRepeat)
                    viewModel.setShowAddStock(null)
                },
                onDismiss = { viewModel.setShowAddStock(null) }
            )
        }
    }
}
