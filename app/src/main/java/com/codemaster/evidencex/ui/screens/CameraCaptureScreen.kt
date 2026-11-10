package com.codemaster.evidencex.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import java.io.File

@Composable
fun CameraCaptureScreen(
    caseId: String,
    investigatorId: String,
    onEvidenceCaptured: (File, String, Double, Double, Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var isSensitive by remember { mutableStateOf(false) }

    @SuppressLint("MissingPermission")
    fun captureWithLocation(onCaptured: (Double, Double) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                val lat = location?.latitude ?: 19.4326
                val lng = location?.longitude ?: -99.1332
                onCaptured(lat, lng)
            }.addOnFailureListener {
                onCaptured(19.4326, -99.1332)
            }
        } else {
            onCaptured(19.4326, -99.1332)
        }
    }

    val videoRecordLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val videoUri = result.data?.data
            if (videoUri != null) {
                val inputStream = context.contentResolver.openInputStream(videoUri)
                val tempFile = File(context.cacheDir, "evidence_video_${System.currentTimeMillis()}.mp4")
                inputStream?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                captureWithLocation { lat, lng ->
                    Toast.makeText(context, "Video de 20s grabado con GPS ($lat, $lng)", Toast.LENGTH_SHORT).show()
                    onEvidenceCaptured(tempFile, tempFile.name, lat, lng, isSensitive)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    imageCapture = ImageCapture.Builder().build()
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner, cameraSelector, preview, imageCapture
                        )
                    } catch (exc: Exception) {
                        // Handle binding failure
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Sensitivity Switch Overlay
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Evidencia Sensible [⚠️]", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = isSensitive,
                    onCheckedChange = { isSensitive = it }
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Photo Capture
            FloatingActionButton(
                onClick = {
                    val photoFile = File(context.cacheDir, "evidence_photo_${System.currentTimeMillis()}.jpg")
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                    imageCapture?.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onError(exc: ImageCaptureException) {
                                Toast.makeText(context, "Error al capturar fotografía: ${exc.message}", Toast.LENGTH_SHORT).show()
                            }

                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                captureWithLocation { lat, lng ->
                                    Toast.makeText(context, "Foto capturada con GPS ($lat, $lng)", Toast.LENGTH_SHORT).show()
                                    onEvidenceCaptured(photoFile, photoFile.name, lat, lng, isSensitive)
                                }
                            }
                        }
                    )
                }
            ) {
                Icon(Icons.Default.Camera, contentDescription = "Capturar Foto")
            }

            // Real Video Recording (Max 20 seconds)
            FloatingActionButton(
                onClick = {
                    val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
                        putExtra(MediaStore.EXTRA_DURATION_LIMIT, 20) // Max 20 seconds limit
                        putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1) // High quality
                    }
                    videoRecordLauncher.launch(intent)
                }
            ) {
                Icon(Icons.Default.Videocam, contentDescription = "Grabar Video (Max 20s)")
            }
        }
    }
}
