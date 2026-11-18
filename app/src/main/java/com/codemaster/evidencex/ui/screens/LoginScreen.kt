package com.codemaster.evidencex.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codemaster.evidencex.R
import com.codemaster.evidencex.ui.viewmodel.EvidenceViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    viewModel: EvidenceViewModel,
    onLoginSuccess: (isAdmin: Boolean, userId: String) -> Unit
) {
    var email by remember { mutableStateOf("superadmin@evidencex.com") }
    var password by remember { mutableStateOf("super123") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.evidencex_logo),
                contentDescription = "Logo EVIDENCE X",
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("EVIDENCE X", fontWeight = FontWeight.Bold, fontSize = 28.sp, color = MaterialTheme.colorScheme.primary)
            Text("Sistema Forense Multi-Área", fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Correo o ID (ej. superadmin@evidencex.com)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        errorMessage = "Por favor ingrese usuario y contraseña."
                        return@Button
                    }
                    isLoading = true
                    errorMessage = null

                    scope.launch {
                        // 1. Authenticate against local Room database (Offline-First hierarchy)
                        val localUser = viewModel.authenticateLocalUser(email.trim(), password.trim())
                        if (localUser != null) {
                            isLoading = false
                            val role = localUser.role // SUPER_ADMIN, ADMIN_AREA, PERITO
                            val isAdmin = role == "SUPER_ADMIN" || role == "ADMIN_AREA"
                            viewModel.setUserCredentials(role, localUser.id, localUser.area)
                            onLoginSuccess(isAdmin, localUser.id)
                            return@launch
                        }

                        // 2. Fallback to Firebase Authentication online
                        val auth = FirebaseAuth.getInstance()
                        auth.signInWithEmailAndPassword(email.trim(), password)
                            .addOnCompleteListener { task ->
                                isLoading = false
                                if (task.isSuccessful) {
                                    val user = auth.currentUser
                                    val userId = user?.email ?: email
                                    val role = if (userId.contains("super", ignoreCase = true)) "SUPER_ADMIN" else if (userId.contains("admin", ignoreCase = true)) "ADMIN_AREA" else "PERITO"
                                    val area = if (userId.contains("balistica", ignoreCase = true)) "Balística" else if (userId.contains("informatica", ignoreCase = true)) "Informática" else if (userId.contains("dactilo", ignoreCase = true)) "Dactiloscopia" else "General"
                                    viewModel.setUserCredentials(role, userId, area)
                                    onLoginSuccess(role != "PERITO", userId)
                                } else {
                                    errorMessage = "Credenciales incorrectas o usuario no registrado."
                                }
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Iniciar Sesión (Jerárquico)")
                }
            }
        }
    }
}
