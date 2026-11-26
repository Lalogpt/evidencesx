package com.codemaster.evidencex.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codemaster.evidencex.data.local.entity.CaseEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCaseDialog(
    case: CaseEntity,
    onDismiss: () -> Unit,
    onUpdate: (title: String, description: String, status: String) -> Unit
) {
    var title by remember { mutableStateOf(case.title) }
    var description by remember { mutableStateOf(case.description) }
    var status by remember { mutableStateOf(case.status) }
    val validStatuses = listOf("ACTIVE", "CLOSED", "ARCHIVED")
    var expandedStatusMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Expediente (Solo Administrador)") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título del Expediente") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = expandedStatusMenu,
                    onExpandedChange = { expandedStatusMenu = !expandedStatusMenu },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = status,
                        onValueChange = { status = it },
                        label = { Text("Estado (ACTIVE, CLOSED, ARCHIVED)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedStatusMenu) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedStatusMenu,
                        onDismissRequest = { expandedStatusMenu = false }
                    ) {
                        validStatuses.forEach { st ->
                            DropdownMenuItem(
                                text = { Text(st) },
                                onClick = {
                                    status = st
                                    expandedStatusMenu = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalStatus = status.trim().uppercase()
                    onUpdate(title, description, if (finalStatus.isBlank()) "ACTIVE" else finalStatus)
                }
            ) {
                Text("Guardar Cambios (Registrar Audit)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
