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
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
import com.codemaster.evidencex.util.QrCodeGenerator
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
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
        composable("super_admin_users") {
            SuperAdminUserManagementScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("audit_log") {
            AuditLogScreen(viewModel = viewModel)
        }
        composable("integrity") {
            IntegrityScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(
            route = "chat/{recipientId}/{recipientName}/{area}",
            arguments = listOf(
                navArgument("recipientId") { type = NavType.StringType },
                navArgument("recipientName") { type = NavType.StringType },
                navArgument("area") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val recipientId = backStackEntry.arguments?.getString("recipientId") ?: ""
            val recipientName = backStackEntry.arguments?.getString("recipientName") ?: ""
            val area = backStackEntry.arguments?.getString("area") ?: ""
            val currentUserId by viewModel.currentUserId.collectAsState()
            ChatScreen(
                viewModel = viewModel,
                currentUserId = currentUserId,
                recipientId = recipientId,
                recipientName = recipientName,
                area = area,
                onBack = { navController.popBackStack() }
            )
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
                        Toast.makeText(contextSafe(navController.context), "Evidencia guardada y Cédula QR generada", Toast.LENGTH_SHORT).show()
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
    val currentUserRole by viewModel.currentUserRole.collectAsState()
    val currentArea by viewModel.currentArea.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showQrScannerDialog by remember { mutableStateOf(false) }
    var showPeritoChatSelector by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val isAdmin = currentUserRole == "SUPER_ADMIN" || currentUserRole == "ADMIN_AREA"
    val isSuperAdmin = currentUserRole == "SUPER_ADMIN"

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
                case.area.contains(searchQuery, ignoreCase = true) ||
                matchingUserIds.any { case.investigatorId.equals(it, ignoreCase = true) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "EVIDENCE X",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when (currentUserRole) {
                                "SUPER_ADMIN" -> "Super Admin ($currentArea)"
                                "ADMIN_AREA" -> "Admin: $currentArea"
                                else -> "Perito: $currentUserId"
                            },
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (isAdmin) {
                            showPeritoChatSelector = true
                        } else {
                            val adminEmail = "admin.${currentArea.lowercase().replace(" ", "")}@evidencex.com"
                            navController.navigate("chat/$adminEmail/Admin $currentArea/$currentArea")
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chat Operativo")
                    }
                    IconButton(onClick = { showQrScannerDialog = true }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Escanear Cédula QR")
                    }
                    if (isSuperAdmin) {
                        IconButton(onClick = { navController.navigate("super_admin_users") }) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = "Gestionar Admins")
                        }
                    } else if (isAdmin) {
                        IconButton(onClick = { navController.navigate("admin_users") }) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Gestionar Peritos")
                        }
                    }
                    IconButton(onClick = { viewModel.triggerSync() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sincronizar")
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = !showOverflowMenu }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Verificador de Integridad") },
                                onClick = {
                                    showOverflowMenu = false
                                    navController.navigate("integrity")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Bitácora de Auditoría") },
                                onClick = {
                                    showOverflowMenu = false
                                    navController.navigate("audit_log")
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Cerrar Sesión", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showOverflowMenu = false
                                    FirebaseAuth.getInstance().signOut()
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }
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
                label = { Text("Buscar por expediente, título, perito o área...") },
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
                        defaultArea = currentArea,
                        isAdmin = isAdmin,
                        onDismiss = { showCreateDialog = false },
                        onCreate = { number, title, desc, inv, area ->
                            viewModel.createCase(number, title, desc, inv, area)
                            showCreateDialog = false
                        }
                    )
                }

                if (showQrScannerDialog) {
                    QrScannerDialog(viewModel = viewModel, onDismiss = { showQrScannerDialog = false })
                }

                if (showPeritoChatSelector) {
                    AlertDialog(
                        onDismissRequest = { showPeritoChatSelector = false },
                        title = { Text("Seleccionar Perito para Chat") },
                        text = {
                            LazyColumn(modifier = Modifier.height(300.dp)) {
                                items(users.filter { it.role == "PERITO" }) { perito ->
                                    ListItem(
                                        headlineContent = { Text(perito.name) },
                                        supportingContent = { Text("ID: ${perito.id} | Área: ${perito.area}") },
                                        modifier = Modifier.clickable {
                                            showPeritoChatSelector = false
                                            navController.navigate("chat/${perito.id}/${perito.name}/${perito.area}")
                                        }
                                    )
                                    HorizontalDivider()
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showPeritoChatSelector = false }) {
                                Text("Cerrar")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EvidenceQrDialog(evidence: EvidenceEntity, onDismiss: () -> Unit) {
    val qrBitmap = remember {
        QrCodeGenerator.generateQrBitmap("EVIDENCE_X_REF:${evidence.id}:${evidence.evidenceHash}")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cédula QR Forense") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("ID: ${evidence.id}", fontSize = 10.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "Código QR de Evidencia",
                    modifier = Modifier.size(200.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Hash: ${evidence.evidenceHash.take(24)}...", fontSize = 10.sp, color = Color.DarkGray)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
fun EvidencePhotoDialog(evidence: EvidenceEntity, onDismiss: () -> Unit) {
    val file = File(evidence.fileUri)
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(evidence.fileUri) {
        withContext(Dispatchers.IO) {
            if (file.exists()) {
                bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            } else {
                try {
                    val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference.child("evidence/${evidence.caseId}/${evidence.fileName}")
                    val localDir = File(context.filesDir, "secure_evidence").apply { mkdirs() }
                    val tempFile = File(localDir, evidence.fileName)
                    storageRef.getFile(tempFile).await()
                    if (tempFile.exists()) {
                        bitmap = android.graphics.BitmapFactory.decodeFile(tempFile.absolutePath)
                    }
                } catch (e: Exception) {}
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vista Previa de Fotografía") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "Foto de Evidencia",
                        modifier = Modifier.size(280.dp)
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    Text("Descargando foto desde la nube...", fontSize = 12.sp, color = Color.Gray)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Archivo: ${evidence.fileName}", fontSize = 11.sp, color = Color.Gray)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
fun QrScannerDialog(viewModel: EvidenceViewModel, onDismiss: () -> Unit) {
    var scannedInput by remember { mutableStateOf("") }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val currentUserRole by viewModel.currentUserRole.collectAsState()
    val currentArea by viewModel.currentArea.collectAsState()
    val scope = rememberCoroutineScope()

    fun validateAndFetch(queryIdRaw: String) {
        val queryId = queryIdRaw.substringAfterLast(":").trim()
        scannedInput = queryId
        scope.launch {
            val (evidence, case) = viewModel.getEvidenceAndCaseDetails(queryId)
            if (evidence == null || case == null) {
                resultMessage = "❌ Evidencia no encontrada para: $queryId"
                isError = true
                return@launch
            }

            val hasAccess = when (currentUserRole) {
                "SUPER_ADMIN" -> true
                "ADMIN_AREA" -> case.area.equals(currentArea, ignoreCase = true)
                else -> case.investigatorId.equals(viewModel.currentUserId.value, ignoreCase = true)
            }

            if (!hasAccess) {
                isError = true
                resultMessage = "⚠️ Acceso Denegado por Jurisdicción de Área:\n\nEsta evidencia pertenece al departamento de [${case.area}]. No corresponde a su área actual [$currentArea]. Por favor, acuda con el responsable a cargo."
            } else {
                isError = false
                resultMessage = "✅ Acceso Concedido:\nExpediente: ${case.caseNumber} [Área: ${case.area}]\nInvestigador: ${case.investigatorId}"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Escanear Cédula QR Forense") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                // Live Camera Viewfinder Preview with ZXing ImageAnalysis
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()

                                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                    try {
                                        val buffer = imageProxy.planes[0].buffer
                                        val bytes = ByteArray(buffer.remaining())
                                        buffer.get(bytes)
                                        val width = imageProxy.width
                                        val height = imageProxy.height
                                        imageProxy.close()

                                        val source = com.google.zxing.PlanarYUVLuminanceSource(
                                            bytes, width, height, 0, 0, width, height, false
                                        )
                                        val binaryBitmap = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))
                                        val result = com.google.zxing.MultiFormatReader().decode(binaryBitmap)
                                        if (result != null && !result.text.isNullOrBlank()) {
                                            val decoded = result.text
                                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                validateAndFetch(decoded)
                                            }
                                        }
                                    } catch (_: Exception) {
                                        imageProxy.close()
                                    }
                                }

                                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                try {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner, cameraSelector, preview, imageAnalysis
                                    )
                                } catch (_: Exception) {}
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        modifier = Modifier.align(Alignment.Center).size(100.dp),
                        color = Color.Transparent,
                        border = BorderStroke(2.dp, Color.Green),
                        shape = RoundedCornerShape(8.dp)
                    ) {}
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Enfoque el código QR en la cámara o ingrese el ID:", fontSize = 11.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = scannedInput,
                    onValueChange = { scannedInput = it },
                    label = { Text("ID de Evidencia o Cédula (UUID)") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (resultMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        resultMessage!!,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                validateAndFetch(scannedInput)
            }) {
                Text("Validar Jurisdicción")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
fun CaseCard(case: CaseEntity, viewModel: EvidenceViewModel, navController: NavController, isAdmin: Boolean, currentUserId: String) {
    var expanded by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showQrDialogForEvidence by remember { mutableStateOf<EvidenceEntity?>(null) }
    var showPhotoDialogForEvidence by remember { mutableStateOf<EvidenceEntity?>(null) }
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
                        Toast.makeText(context, "Evidencia guardada y Cédula QR generada", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error interno al guardar: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }.addOnFailureListener {
                    viewModel.captureEvidence(case.id, sourceFile, fileName, "Evidencia multimedia", 19.4326, -99.1332, currentUserId, sensitive)
                    Toast.makeText(context, "Evidencia guardada y Cédula QR generada", Toast.LENGTH_SHORT).show()
                }
            } else {
                viewModel.captureEvidence(case.id, sourceFile, fileName, "Evidencia multimedia", 19.4326, -99.1332, currentUserId, sensitive)
                Toast.makeText(context, "Evidencia guardada y Cédula QR generada", Toast.LENGTH_SHORT).show()
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
                Text("📁 Área: ${case.area}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(case.title, style = MaterialTheme.typography.titleMedium)
                Text("👤 ${case.investigatorId}", fontSize = 11.sp, color = Color.DarkGray)
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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (ev.sensitive) {
                                            Text("⚠️ SENSIBLE", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        if (ev.fileName.endsWith(".jpg", true) || ev.fileName.endsWith(".png", true)) {
                                            TextButton(onClick = { showPhotoDialogForEvidence = ev }, contentPadding = PaddingValues(0.dp)) {
                                                Icon(Icons.Default.Image, contentDescription = "Ver Foto", modifier = Modifier.size(18.dp))
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        TextButton(onClick = { showQrDialogForEvidence = ev }, contentPadding = PaddingValues(0.dp)) {
                                            Icon(Icons.Default.QrCode, contentDescription = "Ver Cédula QR", modifier = Modifier.size(18.dp))
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        TextButton(
                                            onClick = {
                                                try {
                                                    val file = File(ev.fileUri)
                                                    if (file.exists()) {
                                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                                            type = if (ev.fileName.endsWith(".mp4", true)) "video/mp4" else "image/*"
                                                            putExtra(Intent.EXTRA_STREAM, uri)
                                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        }
                                                        context.startActivity(Intent.createChooser(intent, "Compartir Archivo de Evidencia"))
                                                    } else {
                                                        Toast.makeText(context, "Archivo multimedia no disponible localmente.", Toast.LENGTH_SHORT).show()
                                                    }
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Error al compartir archivo: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                }
                                            },
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = "Compartir Archivo", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                                Text("📍 GPS: Lat ${String.format(Locale.US, "%.4f", ev.gpsLat)}, Lng ${String.format(Locale.US, "%.4f", ev.gpsLng)}", fontSize = 11.sp, color = Color.DarkGray)
                                Text("🕒 ${formatTimestamp(ev.timestamp)}", fontSize = 10.sp, color = Color.Gray)
                                Text("🔐 Hash: ${ev.evidenceHash.take(24)}...", fontSize = 10.sp, color = Color.DarkGray)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    // Action Buttons: Camera Capture, 20s Video Recording, Gallery Upload (Equally weighted without overflow)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = { navController.navigate("camera_capture/${case.id}") },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Cámara", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
                                    putExtra(MediaStore.EXTRA_DURATION_LIMIT, 20)
                                    putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
                                }
                                videoRecordLauncher.launch(intent)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Video 20s", fontSize = 11.sp)
                        }

                        Button(
                            onClick = { galleryLauncher.launch("*/*") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
                        ) {
                            Text("Galería", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Row 2: Reports (PDF, .EVX)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val scope = rememberCoroutineScope()
                        OutlinedButton(
                            onClick = {
                                val auditEvents = viewModel.auditEvents.value
                                scope.launch {
                                    val pdfFile = viewModel.generatePdfReport(case, auditEvents)
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
                                }
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val evxFile = ForensicPackageExporter.exportForensicPackage(context, case, evidences, emptyList())
                                Toast.makeText(context, "Paquete .EVX Exportado: ${evxFile.name}", Toast.LENGTH_LONG).show()
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                        ) {
                            Text("Exportar .EVX", fontSize = 11.sp)
                        }
                    }

                    if (isAdmin) {
                        Spacer(modifier = Modifier.height(6.dp))
                        // Row 3: Admin Actions (Edit, Delete) with confirmation
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { showEditDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Editar", fontSize = 12.sp)
                            }

                            Button(
                                onClick = { showDeleteConfirmDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Eliminar", fontSize =.12.sp)
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

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Confirmar Eliminación") },
            text = { Text("¿Deseas eliminar permanentemente este expediente? THis action will be audited.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteCase(case.id, currentUserId)
                        Toast.makeText(context, "Expediente eliminado y auditado", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sí, Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showQrDialogForEvidence != null) {
        EvidenceQrDialog(evidence = showQrDialogForEvidence!!, onDismiss = { showQrDialogForEvidence = null })
    }

    if (showPhotoDialogForEvidence != null) {
        EvidencePhotoDialog(evidence = showPhotoDialogForEvidence!!, onDismiss = { showPhotoDialogForEvidence = null })
    }
}

@Composable
fun CreateCaseDialog(defaultInvestigatorId: String, defaultArea: String, isAdmin: Boolean, onDismiss: () -> Unit, onCreate: (String, String, String, String, String) -> Unit) {
    val areaCode = defaultArea.take(3).uppercase()
    val randomId = (1000..9999).random()
    var caseNumber by remember { mutableStateOf("EXP-$areaCode-$randomId") }
    var title by remember { mutableStateOf("Investigación de Campo") }
    var description by remember { mutableStateOf("Toma de evidencias en sitio.") }
    var investigatorId by remember { mutableStateOf(defaultInvestigatorId) }
    var area by remember { mutableStateOf(defaultArea) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo Expediente Offline-First") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { /* fixed */ }, modifier = Modifier.fillMaxWidth()) { Text("Área Asignada: $area (Bloqueada)") }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = caseNumber, onValueChange = { caseNumber = it }, label = { Text("Número de Expediente") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Nombre del Hecho / Título") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Descripción de los Hechos") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = investigatorId,
                    onValueChange = { if (isAdmin) investigatorId = it },
                    readOnly = !isAdmin,
                    label = { Text("Perito Asignado") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(caseNumber, title, description, investigatorId, area) }) {
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
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Bitácora y Cadena de Custodia", fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
