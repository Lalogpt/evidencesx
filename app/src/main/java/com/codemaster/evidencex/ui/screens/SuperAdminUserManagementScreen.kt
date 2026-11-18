package com.codemaster.evidencex.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.codemaster.evidencex.ui.viewmodel.EvidenceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperAdminUserManagementScreen(viewModel: EvidenceViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val users by viewModel.users.collectAsState()

    var adminName by remember { mutableStateOf("") }
    var adminEmail by remember { mutableStateOf("") }
    var adminPassword by remember { mutableStateOf("123456") }
    var selectedArea by remember { mutableStateOf("Balística") }
    val areas = listOf("Balística", "Informática", "Dactiloscopia", "Criminalística")
    var expandedAreaMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Super Admin - Gestión de Admins de Área") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("Crear Administrador de Área (Dinámico)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = adminName,
                onValueChange = { adminName = it },
                label = { Text("Nombre del Admin de Área") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = adminEmail,
                onValueChange = { adminEmail = it },
                label = { Text("Correo o ID (ej. admin.balistica@evidencex.com)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = adminPassword,
                onValueChange = { adminPassword = it },
                label = { Text("Contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Area dropdown selection
            ExposedDropdownMenuBox(
                expanded = expandedAreaMenu,
                onExpandedChange = { expandedAreaMenu = !expandedAreaMenu },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedArea,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Área Asignada") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAreaMenu) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                )
                ExposedDropdownMenu(
                    expanded = expandedAreaMenu,
                    onDismissRequest = { expandedAreaMenu = false }
                ) {
                    areas.forEach { area ->
                        DropdownMenuItem(
                            text = { Text(area) },
                            onClick = {
                                selectedArea = area
                                expandedAreaMenu = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val trimmedEmail = adminEmail.trim()
                    if (trimmedEmail.isBlank() || adminName.isBlank() || adminPassword.isBlank()) {
                        Toast.makeText(context, "Complete todos los campos", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val exists = users.any { it.id.equals(trimmedEmail, ignoreCase = true) }
                    if (exists) {
                        Toast.makeText(context, "⚠️ El usuario '$trimmedEmail' ya existe.", Toast.LENGTH_LONG).show()
                        return@Button
                    }

                    viewModel.createAreaAdminUser(adminName, trimmedEmail, adminPassword.trim(), selectedArea)
                    Toast.makeText(context, "Admin de Área $selectedArea creado y guardado en BD.", Toast.LENGTH_LONG).show()
                    adminName = ""
                    adminEmail = ""
                    adminPassword = "123456"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Registrar Administrador de Área")
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Administradores de Área Registrados:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(users.filter { it.role == "ADMIN_AREA" }) { user ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        ListItem(
                            headlineContent = { Text("${user.name} (${user.id})", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Área: ${user.area} | Rol: ${user.role}") },
                            trailingContent = {
                                IconButton(onClick = {
                                    viewModel.deleteUser(user.id)
                                    Toast.makeText(context, "Administrador eliminado", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
