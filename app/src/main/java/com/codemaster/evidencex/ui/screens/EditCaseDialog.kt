package com.codemaster.evidencex.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codemaster.evidencex.data.local.entity.CaseEntity

@Composable
fun EditCaseDialog(
    case: CaseEntity,
    onDismiss: () -> Unit,
    onUpdate: (title: String, description: String, status: String) -> Unit
) {
    var title by remember { mutableStateOf(case.title) }
    var description by remember { mutableStateOf(case.description) }
    var status by remember { mutableStateOf(case.status.ifBlank { "ACTIVE" }) }
    val statuses = listOf("ACTIVE", "CLOSED", "ARCHIVED")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Expediente (Solo Administrador)") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                Spacer(modifier = Modifier.height(12.dp))
                Text("Seleccionar Estado del Expediente:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))

                statuses.forEach { s ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { status = s }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = status.equals(s, ignoreCase = true),
                            onClick = { status = s }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = s,
                            fontWeight = if (status.equals(s, ignoreCase = true)) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onUpdate(title.trim(), description.trim(), status.trim().uppercase()) }) {
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
