package com.example.electrosplitapp

import android.Manifest
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraScreen(
    visionService: VisionService,
    onTextRecognized: (String) -> Unit,
    onClose: () -> Unit
) {
    var hasCameraPermission by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Preparing camera...") }
    var isScanning by remember { mutableStateOf(false) }
    var shouldAnalyzeFrames by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(2) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
            statusMessage = if (granted) "Steady your device (2)..." else "Camera permission required"
        }
    )

    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            while (countdown > 0) {
                delay(1000)
                countdown--
                statusMessage = "Steady your device ($countdown)..."
            }
            shouldAnalyzeFrames = true
            statusMessage = "Scanning now..."
            isScanning = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    val executor = Executors.newSingleThreadExecutor()

                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder()
                                .setTargetRotation(Surface.ROTATION_0)
                                .build()
                                .also { it.surfaceProvider = previewView.surfaceProvider }

                            val imageAnalyzer = ImageAnalysis.Builder()
                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                                .setTargetRotation(Surface.ROTATION_0)
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { analyzer ->
                                    analyzer.setAnalyzer(executor, createDelayedAnalyzer(
                                        visionService = visionService,
                                        shouldAnalyze = { shouldAnalyzeFrames },
                                        onResult = { result ->
                                            analyzer.clearAnalyzer()
                                            onTextRecognized(result)
                                        }
                                    ))
                                }

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                (ctx as ComponentActivity),
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalyzer
                            )
                        } catch (e: Exception) {
                            Log.e("CAMERA", "Setup failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width * 0.8f
            val height = size.height * 0.3f
            drawRect(
                color = Color.Yellow.copy(alpha = 0.2f),
                topLeft = Offset((size.width - width)/2, (size.height - height)/2),
                size = Size(width, height),
                style = Stroke(width = 4f)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 32.dp)
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = statusMessage,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )
            if (hasCameraPermission && !shouldAnalyzeFrames) {
                Spacer(modifier = Modifier.height(8.dp))
                CircularProgressIndicator()
            } else if (isScanning) {
                Spacer(modifier = Modifier.height(8.dp))
                CircularProgressIndicator()
            }
        }

        Button(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .fillMaxWidth(0.8f),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Red.copy(alpha = 0.8f),
                contentColor = Color.White
            )
        ) {
            Text("Cancel Scan")
        }
    }
}

@OptIn(ExperimentalGetImage::class)
private fun createDelayedAnalyzer(
    visionService: VisionService,
    shouldAnalyze: () -> Boolean,
    onResult: (String) -> Unit
): ImageAnalysis.Analyzer {
    var isProcessing = false

    return ImageAnalysis.Analyzer { imageProxy ->
        if (imageProxy.image == null || isProcessing || !shouldAnalyze()) {
            imageProxy.close()
            return@Analyzer
        }

        isProcessing = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reading = visionService.detectDigits(imageProxy)
                withContext(Dispatchers.Main) {
                    onResult(reading)
                }
            } catch (e: Exception) {
                Log.e("CAMERA", "Analysis failed", e)
                withContext(Dispatchers.Main) {
                    onResult("Error: ${e.message}")
                }
            } finally {
                isProcessing = false
                imageProxy.close()
            }
        }
    }
}