package com.mymeds.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mymeds.app.data.model.Medication

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddStockDialog(
    medication: Medication,
    onConfirm: (quantity: Int, note: String, useRepeat: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var quantityText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var useRepeat by remember { mutableStateOf(false) }

    val quickSelectOptions = listOf(14, 28, 30, 56, 60, 90)
    val selectedQuantity = quantityText.toIntOrNull()

    val indigo = Color(0xFF6366F1)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Refill ${medication.name}")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Info card showing current stock and repeats
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Current stock: ${medication.currentStock}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Repeats remaining: ${medication.repeatsRemaining}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Quantity input
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { value ->
                        if (value.isEmpty() || value.all { it.isDigit() }) {
                            quantityText = value
                        }
                    },
                    label = { Text("Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick-select buttons
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickSelectOptions.forEach { qty ->
                        val isSelected = selectedQuantity == qty
                        FilterChip(
                            selected = isSelected,
                            onClick = { quantityText = qty.toString() },
                            label = { Text(qty.toString()) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = indigo.copy(alpha = 0.15f),
                                selectedLabelColor = indigo
                            ),
                            border = if (isSelected) {
                                FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = true,
                                    borderColor = indigo,
                                    selectedBorderColor = indigo
                                )
                            } else {
                                FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = false
                                )
                            }
                        )
                    }
                }

                // Use repeat toggle (only if repeats available)
                if (medication.repeatsRemaining > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Use 1 prescription repeat (${medication.repeatsRemaining} left)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = useRepeat,
                            onCheckedChange = { useRepeat = it }
                        )
                    }
                }

                // Note field
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = quantityText.toIntOrNull() ?: 0
                    if (qty > 0) {
                        onConfirm(qty, note, useRepeat)
                    }
                },
                enabled = (quantityText.toIntOrNull() ?: 0) > 0
            ) {
                Text("Add Stock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
