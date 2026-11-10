package com.codemaster.evidencex.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.codemaster.evidencex.ui.viewmodel.EvidenceViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUserManagementScreen(viewModel: EvidenceViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val users by viewModel.users.collectAsState()

    var peritoName by remember { mutableStateOf("") }
    // Auto-suggest next unique ID based on existing users count
    var peritoId by remember { mutableStateOf("") }
    var peritoPassword by remember { mutableStateOf("123456") }

    // Update suggested ID when users list changes if peritoId is empty
    LaunchedEffect(users) {
        if (peritoId.isEmpty()) {
            val nextNum = users.size + 1
            peritoId = String.format(Locale.US, "PERITO-%03d", nextNum)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestión de Cuentas de Peritos") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("Crear Cuenta de Perito con ID Único", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = peritoName,
                onValueChange = { peritoName = it },
                label = { Text("Nombre del Perito") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = peritoId,
                onValueChange = { peritoId = it },
                label = { Text("ID Único de Perito (No repetible)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = peritoPassword,
                onValueChange = { peritoPassword = it },
                label = { Text("Contraseña de Acceso") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val trimmedId = peritoId.trim()
                    if (trimmedId.isBlank() || peritoName.isBlank() || peritoPassword.isBlank()) {
                        Toast.makeText(context, "Complete todos los campos", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    // Check for duplicate ID in existing users
                    val exists = users.any { it.id.equals(trimmedId, ignoreCase = true) }
                    if (exists) {
                        Toast.makeText(context, "⚠️ El ID '$trimmedId' ya existe. Por favor use un ID único.", Toast.LENGTH_LONG).show()
                        return@Button
                    }

                    viewModel.createPeritoUser(peritoName, trimmedId, peritoPassword.trim())
                    Toast.makeText(context, "Perito $trimmedId creado con éxito.", Toast.LENGTH_LONG).show()
                    peritoName = ""
                    peritoPassword = "123456"
                    // Suggest next ID
                    val nextNum = users.size + 2
                    peritoId = String.format(Locale.US, "PERITO-%03d", nextNum)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Registrar Perito Único")
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Peritos Registrados en el Sistema:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(users) { user ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        ListItem(
                            headlineContent = { Text("${user.name} (ID: ${user.id})", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Rol: ${user.role} | Contraseña: ${user.password}") }
                        )
                    }
                }
            }
        }
    }
}
