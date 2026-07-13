package com.distrigo.app.ui.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.distrigo.app.ui.products.AccentGreen
import com.distrigo.app.ui.products.DestructiveRed
import com.distrigo.app.ui.products.PrimaryBlue
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BarcodeScannerScreen(
    onBarcodeScanned: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCameraPermission) {
            CameraPreviewContent(onBarcodeScanned = onBarcodeScanned, onClose = onClose)
        } else {
            PermissionDeniedContent(
                onRetry = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onClose = onClose
            )
        }
    }
}

@Composable
private fun CameraPreviewContent(onBarcodeScanned: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val scanner = remember { BarcodeScanning.getClient() }
    val coroutineScope = rememberCoroutineScope()

    var hasScanned by remember { mutableStateOf(false) }
    var indicatorColor by remember { mutableStateOf(DestructiveRed) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (e: Exception) {
                // no-op — camera may already be unbound
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                if (hasScanned) {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }
                                val mediaImage = imageProxy.image
                                if (mediaImage == null) {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }
                                val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                scanner.process(inputImage)
                                    .addOnSuccessListener { barcodes ->
                                        val rawValue = barcodes.firstOrNull()?.rawValue
                                        if (!hasScanned && !rawValue.isNullOrBlank()) {
                                            hasScanned = true
                                            indicatorColor = AccentGreen

                                            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                                            vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))

                                            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                                            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                                            Handler(Looper.getMainLooper()).postDelayed({ toneGen.release() }, 200)

                                            coroutineScope.launch {
                                                delay(500)
                                                onBarcodeScanned(rawValue)
                                                onClose()
                                            }
                                        }
                                    }
                                    .addOnCompleteListener { imageProxy.close() }
                            }
                        }
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        // no-op — binding may fail if lifecycle already destroyed
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )

        // ── Frame guide + subtitle ──
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(width = 250.dp, height = 150.dp)
                    .border(2.dp, Color.White, RoundedCornerShape(16.dp))
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Placez le code-barres dans le cadre",
                color = Color.White,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        // ── Close button ──
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .size(40.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.4f))
        ) {
            Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White)
        }

        // ── Status dot indicator (red → green on successful scan) ──
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(12.dp)
                .clip(RoundedCornerShape(50))
                .background(indicatorColor)
        )
    }
}

@Composable
private fun PermissionDeniedContent(onRetry: () -> Unit, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Autorisation caméra requise",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Réessayer")
            }
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .size(40.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.15f))
        ) {
            Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White)
        }
    }
}
