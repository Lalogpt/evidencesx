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
    var email by remember { mutableStateOf("PERITO-007") }
    var password by remember { mutableStateOf("123456") }
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
            Text("Sistema Forense Offline-First", fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("ID de Perito o Correo (ej. PERITO-007)") },
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
                        // 1. Check local Room database first (Offline-First authentication for created Peritos & Admin)
                        val localUser = viewModel.authenticateLocalUser(email.trim(), password.trim())
                        if (localUser != null) {
                            isLoading = false
                            val isAdmin = localUser.role.equals("ADMIN", ignoreCase = true)
                            viewModel.setUserRole(isAdmin, localUser.id)
                            onLoginSuccess(isAdmin, localUser.id)
                            return@launch
                        }

                        // 2. Hardcoded fallback admin test account
                        if (email.trim().equals("admin@evidencex.com", ignoreCase = true) && password == "admin123") {
                            isLoading = false
                            viewModel.setUserRole(true, "ADMIN-01")
                            onLoginSuccess(true, "ADMIN-01")
                            return@launch
                        }

                        // 3. Fallback to Firebase Authentication online
                        val auth = FirebaseAuth.getInstance()
                        auth.signInWithEmailAndPassword(email.trim(), password)
                            .addOnCompleteListener { task ->
                                isLoading = false
                                if (task.isSuccessful) {
                                    val user = auth.currentUser
                                    val userId = user?.email ?: email
                                    val isAdmin = userId.contains("admin", ignoreCase = true)
                                    viewModel.setUserRole(isAdmin, userId)
                                    onLoginSuccess(isAdmin, userId)
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
                    Text("Iniciar Sesión (Local / Firebase)")
                }
            }
        }
    }
}
