package com.mymeds.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mymeds.app.data.model.Medication
import com.mymeds.app.ui.components.AddStockDialog
import com.mymeds.app.ui.components.MedicationFormDialog
import com.mymeds.app.ui.navigation.AppNavigation
import com.mymeds.app.ui.theme.MyMedsTheme
import com.mymeds.app.ui.viewmodel.MedsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyMedsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: MedsViewModel = viewModel()
                    MainContent(viewModel)
                }
            }
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
