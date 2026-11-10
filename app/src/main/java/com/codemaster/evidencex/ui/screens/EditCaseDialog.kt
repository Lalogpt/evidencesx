package com.codemaster.evidencex.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codemaster.evidencex.data.local.entity.CaseEntity

@Composable
fun EditCaseDialog(
    case: CaseEntity,
    onDismiss: () -> Unit,
    onUpdate: (title: String, description: String, status: String) -> Unit
) {
    var title by remember { mutableStateOf(case.title) }
    var description by remember { mutableStateOf(case.description) }
    var status by remember { mutableStateOf(case.status) }

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
                OutlinedTextField(
                    value = status,
                    onValueChange = { status = it },
                    label = { Text("Estado (ACTIVE, CLOSED, ARCHIVED)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onUpdate(title, description, status) }) {
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
