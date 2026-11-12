package com.codemaster.evidencex

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.codemaster.evidencex.data.export.ForensicPackageExporter
import com.codemaster.evidencex.data.local.entity.CaseEntity
import com.codemaster.evidencex.data.local.entity.EvidenceEntity
import com.codemaster.evidencex.ui.screens.*
import com.codemaster.evidencex.ui.theme.EVIDENCEXTheme
import com.codemaster.evidencex.ui.viewmodel.EvidenceViewModel
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (!cameraGranted || !locationGranted) {
            Toast.makeText(this, "Se requieren permisos de cámara y ubicación GPS para el levantamiento de evidencias.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionsLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        setContent {
            EVIDENCEXTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EvidenceXAppNavHost()
                }
            }
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun EvidenceXAppNavHost(viewModel: EvidenceViewModel = viewModel()) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = { _, _ ->
                    navController.navigate("dashboard") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        composable("dashboard") {
            DashboardScreen(navController = navController, viewModel = viewModel)
        }
        composable("admin_users") {
            AdminUserManagementScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("audit_log") {
            AuditLogScreen(viewModel = viewModel)
        }
        composable("integrity") {
            IntegrityScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(
            route = "camera_capture/{caseId}",
            arguments = listOf(navArgument("caseId") { type = NavType.StringType })
        ) { backStackEntry ->
            val caseId = backStackEntry.arguments?.getString("caseId") ?: ""
            val currentUserId by viewModel.currentUserId.collectAsState()
            CameraCaptureScreen(
                caseId = caseId,
                investigatorId = currentUserId,
                onEvidenceCaptured = { file, fileName, lat, lng, sensitive ->
                    try {
                        viewModel.captureEvidence(
                            caseId = caseId,
                            sourceFile = file,
                            fileName = fileName,
                            notes = if (sensitive) "EVIDENCIA SENSIBLE / CLASIFICADA" else "Levantamiento de evidencia multimedia con GPS.",
                            lat = lat,
                            lng = lng,
                            investigatorId = currentUserId,
                            sensitive = sensitive
                        )
                        Toast.makeText(contextSafe(navController.context), "Evidencia guardada exitosamente", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(contextSafe(navController.context), "Error al guardar evidencia: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}

fun contextSafe(context: android.content.Context) = context

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavController, viewModel: EvidenceViewModel) {
    val cases by viewModel.cases.collectAsState()
    val users by viewModel.users.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredCases = remember(cases, searchQuery, users) {
        if (searchQuery.isBlank()) cases
        else {
            val matchingUserIds = users.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.id.contains(searchQuery, ignoreCase = true) ||
                it.email.contains(searchQuery, ignoreCase = true)
            }.map { it.id }

            cases.filter { case ->
                case.caseNumber.contains(searchQuery, ignoreCase = true) ||
                case.title.contains(searchQuery, ignoreCase = true) ||
                case.description.contains(searchQuery, ignoreCase = true) ||
                case.investigatorId.contains(searchQuery, ignoreCase = true) ||
                matchingUserIds.any { case.investigatorId.equals(it, ignoreCase = true) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("EVIDENCE X - Cadena de Custodia", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            text = if (isAdmin) "Modo: Administrador (Control Total)" else "Modo: Perito ($currentUserId)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                },
                actions = {
                    if (isAdmin) {
                        IconButton(onClick = { navController.navigate("admin_users") }) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Gestionar Peritos")
                        }
                    }
                    IconButton(onClick = { navController.navigate("integrity") }) {
                        Icon(Icons.Default.Lock, contentDescription = "Verificador de Integridad")
                    }
                    IconButton(onClick = { navController.navigate("audit_log") }) {
                        Icon(Icons.Default.Security, contentDescription = "Bitácora")
                    }
                    IconButton(onClick = { viewModel.triggerSync() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sincronizar")
                    }
                    IconButton(onClick = {
                        FirebaseAuth.getInstance().signOut()
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Cerrar Sesión")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Nuevo Expediente")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Buscar expediente, título o perito...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(modifier = Modifier.fillMaxSize()) {
                if (filteredCases.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No se encontraron expedientes.", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Intente otra búsqueda o pulse '+' para crear uno.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filteredCases) { case ->
                            CaseCard(case = case, viewModel = viewModel, navController = navController, isAdmin = isAdmin, currentUserId = currentUserId)
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }

                if (showCreateDialog) {
                    CreateCaseDialog(
                        defaultInvestigatorId = currentUserId,
                        onDismiss = { showCreateDialog = false },
                        onCreate = { number, title, desc, inv ->
                            viewModel.createCase(number, title, desc, inv)
                            showCreateDialog = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CaseCard(case: CaseEntity, viewModel: EvidenceViewModel, navController: NavController, isAdmin: Boolean, currentUserId: String) {
    var expanded by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var isSensitiveEvidence by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val evidences by remember(case.id) {
        viewModel.getEvidencesForCase(case.id)
    }.collectAsState(initial = emptyList())
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    fun captureGpsAndSave(sourceFile: File, fileName: String, sensitive: Boolean) {
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    val lat = location?.latitude ?: 19.4326
                    val lng = location?.longitude ?: -99.1332
                    try {
                        viewModel.captureEvidence(case.id, sourceFile, fileName, if (sensitive) "EVIDENCIA SENSIBLE / CLASIFICADA" else "Evidencia multimedia", lat, lng, currentUserId, sensitive)
                        Toast.makeText(context, "Evidencia guardada con GPS ($lat, $lng)", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error interno al guardar: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }.addOnFailureListener {
                    viewModel.captureEvidence(case.id, sourceFile, fileName, "Evidencia multimedia", 19.4326, -99.1332, currentUserId, sensitive)
                    Toast.makeText(context, "Evidencia guardada", Toast.LENGTH_SHORT).show()
                }
            } else {
                viewModel.captureEvidence(case.id, sourceFile, fileName, "Evidencia multimedia", 19.4326, -99.1332, currentUserId, sensitive)
                Toast.makeText(context, "Evidencia guardada", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error al guardar evidencia: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val tempFile = File(context.cacheDir, "gallery_${System.currentTimeMillis()}.dat")
                inputStream?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                captureGpsAndSave(tempFile, "archivo_galeria_${System.currentTimeMillis()}", isSensitiveEvidence)
            } catch (e: Exception) {
                Toast.makeText(context, "Error al procesar archivo de galería: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val videoRecordLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val videoUri = result.data?.data
            if (videoUri != null) {
                try {
                    val inputStream = context.contentResolver.openInputStream(videoUri)
                    val tempFile = File(context.cacheDir, "video_${System.currentTimeMillis()}.mp4")
                    inputStream?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    captureGpsAndSave(tempFile, tempFile.name, isSensitiveEvidence)
                } catch (e: Exception) {
                    Toast.makeText(context, "Error al guardar video grabado: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Exp: ${case.caseNumber}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("👤 Perito: ${case.investigatorId}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.DarkGray)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(case.title, style = MaterialTheme.typography.titleMedium)
                Text("🕒 ${formatTimestamp(case.createdAt)}", fontSize = 10.sp, color = Color.Gray)
            }
            Text(case.description, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Estado: ${case.status}", style = MaterialTheme.typography.bodySmall)
                Text(if (case.synced) "Sincronizado" else "Pendiente", color = if (case.synced) Color.Green else Color.Red, fontSize = 11.sp)
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // Sensitive Evidence Toggle inside Card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Marcar siguiente evidencia como Sensible [⚠️]", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Switch(
                        checked = isSensitiveEvidence,
                        onCheckedChange = { isSensitiveEvidence = it }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Display Captured Evidences References & GPS Locations
                Text("Evidencias Levantadas (${evidences.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                if (evidences.isEmpty()) {
                    Text("No hay evidencias registradas en este expediente.", fontSize = 12.sp, color = Color.Gray)
                } else {
                    evidences.forEach { ev ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (ev.sensitive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("📄 Ref: ${ev.fileName}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    if (ev.sensitive) {
                                        Text("⚠️ SENSIBLE", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                                Text("📍 GPS: Lat ${String.format(Locale.US, "%.4f", ev.gpsLat)}, Lng ${String.format(Locale.US, "%.4f", ev.gpsLng)}", fontSize = 11.sp, color = Color.DarkGray)
                                Text("🕒 ${formatTimestamp(ev.timestamp)}", fontSize = 10.sp, color = Color.Gray)
                                Text("🔐 Hash: ${ev.evidenceHash.take(24)}...", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    // Action Buttons: Camera Capture, 20s Video Recording, Gallery Upload
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Button(onClick = {
                            navController.navigate("camera_capture/${case.id}")
                        }) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Cámara", fontSize = 12.sp)
                        }

                        Button(onClick = {
                            val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
                                putExtra(MediaStore.EXTRA_DURATION_LIMIT, 20) // Max 20 seconds recording
                                putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
                            }
                            videoRecordLauncher.launch(intent)
                        }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) {
                            Icon(Icons.Default.Videocam, contentDescription = null)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Video 20s", fontSize = 12.sp)
                        }

                        Button(onClick = {
                            galleryLauncher.launch("*/*") // Pick photo or video from gallery
                        }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                            Text("Galería", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        OutlinedButton(onClick = {
                            val auditEvents = viewModel.auditEvents.value
                            val pdfFile = viewModel.generatePdfReport(case, evidences, auditEvents)
                            try {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "application/pdf")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "PDF Guardado: ${pdfFile.absolutePath}", Toast.LENGTH_LONG).show()
                            }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF")
                        }

                        OutlinedButton(onClick = {
                            val evxFile = ForensicPackageExporter.exportForensicPackage(context, case, evidences, emptyList())
                            Toast.makeText(context, "Paquete .EVX Exportado: ${evxFile.name}", Toast.LENGTH_LONG).show()
                        }) {
                            Text("Exportar .EVX")
                        }
                    }

                    if (isAdmin) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Button(
                                onClick = { showEditDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                                Text("Editar (Admin)")
                            }

                            Button(
                                onClick = {
                                    viewModel.deleteCase(case.id, currentUserId)
                                    Toast.makeText(context, "Expediente eliminado y auditado", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                                Text("Eliminar (Admin)")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        EditCaseDialog(
            case = case,
            onDismiss = { showEditDialog = false },
            onUpdate = { title, desc, status ->
                viewModel.updateCase(case.id, title, desc, status, currentUserId)
                showEditDialog = false
                Toast.makeText(context, "Expediente actualizado y auditado", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun CreateCaseDialog(defaultInvestigatorId: String, onDismiss: () -> Unit, onCreate: (String, String, String, String) -> Unit) {
    var caseNumber by remember { mutableStateOf("EXP-2026-002") }
    var title by remember { mutableStateOf("Investigación de Campo") }
    var description by remember { mutableStateOf("Toma de evidencias en sitio.") }
    var investigatorId by remember { mutableStateOf(defaultInvestigatorId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo Expediente Offline-First") },
        text = {
            Column {
                OutlinedTextField(value = caseNumber, onValueChange = { caseNumber = it }, label = { Text("Número de Expediente") })
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Título") })
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Descripción") })
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = investigatorId, onValueChange = { investigatorId = it }, label = { Text("ID Perito Asignado") })
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(caseNumber, title, description, investigatorId) }) {
                Text("Crear y Registrar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreen(viewModel: EvidenceViewModel) {
    val auditEvents by viewModel.auditEvents.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Bitácora y Cadena de Custodia", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            items(auditEvents) { event ->
                Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Acción: ${event.eventType}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            Text("Usuario: ${event.userId}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.DarkGray)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(event.eventDetails, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("🕒 ${formatTimestamp(event.timestamp)}", fontSize = 11.sp, color = Color.Gray)
                        Text("Hash Actual: ${event.eventHash.take(32)}...", fontSize = 10.sp, color = Color.DarkGray)
                        Text("Hash Previo: ${event.prevEventHash.take(32)}...", fontSize = 10.sp, color = Color.Gray)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
